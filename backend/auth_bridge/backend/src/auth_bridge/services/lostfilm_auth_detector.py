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
        has_anonymous_markers = any(marker in normalized_html for marker in self._ANONYMOUS_MARKERS)
        has_profile_markers = any(marker in normalized_html for marker in self._PROFILE_PAGE_MARKERS)
        has_home_markers = all(marker in normalized_html for marker in self._HOME_PAGE_MARKERS)
        has_avatar_marker = any(marker in normalized_html for marker in self._HOME_PAGE_AVATAR_MARKERS)
        has_post_login_root_cookies = (
            login_succeeded
            and normalized_path == "/"
            and self._POST_LOGIN_ROOT_COOKIE_NAMES.issubset(normalized_cookie_names)
        )

        if not self._REQUIRED_COOKIE_NAMES.issubset(normalized_cookie_names):
            return False
        if has_profile_markers:
            return True
        if has_post_login_root_cookies and has_home_markers and has_avatar_marker:
            return True
        if has_anonymous_markers:
            return False
        if has_post_login_root_cookies:
            return True
        return has_home_markers and has_avatar_marker
