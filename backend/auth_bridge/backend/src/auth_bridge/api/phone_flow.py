from pathlib import Path

from fastapi import APIRouter, status
from fastapi.responses import HTMLResponse, RedirectResponse
from jinja2 import Environment, FileSystemLoader, select_autoescape

from auth_bridge.api.security_headers import secure_html_response
from auth_bridge.services.pairing_service import (
    PHONE_FLOW_COOKIE_NAME,
    PairingExpiredError,
    PairingNotFoundError,
    PairingService,
)

_templates_dir = Path(__file__).resolve().parents[1] / "templates"
_template_env = Environment(
    loader=FileSystemLoader(_templates_dir),
    autoescape=select_autoescape(("html", "xml")),
)


def attach_phone_flow_router(
    app,
    pairing_service: PairingService,
) -> None:
    router = APIRouter(tags=["phone-flow"])

    @router.get("/pair/{phone_verifier}")
    def pair_page(phone_verifier: str):
        try:
            pairing = pairing_service.open_phone_flow(phone_verifier)
        except PairingNotFoundError:
            return _render_error_response("Pairing session was not found.", status.HTTP_404_NOT_FOUND)
        except PairingExpiredError:
            return secure_html_response(_render_template("expired.html", user_code=""), status_code=410)

        response = RedirectResponse(url="/", status_code=status.HTTP_307_TEMPORARY_REDIRECT)
        response.set_cookie(
            key=PHONE_FLOW_COOKIE_NAME,
            value=pairing.phone_verifier,
            max_age=pairing.expires_in(),
            secure=True,
            httponly=True,
            samesite="lax",
            path="/",
            domain=pairing_service.phone_flow_cookie_domain(),
        )
        return response

    app.include_router(router)


def _render_template(template_name: str, **context: str) -> str:
    return _template_env.get_template(template_name).render(**context)


def _render_error_response(message: str, status_code: int) -> HTMLResponse:
    return secure_html_response(_render_template("error.html", message=message, retry_url=""), status_code=status_code)
