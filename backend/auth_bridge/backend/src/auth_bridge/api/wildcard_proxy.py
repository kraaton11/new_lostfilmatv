from pathlib import Path
import logging

from fastapi import APIRouter, Request, status
from fastapi.responses import HTMLResponse, Response
from jinja2 import Environment, FileSystemLoader, select_autoescape
from starlette.concurrency import run_in_threadpool

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
            pairing = pairing_service.open_phone_flow_for_host(request.headers.get("host", ""))
        except PairingNotFoundError:
            return HTMLResponse("Pairing session was not found.", status_code=status.HTTP_404_NOT_FOUND)
        except PairingExpiredError:
            return HTMLResponse("Pairing expired.", status_code=status.HTTP_410_GONE)

        if pairing.session_payload is None and app.state.proxy_session_store.get(pairing.pairing_id) is not None:
            return await _proxy_request(app, pairing_service, pairing, request, "/")

        return HTMLResponse(
            _render_template(
                "phone_shell.html",
                confirmed=pairing.session_payload is not None,
                user_code=pairing.user_code,
            )
        )

    @router.api_route("/{proxy_path:path}", methods=["GET", "POST"])
    async def wildcard_proxy(request: Request, proxy_path: str) -> Response:
        if proxy_path == "":
            return wildcard_entry(request)

        try:
            pairing = pairing_service.open_phone_flow_for_host(request.headers.get("host", ""))
        except PairingNotFoundError:
            return HTMLResponse("Pairing session was not found.", status_code=status.HTTP_404_NOT_FOUND)
        except PairingExpiredError:
            return HTMLResponse("Pairing expired.", status_code=status.HTTP_410_GONE)

        return await _proxy_request(app, pairing_service, pairing, request, f"/{proxy_path}")

    app.include_router(router)


async def _proxy_request(app, pairing_service: PairingService, pairing, request: Request, path: str) -> Response:
    proxied_response = await run_in_threadpool(
        app.state.lostfilm_proxy_service.proxy,
        pairing.pairing_id,
        request.headers.get("host", "").split(":", 1)[0].strip(),
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
            pairing.pairing_id,
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
