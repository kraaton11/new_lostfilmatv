from pathlib import Path
import logging

from fastapi import APIRouter, Request, status
from fastapi.responses import HTMLResponse, RedirectResponse, Response
from jinja2 import Environment, FileSystemLoader, select_autoescape
from starlette.concurrency import run_in_threadpool

from auth_bridge.api.security_headers import apply_security_headers, secure_html_response
from auth_bridge.logging_utils import mask_token
from auth_bridge.middleware.rate_limit import (
    RateLimitExceeded,
    SlidingWindowRateLimiter,
    build_proxy_rate_limit_keys,
    extract_client_ip,
)
from auth_bridge.services.pairing_service import (
    PHONE_FLOW_COOKIE_NAME,
    PairingExpiredError,
    PairingNotFoundError,
    PairingService,
)
from auth_bridge.services.lostfilm_proxy_service import UpstreamProxyError
from auth_bridge.services.trusted_device_service import TrustedDeviceSession

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
            return secure_html_response("Pairing session was not found.", status_code=status.HTTP_404_NOT_FOUND)
        except PairingExpiredError:
            return secure_html_response("Pairing expired.", status_code=status.HTTP_410_GONE)

        if pairing.session_payload is None:
            device_id = request.query_params.get("device_id")
            trusted_session = await _trusted_device_session_for_request(app, request, device_id=device_id)
            if trusted_session is not None and _proxy_access_allowed(pairing):
                return _render_trusted_device_response(pairing, trusted_session)

        proxy_state = app.state.proxy_session_store.get(pairing.pairing_id)
        if pairing.session_payload is None and proxy_state is None and _proxy_access_allowed(pairing):
            return RedirectResponse(url="/login", status_code=status.HTTP_307_TEMPORARY_REDIRECT)
        if pairing.session_payload is None and proxy_state is not None and _proxy_access_allowed(pairing):
            try:
                _check_proxy_rate_limit_for_request(app, pairing_service, request)
            except RateLimitExceeded:
                return secure_html_response("Too many requests. Please try again later.", status_code=status.HTTP_429_TOO_MANY_REQUESTS)
            return await _proxy_request(app, pairing_service, pairing, request, "/", wildcard_host)

        response = _render_phone_shell_response(pairing)
        await _reissue_trusted_cookie_after_confirm(app, pairing, response)
        return response

    @router.api_route("/{proxy_path:path}", methods=["GET", "POST"])
    async def wildcard_proxy(request: Request, proxy_path: str) -> Response:
        if proxy_path == "":
            return wildcard_entry(request)

        if not (request.method == "GET" and proxy_path == "login"):
            try:
                _check_proxy_rate_limit_for_request(app, pairing_service, request)
            except RateLimitExceeded:
                return secure_html_response("Too many requests. Please try again later.", status_code=status.HTTP_429_TOO_MANY_REQUESTS)

        try:
            pairing, wildcard_host = _open_phone_flow_for_request(pairing_service, request)
        except PairingNotFoundError:
            return secure_html_response("Pairing session was not found.", status_code=status.HTTP_404_NOT_FOUND)
        except PairingExpiredError:
            return secure_html_response("Pairing expired.", status_code=status.HTTP_410_GONE)

        if not _proxy_access_allowed(pairing):
            return _render_phone_shell_response(pairing)

        if request.method == "POST" and proxy_path == "auth-bridge/trusted/authorize":
            device_id = request.query_params.get("device_id")
            trusted_session = await _trusted_device_session_for_request(app, request, device_id=device_id)
            if trusted_session is None:
                return RedirectResponse(url="/login", status_code=status.HTTP_303_SEE_OTHER)
            pairing_service.confirm_pairing(pairing.pairing_id, trusted_session.payload)
            response = _render_phone_shell_response(pairing)
            await app.state.trusted_device_service.remember(response, trusted_session.payload, previous_token=trusted_session.token)
            return response

        if request.method == "POST" and proxy_path == "auth-bridge/trusted/revoke":
            trusted_service = getattr(app.state, "trusted_device_service", None)
            if trusted_service is not None:
                await trusted_service.revoke_token(request.cookies.get(trusted_service.cookie_name, ""))
            response = RedirectResponse(url="/login", status_code=status.HTTP_303_SEE_OTHER)
            if trusted_service is not None:
                trusted_service.forget_response_cookie(response)
            return response

        if request.method == "GET" and proxy_path == "login":
            if pairing.session_payload is None:
                device_id = request.query_params.get("device_id")
                trusted_session = await _trusted_device_session_for_request(app, request, device_id=device_id)
                if trusted_session is not None:
                    return _render_trusted_device_response(pairing, trusted_session, device_id=device_id)

        return await _proxy_request(app, pairing_service, pairing, request, f"/{proxy_path}", wildcard_host)

    app.include_router(router)


def _open_phone_flow_for_request(pairing_service: PairingService, request: Request):
    host = request.headers.get("host", "")
    try:
        wildcard_host = pairing_service.normalize_wildcard_host(host)
        pairing = pairing_service.open_phone_flow_for_host(wildcard_host)
        return pairing, wildcard_host
    except PairingNotFoundError:
        phone_verifier = request.cookies.get(PHONE_FLOW_COOKIE_NAME, "")
        if not phone_verifier:
            raise
        pairing = pairing_service.open_phone_flow(phone_verifier)
        return pairing, host


async def _proxy_request(app, pairing_service: PairingService, pairing, request: Request, path: str, wildcard_host: str) -> Response:
    try:
        proxied_response = await run_in_threadpool(
            app.state.lostfilm_proxy_service.proxy,
            pairing.pairing_id,
            wildcard_host,
            request.method,
            path,
            request.url.query,
            dict(request.headers),
            await request.body(),
            user_code=pairing.user_code,
        )
    except UpstreamProxyError:
        logger.warning("Upstream proxy unavailable pairing_id=%s path=%s", mask_token(pairing.pairing_id), path)
        return secure_html_response("LostFilm is temporarily unavailable. Please try again later.", status_code=502)
    confirmed_payload = None
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
            confirmed_payload = pairing_service.confirm_pairing_from_proxy_session(pairing.pairing_id, proxy_state.cookie_jar)
    response = Response(
        content=proxied_response.content,
        status_code=proxied_response.status_code,
        headers=proxied_response.headers,
    )
    if confirmed_payload is not None and proxy_state is not None and proxy_state.remember_device:
        await app.state.trusted_device_service.remember(response, confirmed_payload)
    if "text/html" in proxied_response.headers.get("content-type", "").lower():
        apply_security_headers(response)
    return response


def _render_template(template_name: str, **context: object) -> str:
    return _template_env.get_template(template_name).render(**context)


async def _reissue_trusted_cookie_after_confirm(app, pairing, response: Response) -> None:
    if pairing.session_payload is None:
        return
    proxy_state = app.state.proxy_session_store.get(pairing.pairing_id)
    if proxy_state is None or not proxy_state.remember_device or proxy_state.trusted_cookie_reissued:
        return
    await app.state.trusted_device_service.remember(response, pairing.session_payload)
    proxy_state.trusted_cookie_reissued = True


def _get_proxy_rate_limiter(app) -> SlidingWindowRateLimiter | None:
    return getattr(app.state, "proxy_rate_limiter", None)


def _check_proxy_rate_limit_for_request(app, pairing_service: PairingService, request: Request) -> None:
    limiter = _get_proxy_rate_limiter(app)
    if limiter is None:
        return

    try:
        phone_verifier = pairing_service.resolve_phone_verifier_from_host(request.headers.get("host", ""))
    except PairingNotFoundError:
        phone_verifier = request.cookies.get(PHONE_FLOW_COOKIE_NAME, "")
        if not phone_verifier:
            return

    client_ip = extract_client_ip(
        headers=request.headers,
        client_host=request.client.host if request.client is not None else None,
        trusted_proxies=getattr(request.app.state, "trusted_proxy_networks", ()),
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


async def _trusted_device_session_for_request(app, request: Request, device_id: str | None = None) -> TrustedDeviceSession | None:
    trusted_service = getattr(app.state, "trusted_device_service", None)
    if trusted_service is None:
        return None
    return await trusted_service.resolve(request, device_id=device_id)


def _render_trusted_device_response(pairing, trusted_session: TrustedDeviceSession, device_id: str | None = None) -> HTMLResponse:
    account_label = trusted_session.payload.accountId or "LostFilm"
    return secure_html_response(
        _render_template(
            "trusted_device.html",
            user_code=pairing.user_code,
            account_label=account_label,
            device_id=device_id or "",
        )
    )


def _render_phone_shell_response(pairing) -> HTMLResponse:
    return secure_html_response(
        _render_template(
            "phone_shell.html",
            confirmed=pairing.session_payload is not None,
            user_code=pairing.user_code,
        )
    )
