from pathlib import Path
import logging

from fastapi import APIRouter, Request, status
from fastapi.responses import HTMLResponse, Response
from jinja2 import Environment, FileSystemLoader, select_autoescape
from starlette.concurrency import run_in_threadpool

from auth_bridge.logging_utils import mask_token
from auth_bridge.middleware.rate_limit import (
    RateLimitExceeded,
    SlidingWindowRateLimiter,
    build_proxy_rate_limit_keys,
    extract_client_ip,
)
from auth_bridge.services.pairing_service import PairingExpiredError, PairingNotFoundError, PairingService

logger = logging.getLogger(__name__)
_templates_dir = Path(__file__).resolve().parents[1] / "templates"
_template_env = Environment(
    loader=FileSystemLoader(_templates_dir),
    autoescape=select_autoescape(("html", "xml")),
)


def attach_wildcard_proxy_router(app, pairing_service: PairingService) -> None:
    router = APIRouter(tags=["wildcard-proxy"])

    @router.get("/", response_class=HTMLResponse)
    async def wildcard_entry(request: Request) -> Response:
        try:
            pairing, wildcard_host = _open_phone_flow_for_request(pairing_service, request)
        except PairingNotFoundError:
            return HTMLResponse("Pairing session was not found.", status_code=status.HTTP_404_NOT_FOUND)
        except PairingExpiredError:
            return HTMLResponse("Pairing expired.", status_code=status.HTTP_410_GONE)

        proxy_state = app.state.proxy_session_store.get(pairing.pairing_id)
        if pairing.session_payload is None and proxy_state is not None and _proxy_access_allowed(pairing):
            try:
                _check_proxy_rate_limit_for_request(app, pairing_service, request)
            except RateLimitExceeded:
                return HTMLResponse("Too many requests. Please try again later.", status_code=status.HTTP_429_TOO_MANY_REQUESTS)
            return await _proxy_request(app, pairing_service, pairing, request, "/", wildcard_host)

        return _render_phone_shell_response(pairing)

    @router.api_route("/{proxy_path:path}", methods=["GET", "POST"])
    async def wildcard_proxy(request: Request, proxy_path: str) -> Response:
        if proxy_path == "":
            return wildcard_entry(request)

        try:
            _check_proxy_rate_limit_for_request(app, pairing_service, request)
        except RateLimitExceeded:
            return HTMLResponse("Too many requests. Please try again later.", status_code=status.HTTP_429_TOO_MANY_REQUESTS)

        try:
            pairing, wildcard_host = _open_phone_flow_for_request(pairing_service, request)
        except PairingNotFoundError:
            return HTMLResponse("Pairing session was not found.", status_code=status.HTTP_404_NOT_FOUND)
        except PairingExpiredError:
            return HTMLResponse("Pairing expired.", status_code=status.HTTP_410_GONE)

        if not _proxy_access_allowed(pairing):
            return _render_phone_shell_response(pairing)

        return await _proxy_request(app, pairing_service, pairing, request, f"/{proxy_path}", wildcard_host)

    app.include_router(router)


def _open_phone_flow_for_request(pairing_service: PairingService, request: Request):
    wildcard_host = pairing_service.normalize_wildcard_host(request.headers.get("host", ""))
    pairing = pairing_service.open_phone_flow_for_host(wildcard_host)
    return pairing, wildcard_host


async def _proxy_request(app, pairing_service: PairingService, pairing, request: Request, path: str, wildcard_host: str) -> Response:
    proxied_response = await run_in_threadpool(
        app.state.lostfilm_proxy_service.proxy,
        pairing.pairing_id,
        wildcard_host,
        request.method,
        path,
        request.url.query,
        dict(request.headers),
        await request.body(),
    )
    if pairing.session_payload is None and "text/html" in proxied_response.headers.get("content-type", "").lower():
        proxy_state = app.state.proxy_session_store.get(pairing.pairing_id)
        cookie_names = [cookie.name for cookie in proxy_state.cookie_jar.jar] if proxy_state is not None else []
        html = proxied_response.content.decode("utf-8", errors="ignore")
        is_authenticated = app.state.lostfilm_auth_detector.is_authenticated(
            html,
            cookie_names,
            path=path,
            login_succeeded=proxy_state.login_succeeded if proxy_state is not None else False,
        )
        logger.debug(
            "Auth detector pairing_id=%s path=%s authenticated=%s login_succeeded=%s cookie_names=%s",
            mask_token(pairing.pairing_id),
            path,
            is_authenticated,
            proxy_state.login_succeeded if proxy_state is not None else False,
            sorted({cookie_name.lower() for cookie_name in cookie_names}),
        )
        if is_authenticated and proxy_state is not None:
            pairing_service.confirm_pairing_from_proxy_session(pairing.pairing_id, proxy_state.cookie_jar)
    return Response(
        content=proxied_response.content,
        status_code=proxied_response.status_code,
        headers=proxied_response.headers,
    )


def _render_template(template_name: str, **context: object) -> str:
    return _template_env.get_template(template_name).render(**context)


def _get_proxy_rate_limiter(app) -> SlidingWindowRateLimiter | None:
    return getattr(app.state, "proxy_rate_limiter", None)


def _check_proxy_rate_limit_for_request(app, pairing_service: PairingService, request: Request) -> None:
    limiter = _get_proxy_rate_limiter(app)
    if limiter is None:
        return

    try:
        phone_verifier = pairing_service.resolve_phone_verifier_from_host(request.headers.get("host", ""))
    except PairingNotFoundError:
        return

    client_ip = extract_client_ip(
        headers=request.headers,
        client_host=request.client.host if request.client is not None else None,
    )
    keys = build_proxy_rate_limit_keys(
        client_ip=client_ip,
        phone_verifier=phone_verifier,
    )
    try:
        pairing = pairing_service.get_pairing_by_phone_verifier(phone_verifier)
    except PairingNotFoundError:
        limiter.check_many(keys)
        return

    if pairing.is_expired() or pairing.failure_reason is not None or pairing.finalized:
        # Preserve the shared per-IP budget so terminal pairings cannot restore
        # capacity for other active pairings from the same client.
        limiter.reset_many(keys[1:])
        return

    limiter.check_many(keys)


def _proxy_access_allowed(pairing) -> bool:
    return (
        not pairing.is_expired()
        and pairing.failure_reason is None
        and not pairing.finalized
    )


def _render_phone_shell_response(pairing) -> HTMLResponse:
    return HTMLResponse(
        _render_template(
            "phone_shell.html",
            confirmed=pairing.session_payload is not None,
            user_code=pairing.user_code,
        )
    )
