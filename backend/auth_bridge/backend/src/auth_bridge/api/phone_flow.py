from pathlib import Path
from urllib.parse import parse_qs

from fastapi import APIRouter, Request, status
from fastapi.responses import HTMLResponse, Response
from jinja2 import Environment, FileSystemLoader, select_autoescape
from starlette.concurrency import run_in_threadpool

from auth_bridge.services.lostfilm_login_client import LostFilmLoginError
from auth_bridge.services.pairing_service import PairingExpiredError, PairingNotFoundError, PairingService


_templates_dir = Path(__file__).resolve().parents[1] / "templates"
_template_env = Environment(
    loader=FileSystemLoader(_templates_dir),
    autoescape=select_autoescape(("html", "xml")),
)


def attach_phone_flow_router(app, pairing_service: PairingService) -> None:
    router = APIRouter(tags=["phone-flow"])

    @router.get("/pair/{phone_verifier}", response_class=HTMLResponse)
    def pair_page(phone_verifier: str) -> HTMLResponse:
        try:
            pairing = pairing_service.open_phone_flow(phone_verifier)
        except PairingNotFoundError:
            return _render_error_response("Pairing session was not found.", status.HTTP_404_NOT_FOUND)
        except PairingExpiredError:
            return HTMLResponse(_render_template("expired.html", user_code=""), status_code=410)

        if pairing.session_payload is not None:
            return HTMLResponse(_render_template("success.html", user_code=pairing.user_code))
        return HTMLResponse(
            _render_template(
                "login.html",
                user_code=pairing.user_code,
                error_message="",
                form_action=f"/pair/{phone_verifier}/login",
            )
        )

    @router.get("/pair/{phone_verifier}/login", response_class=HTMLResponse)
    def login_page(phone_verifier: str) -> HTMLResponse:
        return pair_page(phone_verifier)

    @router.post("/pair/{phone_verifier}/login", response_class=HTMLResponse)
    async def submit_login(phone_verifier: str, request: Request) -> HTMLResponse:
        form_fields = await _parse_form_body(request)
        username = form_fields.get("username", "")
        password = form_fields.get("password", "")
        try:
            result = await run_in_threadpool(
                pairing_service.submit_phone_login,
                phone_verifier,
                username,
                password,
                app.state.lostfilm_login_client_factory,
            )
            pairing = pairing_service.get_pairing_by_phone_verifier(phone_verifier)
            if getattr(result, "step_kind", None) == "challenge":
                return HTMLResponse(
                    _render_template(
                        "challenge.html",
                        user_code=pairing.user_code,
                        phone_verifier=phone_verifier,
                        error_message="",
                        captcha_image_url=f"/pair/{phone_verifier}/captcha",
                    )
                )
            if getattr(result, "step_kind", None) == "login":
                return HTMLResponse(
                    _render_template(
                        "login.html",
                        user_code=pairing.user_code,
                        error_message="Login failed.",
                        form_action=f"/pair/{phone_verifier}/login",
                    )
                )
            if pairing.session_payload is None:
                return HTMLResponse(
                    _render_template(
                        "login.html",
                        user_code=pairing.user_code,
                        error_message="Login failed.",
                        form_action=f"/pair/{phone_verifier}/login",
                    )
                )
            return HTMLResponse(_render_template("success.html", user_code=pairing.user_code))
        except PairingNotFoundError:
            return _render_error_response("Pairing session was not found.", status.HTTP_404_NOT_FOUND)
        except PairingExpiredError:
            return HTMLResponse(_render_template("expired.html", user_code=""), status_code=410)
        except LostFilmLoginError as exc:
            try:
                pairing = pairing_service.get_pairing_by_phone_verifier(phone_verifier)
                return HTMLResponse(
                    _render_template(
                        "login.html",
                        user_code=pairing.user_code,
                        error_message=str(exc),
                        form_action=f"/pair/{phone_verifier}/login",
                    )
                )
            except PairingNotFoundError:
                return _render_error_response("Pairing session was not found.", status.HTTP_404_NOT_FOUND)

    @router.post("/pair/{phone_verifier}/challenge", response_class=HTMLResponse)
    async def submit_challenge(phone_verifier: str, request: Request) -> HTMLResponse:
        form_fields = await _parse_form_body(request)
        username = form_fields.get("username", "")
        password = form_fields.get("password", "")
        captcha_code = form_fields.get("captcha_code", "")
        try:
            result = await run_in_threadpool(
                pairing_service.complete_phone_challenge,
                phone_verifier,
                username,
                password,
                captcha_code,
            )
            pairing = pairing_service.get_pairing_by_phone_verifier(phone_verifier)
            if getattr(result, "step_kind", None) == "challenge":
                return HTMLResponse(
                    _render_template(
                        "challenge.html",
                        user_code=pairing.user_code,
                        phone_verifier=phone_verifier,
                        error_message="Try again.",
                        captcha_image_url=f"/pair/{phone_verifier}/captcha",
                    )
                )
            if getattr(result, "step_kind", None) == "login":
                return HTMLResponse(
                    _render_template(
                        "login.html",
                        user_code=pairing.user_code,
                        error_message="Login failed.",
                        form_action=f"/pair/{phone_verifier}/login",
                    )
                )
            if pairing.session_payload is None:
                return HTMLResponse(
                    _render_template(
                        "login.html",
                        user_code=pairing.user_code,
                        error_message="Login failed.",
                        form_action=f"/pair/{phone_verifier}/login",
                    )
                )
            return HTMLResponse(_render_template("success.html", user_code=pairing.user_code))
        except PairingNotFoundError:
            return _render_error_response("Pairing session was not found.", status.HTTP_404_NOT_FOUND)
        except PairingExpiredError:
            return HTMLResponse(_render_template("expired.html", user_code=""), status_code=410)
        except LostFilmLoginError as exc:
            try:
                pairing = pairing_service.get_pairing_by_phone_verifier(phone_verifier)
                return HTMLResponse(
                    _render_template(
                        "challenge.html",
                        user_code=pairing.user_code,
                        phone_verifier=phone_verifier,
                        error_message=str(exc),
                        captcha_image_url=f"/pair/{phone_verifier}/captcha",
                    )
                )
            except PairingNotFoundError:
                return _render_error_response("Pairing session was not found.", status.HTTP_404_NOT_FOUND)

    @router.get("/pair/{phone_verifier}/captcha")
    async def challenge_captcha(phone_verifier: str) -> Response:
        try:
            pairing = pairing_service.get_pairing_by_phone_verifier(phone_verifier)
        except PairingNotFoundError:
            return Response(status_code=status.HTTP_404_NOT_FOUND)
        except PairingExpiredError:
            return Response(status_code=status.HTTP_410_GONE)

        if pairing.challenge_step is None or pairing.login_client is None:
            return Response(status_code=status.HTTP_404_NOT_FOUND)

        try:
            content = await run_in_threadpool(pairing.login_client.fetch_captcha_image, pairing.challenge_step)
        except LostFilmLoginError:
            return Response(status_code=status.HTTP_502_BAD_GATEWAY)
        return Response(content=content, media_type="image/png")

    app.include_router(router)


async def _parse_form_body(request: Request) -> dict[str, str]:
    body = (await request.body()).decode("utf-8")
    parsed = parse_qs(body, keep_blank_values=True)
    return {key: values[-1] if values else "" for key, values in parsed.items()}


def _render_template(template_name: str, **context: str) -> str:
    return _template_env.get_template(template_name).render(**context)


def _render_error_response(message: str, status_code: int) -> HTMLResponse:
    return HTMLResponse(_render_template("error.html", message=message, retry_url=""), status_code=status_code)
