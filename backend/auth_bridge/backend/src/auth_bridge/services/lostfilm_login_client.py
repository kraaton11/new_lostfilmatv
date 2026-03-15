from dataclasses import dataclass, field
from html.parser import HTMLParser
from typing import cast
from urllib.parse import urlencode, urljoin

import httpx

from auth_bridge.schemas.session_payload import LostFilmCookie, SessionPayload


class LostFilmLoginError(Exception):
    pass


@dataclass(slots=True)
class LostFilmLoginStep:
    form_action: str
    hidden_fields: dict[str, str]
    step_kind: str = "login"
    challenge_required: bool = False
    captcha_image_url: str | None = None


@dataclass(slots=True)
class _ParsedForm:
    form_action: str | None = None
    hidden_fields: dict[str, str] = field(default_factory=dict)
    field_names: set[str] = field(default_factory=set)
    captcha_image_url: str | None = None


class _LostFilmFormParser(HTMLParser):
    def __init__(self) -> None:
        super().__init__()
        self._current_form: _ParsedForm | None = None
        self.forms: list[_ParsedForm] = []

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        attributes = dict(attrs)

        if tag == "form":
            self._current_form = _ParsedForm(form_action=attributes.get("action"))
            return

        if self._current_form is None:
            return

        if tag == "input":
            name = attributes.get("name")
            field_type = (attributes.get("type") or "text").lower()
            value = attributes.get("value") or ""

            if name:
                self._current_form.field_names.add(name)

            if field_type == "hidden" and name:
                self._current_form.hidden_fields[name] = value

        if tag == "img" and self._current_form.captcha_image_url is None:
            src = attributes.get("src")
            if src and "captcha" in src.lower():
                self._current_form.captcha_image_url = src

    def handle_endtag(self, tag: str) -> None:
        if tag == "form" and self._current_form is not None:
            self.forms.append(self._current_form)
            self._current_form = None


class LostFilmLoginClient:
    _required_cookie_names = ("lf_session",)
    _optional_cookie_names = ("uid",)

    def __init__(self, base_url: str, http_client: httpx.Client | None = None) -> None:
        self._base_url = base_url.rstrip("/")
        self._http_client = http_client or httpx.Client(follow_redirects=True)

    def fetch_login_step(self) -> LostFilmLoginStep:
        self._clear_auth_cookies()
        try:
            response = self._http_client.get(f"{self._base_url}/login")
            response.raise_for_status()
        except httpx.HTTPError as exc:
            raise LostFilmLoginError("Unable to fetch the LostFilm login page.") from exc
        return self._parse_login_step(response)

    def submit_credentials(
        self,
        login_step: LostFilmLoginStep,
        *,
        username: str,
        password: str,
    ) -> LostFilmLoginStep | SessionPayload:
        response = self._submit_form(
            login_step.form_action,
            {**login_step.hidden_fields, "mail": username, "pass": password},
        )
        payload = self._extract_session_payload()
        if payload is not None:
            return payload
        return self._parse_login_step(response)

    def complete_challenge(
        self,
        challenge_step: LostFilmLoginStep,
        *,
        captcha_code: str,
    ) -> LostFilmLoginStep | SessionPayload:
        response = self._submit_form(
            challenge_step.form_action,
            {**challenge_step.hidden_fields, "cap": captcha_code},
        )
        payload = self._extract_session_payload()
        if payload is not None:
            return payload
        return self._parse_login_step(response)

    def login(self, username: str, password: str) -> SessionPayload:
        login_step = self.fetch_login_step()
        result = self.submit_credentials(login_step, username=username, password=password)
        if isinstance(result, SessionPayload):
            return result
        raise LostFilmLoginError("LostFilm login requires challenge completion.")

    def close(self) -> None:
        self._http_client.close()

    def _submit_form(self, action: str, fields: dict[str, str]) -> httpx.Response:
        try:
            response = self._http_client.post(
                action,
                content=urlencode(fields),
                headers={"content-type": "application/x-www-form-urlencoded"},
            )
            response.raise_for_status()
            return response
        except httpx.HTTPError as exc:
            raise LostFilmLoginError("LostFilm login request failed.") from exc

    def _parse_login_step(self, response: httpx.Response) -> LostFilmLoginStep:
        parser = _LostFilmFormParser()
        parser.feed(response.text)

        target_form = next(
            (
                form
                for form in parser.forms
                if form.hidden_fields.get("act") == "users"
                and form.hidden_fields.get("type") == "login"
            ),
            None,
        )
        if target_form is None:
            target_form = next(
                (
                    form
                    for form in parser.forms
                    if {"mail", "pass"}.issubset(form.field_names)
                    or "cap" in form.field_names
                ),
                None,
            )

        if target_form is None or not target_form.form_action:
            raise LostFilmLoginError("LostFilm login page did not include a form action.")

        captcha_image_url = None
        if target_form.captcha_image_url is not None:
            captcha_image_url = urljoin(str(response.url), target_form.captcha_image_url)

        challenge_required = "cap" in target_form.field_names or target_form.captcha_image_url is not None
        step_kind = "challenge" if challenge_required else "login"

        return LostFilmLoginStep(
            form_action=urljoin(str(response.url), cast(str, target_form.form_action)),
            hidden_fields=target_form.hidden_fields,
            step_kind=step_kind,
            challenge_required=challenge_required,
            captcha_image_url=captcha_image_url,
        )

    def _extract_session_payload(self) -> SessionPayload | None:
        cookies = [
            LostFilmCookie(
                name=cookie.name,
                value=cookie.value,
                domain=cookie.domain or ".lostfilm.today",
                path=cookie.path or "/",
            )
            for cookie in self._http_client.cookies.jar
            if cookie.name in (*self._required_cookie_names, *self._optional_cookie_names)
        ]
        if not any(cookie.name in self._required_cookie_names for cookie in cookies):
            return None

        account_cookie = next((cookie for cookie in cookies if cookie.name == "uid"), None)
        return SessionPayload(cookies=cookies, accountId=account_cookie.value if account_cookie else None)

    def _clear_auth_cookies(self) -> None:
        auth_cookie_names = {*self._required_cookie_names, *self._optional_cookie_names}
        cookies_to_clear = [cookie for cookie in self._http_client.cookies.jar if cookie.name in auth_cookie_names]
        for cookie in cookies_to_clear:
            self._http_client.cookies.jar.clear(
                domain=cookie.domain,
                path=cookie.path,
                name=cookie.name,
            )
