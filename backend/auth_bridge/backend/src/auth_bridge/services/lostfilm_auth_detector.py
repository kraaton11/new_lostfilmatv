class LostFilmAuthDetector:
    _ANONYMOUS_MARKERS = (
        'location.replace("/")',
        "если по какой-то причине",
        'http-equiv="refresh"',
    )
    _PROFILE_PAGE_MARKERS = (
        'href="/logout"',
        "logout",
    )
    _HOME_PAGE_MARKERS = (
        'class="user-pane"',
        'href="/my"',
    )
    _HOME_PAGE_AVATAR_MARKERS = (
        "/static/users/",
        "/vision/no-avatar-50.jpg",
    )
    _REQUIRED_COOKIE_NAMES = {"lf_session"}

    _POST_LOGIN_ROOT_COOKIE_NAMES = {"lf_session", "lf_udv"}

    def is_authenticated(
        self,
        html: str,
        cookie_names: list[str],
        *,
        path: str = "",
        login_succeeded: bool = False,
    ) -> bool:
        normalized_html = html.lower()
        normalized_cookie_names = {cookie_name.lower() for cookie_name in cookie_names}
        normalized_path = path.lower()

        if any(marker in normalized_html for marker in self._ANONYMOUS_MARKERS):
            return False
        if not self._REQUIRED_COOKIE_NAMES.issubset(normalized_cookie_names):
            return False
        if any(marker in normalized_html for marker in self._PROFILE_PAGE_MARKERS):
            return True
        if (
            login_succeeded
            and normalized_path == "/"
            and self._POST_LOGIN_ROOT_COOKIE_NAMES.issubset(normalized_cookie_names)
        ):
            return True
        return all(marker in normalized_html for marker in self._HOME_PAGE_MARKERS) and any(
            marker in normalized_html for marker in self._HOME_PAGE_AVATAR_MARKERS
        )
