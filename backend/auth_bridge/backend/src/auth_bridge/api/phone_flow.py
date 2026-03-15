from pathlib import Path
from urllib.parse import parse_qs

from fastapi import APIRouter, Request, status
from fastapi.responses import HTMLResponse
from jinja2 import Environment, FileSystemLoader, select_autoescape
from starlette.concurrency import run_in_threadpool

from auth_bridge.schemas.session_payload import SessionPayload
from auth_bridge.services.lostfilm_login_client import LostFilmLoginError, LostFilmLoginStep
from auth_bridge.services.pairing_service import PairingExpiredError, PairingNotFoundError, PairingNotReadyError, PairingService


_templates_dir = Path(__file__).resolve().parents[1] / "templates"
_template_env = Environment(
    loader=FileSystemLoader(_templates_dir),
    autoescape=select_autoescape(("html", "xml")),
)


def attach_phone_flow_router(app, pairing_service: PairingService) -> None:
    router = APIRouter(tags=["phone-flow"])

    @router.get("/pair/{user_code}", response_class=HTMLResponse)
    def pair_page(user_code: str) -> HTMLResponse:
        try:
            pairing = pairing_service.mark_phone_flow_opened(user_code)
        except PairingNotFoundError:
            return _render_error_response(
                message="Pairing session was not found.",
                status_code=status.HTTP_404_NOT_FOUND,
            )
        except PairingExpiredError:
            return HTMLResponse(_render_template("expired.html", user_code=user_code), status_code=410)

        if pairing.is_expired():
            return HTMLResponse(_render_template("expired.html", user_code=user_code), status_code=410)

        if pairing.session_payload is not None:
            return HTMLResponse(_render_template("success.html", user_code=user_code))

        return HTMLResponse(_render_template("pair.html", user_code=user_code))

    @router.get("/pair/{user_code}/login", response_class=HTMLResponse)
    def login_page(user_code: str) -> HTMLResponse:
        try:
            pairing = pairing_service.mark_phone_flow_opened(user_code)
        except PairingNotFoundError:
            return _render_error_response(
                message="Pairing session was not found.",
                status_code=status.HTTP_404_NOT_FOUND,
            )
        except PairingExpiredError:
            return HTMLResponse(_render_template("expired.html", user_code=user_code), status_code=410)

        if pairing.session_payload is not None:
            return HTMLResponse(_render_template("success.html", user_code=user_code))

        if pairing.challenge_step is not None:
            return HTMLResponse(
                _render_template(
                    "challenge.html",
                    user_code=user_code,
                    captcha_image_url=pairing.challenge_step.captcha_image_url or "",
                    error_message="",
                )
            )

        return HTMLResponse(_render_template("login.html", user_code=user_code, error_message=""))

    @router.post("/pair/{user_code}/login", response_class=HTMLResponse)
    async def submit_login(user_code: str, request: Request) -> HTMLResponse:
        form_fields = await _parse_form_body(request)
        username = form_fields.get("username", "")
        password = form_fields.get("password", "")
        try:
            pairing = pairing_service.mark_phone_flow_opened(user_code)
            if pairing.session_payload is not None:
                return HTMLResponse(_render_template("success.html", user_code=user_code))
            login_client = pairing_service.get_or_create_login_client(
                user_code,
                app.state.lostfilm_login_client_factory,
            )
            login_step = await run_in_threadpool(login_client.fetch_login_step)
            result = await run_in_threadpool(
                login_client.submit_credentials,
                login_step,
                username=username,
                password=password,
            )
        except PairingNotFoundError:
            return _render_error_response(
                message="Pairing session was not found.",
                status_code=status.HTTP_404_NOT_FOUND,
            )
        except PairingExpiredError:
            return HTMLResponse(_render_template("expired.html", user_code=user_code), status_code=410)
        except LostFilmLoginError as exc:
            try:
                pairing_service.mark_phone_flow_retryable(user_code)
            except PairingExpiredError:
                return HTMLResponse(_render_template("expired.html", user_code=user_code), status_code=410)
            return HTMLResponse(
                _render_template(
                    "login.html",
                    user_code=user_code,
                    error_message=str(exc),
                )
            )

        if isinstance(result, LostFilmLoginStep):
            if result.step_kind == "login":
                pairing_service.mark_phone_flow_retryable(user_code)
                return HTMLResponse(
                    _render_template(
                        "login.html",
                        user_code=user_code,
                        error_message="Wrong username or password.",
                    )
                )
            pairing_service.store_challenge_step(user_code, result)
            return HTMLResponse(
                _render_template(
                    "challenge.html",
                    user_code=user_code,
                    captcha_image_url=result.captcha_image_url or "",
                    error_message="",
                )
            )

        pairing_service.confirm_pairing_by_user_code(user_code, result)
        return HTMLResponse(_render_template("success.html", user_code=user_code))

    @router.post("/pair/{user_code}/challenge", response_class=HTMLResponse)
    async def submit_challenge(user_code: str, request: Request) -> HTMLResponse:
        form_fields = await _parse_form_body(request)
        captcha_code = form_fields.get("captcha_code", "")
        try:
            pairing = pairing_service.get_pairing_by_user_code(user_code)
            if pairing.session_payload is not None:
                return HTMLResponse(_render_template("success.html", user_code=user_code))
            challenge_step = pairing_service.get_challenge_step(user_code)
            login_client = pairing_service.get_login_client(user_code)
            result = await run_in_threadpool(
                login_client.complete_challenge,
                challenge_step,
                captcha_code=captcha_code,
            )
        except PairingNotFoundError:
            return _render_error_response(
                message="Pairing session was not found.",
                status_code=status.HTTP_404_NOT_FOUND,
            )
        except PairingExpiredError:
            return HTMLResponse(_render_template("expired.html", user_code=user_code), status_code=410)
        except PairingNotReadyError:
            return _render_error_response(
                message="Challenge step is no longer available. Start the login again.",
                status_code=status.HTTP_409_CONFLICT,
                retry_url=f"/pair/{user_code}/login",
            )
        except LostFilmLoginError as exc:
            try:
                current_challenge_step = pairing_service.get_challenge_step(user_code)
            except PairingExpiredError:
                return HTMLResponse(_render_template("expired.html", user_code=user_code), status_code=410)
            except PairingNotReadyError:
                return _render_error_response(
                    message="Challenge step is no longer available. Start the login again.",
                    status_code=status.HTTP_409_CONFLICT,
                    retry_url=f"/pair/{user_code}/login",
                )
            return HTMLResponse(
                _render_template(
                    "challenge.html",
                    user_code=user_code,
                    captcha_image_url=current_challenge_step.captcha_image_url or "",
                    error_message=str(exc),
                )
            )

        if isinstance(result, LostFilmLoginStep) and result.step_kind == "challenge":
            pairing_service.store_challenge_step(user_code, result)
            return HTMLResponse(
                _render_template(
                    "challenge.html",
                    user_code=user_code,
                    captcha_image_url=result.captcha_image_url or "",
                    error_message="Wrong challenge code.",
                )
            )

        if isinstance(result, LostFilmLoginStep):
            try:
                pairing_service.mark_phone_flow_retryable(user_code)
            except PairingExpiredError:
                return HTMLResponse(_render_template("expired.html", user_code=user_code), status_code=410)
            return HTMLResponse(
                _render_template(
                    "login.html",
                    user_code=user_code,
                    error_message="Challenge expired. Start the login again.",
                )
            )

        pairing_service.confirm_pairing_by_user_code(user_code, result)
        return HTMLResponse(_render_template("success.html", user_code=user_code))


    app.include_router(router)


def _render_template(template_name: str, **context: str) -> str:
    return _template_env.get_template(template_name).render(**context)


def _render_error_response(
    message: str,
    status_code: int,
    retry_url: str | None = None,
) -> HTMLResponse:
    return HTMLResponse(
        _render_template("error.html", message=message, retry_url=retry_url),
        status_code=status_code,
    )


async def _parse_form_body(request: Request) -> dict[str, str]:
    raw_body = (await request.body()).decode()
    parsed = parse_qs(raw_body, keep_blank_values=True)
    return {key: values[-1] for key, values in parsed.items()}
