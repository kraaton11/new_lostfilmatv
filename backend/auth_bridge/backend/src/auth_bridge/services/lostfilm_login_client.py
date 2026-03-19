from dataclasses import dataclass, field
from html.parser import HTMLParser
import json
import re
from typing import cast
from urllib.parse import quote, urlencode, urljoin

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
            self._build_login_fields(login_step.form_action, login_step.hidden_fields, username=username, password=password),
        )
        payload = self._extract_session_payload()
        if payload is not None:
            return payload
        json_retry_step = self._parse_json_login_response(response, login_step)
        if json_retry_step is not None:
            return json_retry_step
        return self._parse_login_step(response)

    def complete_challenge(
        self,
        challenge_step: LostFilmLoginStep,
        *,
        captcha_code: str,
        username: str | None = None,
        password: str | None = None,
    ) -> LostFilmLoginStep | SessionPayload:
        fields = dict(challenge_step.hidden_fields)
        if username is None and password is None:
            fields["cap"] = captcha_code
        else:
            fields["captcha"] = captcha_code
        if username is not None:
            fields["mail"] = username
        if password is not None:
            fields["pass"] = password
        if challenge_step.form_action.endswith("ajaxik.users.php"):
            fields = self._encode_javascript_fields(fields)
            fields.setdefault("rem", "1")
        response = self._submit_form(
            challenge_step.form_action,
            fields,
        )
        payload = self._extract_session_payload()
        if payload is not None:
            return payload
        json_retry_step = self._parse_json_login_response(response, challenge_step)
        if json_retry_step is not None:
            return json_retry_step
        return self._parse_login_step(response)

    def _parse_json_login_response(
        self,
        response: httpx.Response,
        login_step: LostFilmLoginStep,
    ) -> LostFilmLoginStep | SessionPayload | None:
        text = response.text.strip()
        if not text.startswith("{"):
            return None

        try:
            payload = json.loads(text)
        except json.JSONDecodeError:
            return None

        if payload.get("need_captcha"):
            hidden_fields = dict(login_step.hidden_fields)
            hidden_fields["need_captcha"] = hidden_fields.get("need_captcha") or "1"
            return LostFilmLoginStep(
                form_action=login_step.form_action,
                hidden_fields=hidden_fields,
                step_kind="challenge",
                challenge_required=True,
                captcha_image_url=login_step.captcha_image_url or f"{self._base_url}/simple_captcha.php",
            )

        if payload.get("success"):
            extracted_payload = self._extract_session_payload()
            if extracted_payload is not None:
                return extracted_payload
            raise LostFilmLoginError("LostFilm login did not return the required session cookies.")

        if payload.get("error") is not None:
            return LostFilmLoginStep(
                form_action=login_step.form_action,
                hidden_fields=login_step.hidden_fields,
                step_kind="login",
                challenge_required=False,
                captcha_image_url=None,
            )

        return None

    def login(self, username: str, password: str) -> SessionPayload:
        login_step = self.fetch_login_step()
        result = self.submit_credentials(login_step, username=username, password=password)
        if isinstance(result, SessionPayload):
            return result
        raise LostFilmLoginError("LostFilm login requires challenge completion.")

    def fetch_captcha_image(self, challenge_step: LostFilmLoginStep) -> bytes:
        if not challenge_step.captcha_image_url:
            raise LostFilmLoginError("LostFilm challenge page did not include a captcha image.")
        try:
            response = self._http_client.get(challenge_step.captcha_image_url)
            response.raise_for_status()
            return response.content
        except httpx.HTTPError as exc:
            raise LostFilmLoginError("Unable to fetch the LostFilm captcha image.") from exc

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

    def _build_login_fields(self, action: str, hidden_fields: dict[str, str], *, username: str, password: str) -> dict[str, str]:
        fields = {**hidden_fields, "mail": username, "pass": password}
        if self._looks_like_javascript_login_endpoint(action, hidden_fields):
            fields.setdefault("captcha", "")
            fields.setdefault("rem", "1")
            fields = self._encode_javascript_fields(fields)
        return fields

    def _encode_javascript_fields(self, fields: dict[str, str]) -> dict[str, str]:
        encoded = dict(fields)
        for key in ("mail", "pass", "need_captcha", "captcha", "rem", "return_url"):
            if key in encoded:
                encoded[key] = quote(encoded[key], safe="")
        return encoded

    def _looks_like_javascript_login_endpoint(self, action: str, hidden_fields: dict[str, str]) -> bool:
        return action.endswith("ajaxik.users.php") and hidden_fields.get("act") == "users" and hidden_fields.get("type") == "login"

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
            javascript_step = self._parse_javascript_login_step(response.text, str(response.url))
            if javascript_step is None:
                raise LostFilmLoginError("LostFilm login page did not include a form action.")
            return javascript_step

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

    def _parse_javascript_login_step(self, html: str, page_url: str) -> LostFilmLoginStep | None:
        if "ajaxik.users.php" not in html or "act:'users'" not in html or "type:'login'" not in html:
            return None

        action_match = re.search(r"url:\s*['\"]([^'\"]*ajaxik\.users\.php)['\"]", html)
        action = action_match.group(1) if action_match else "/ajaxik.users.php"

        need_captcha_match = re.search(r'name="need_captcha"\s+value="([^"]*)"', html)
        return_url_match = re.search(r'name="return_url"\s+value="([^"]*)"', html)
        captcha_image_match = re.search(r'<img[^>]+id="captcha_pictcha"[^>]+src="([^"]+)"', html)

        hidden_fields = {
            "act": "users",
            "type": "login",
        }
        if need_captcha_match is not None:
            hidden_fields["need_captcha"] = ""
        if return_url_match is not None:
            hidden_fields["return_url"] = return_url_match.group(1)

        captcha_image_url = None
        if captcha_image_match is not None:
            captcha_image_url = urljoin(page_url, captcha_image_match.group(1))

        return LostFilmLoginStep(
            form_action=urljoin(page_url, action),
            hidden_fields=hidden_fields,
            step_kind="login",
            challenge_required=False,
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
