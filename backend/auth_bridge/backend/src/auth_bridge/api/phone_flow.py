from pathlib import Path

from fastapi import APIRouter, status
from fastapi.responses import HTMLResponse, RedirectResponse
from jinja2 import Environment, FileSystemLoader, select_autoescape

from auth_bridge.services.pairing_service import PairingExpiredError, PairingNotFoundError, PairingService

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
            return HTMLResponse(_render_template("expired.html", user_code=""), status_code=410)

        return RedirectResponse(
            url=pairing_service.build_verification_url(pairing.phone_verifier),
            status_code=status.HTTP_307_TEMPORARY_REDIRECT,
        )

    app.include_router(router)


def _render_template(template_name: str, **context: str) -> str:
    return _template_env.get_template(template_name).render(**context)


def _render_error_response(message: str, status_code: int) -> HTMLResponse:
    return HTMLResponse(_render_template("error.html", message=message, retry_url=""), status_code=status_code)
