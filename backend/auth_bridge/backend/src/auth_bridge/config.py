from functools import lru_cache
from ipaddress import ip_network
from urllib.parse import urlparse

from pydantic import AliasChoices, Field, field_validator
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_prefix="AUTH_BRIDGE_",
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
        populate_by_name=True,
    )

    public_base_url: str = Field(
        default="https://auth.example.test",
        validation_alias=AliasChoices("AUTH_BRIDGE_PUBLIC_BASE_URL", "APP_BASE_URL"),
    )
    public_base_domain: str | None = Field(
        default=None,
        validation_alias=AliasChoices("AUTH_BRIDGE_PUBLIC_BASE_DOMAIN"),
    )
    lostfilm_base_url: str = "https://www.lostfilm.today"
    pairing_ttl_seconds: int = 600
    pairing_poll_interval_seconds: int = 5
    claim_lease_ttl_seconds: int = 60
    create_pairing_rate_limit_max_requests: int = 10
    create_pairing_rate_limit_window_seconds: int = 60
    pairing_action_rate_limit_max_requests: int = 120
    pairing_action_rate_limit_window_seconds: int = 60
    proxy_rate_limit_max_requests: int = 240
    proxy_rate_limit_window_seconds: int = 60
    cleanup_interval_seconds: int = 60
    upstream_timeout_seconds: float = 10.0
    upstream_retry_attempts: int = 2
    upstream_retry_backoff_seconds: float = 0.25
    translation_rate_limit_max_requests: int = 120
    translation_rate_limit_window_seconds: int = 60
    tmdb_rate_limit_max_requests: int = 240
    tmdb_rate_limit_window_seconds: int = 60
    tmdb_api_key: str = ""
    tmdb_bearer_token: str = ""
    tmdb_api_base_url: str = "https://api.themoviedb.org/3"
    tmdb_timeout_seconds: float = 10.0
    tmdb_cache_max_entries: int = 5000
    tmdb_cache_ttl_search_seconds: int = 7 * 24 * 60 * 60
    tmdb_cache_ttl_images_seconds: int = 30 * 24 * 60 * 60
    tmdb_cache_ttl_details_seconds: int = 7 * 24 * 60 * 60
    tmdb_cache_ttl_negative_seconds: int = 24 * 60 * 60
    deepl_api_key: str = ""
    deepl_api_url: str = "https://api-free.deepl.com/v2/translate"
    deepl_timeout_seconds: float = 10.0
    translation_cache_max_entries: int = 1000
    translation_cache_ttl_seconds: int = 7 * 24 * 60 * 60
    log_format: str = "text"
    trusted_proxy_ips: str = ""
    trusted_device_secret: str = ""
    trusted_device_cookie_name: str = "auth_bridge_session"
    trusted_device_cookie_domain: str | None = None
    trusted_device_ttl_seconds: int = 365 * 24 * 60 * 60
    trusted_device_db_path: str = "/data/trusted_devices.sqlite3"

    @field_validator("pairing_ttl_seconds", "pairing_poll_interval_seconds", "claim_lease_ttl_seconds")
    @classmethod
    def validate_positive_int(cls, value: int) -> int:
        if value <= 0:
            raise ValueError("value must be positive")
        return value

    @field_validator(
        "create_pairing_rate_limit_max_requests",
        "create_pairing_rate_limit_window_seconds",
        "pairing_action_rate_limit_max_requests",
        "pairing_action_rate_limit_window_seconds",
        "proxy_rate_limit_max_requests",
        "proxy_rate_limit_window_seconds",
        "translation_rate_limit_max_requests",
        "translation_rate_limit_window_seconds",
        "tmdb_rate_limit_max_requests",
        "tmdb_rate_limit_window_seconds",
        "tmdb_cache_max_entries",
        "tmdb_cache_ttl_search_seconds",
        "tmdb_cache_ttl_images_seconds",
        "tmdb_cache_ttl_details_seconds",
        "tmdb_cache_ttl_negative_seconds",
        "translation_cache_max_entries",
        "translation_cache_ttl_seconds",
        "cleanup_interval_seconds",
        "trusted_device_ttl_seconds",
    )
    @classmethod
    def validate_non_negative_int(cls, value: int) -> int:
        if value < 0:
            raise ValueError("value must not be negative")
        return value

    @field_validator("upstream_timeout_seconds", "upstream_retry_backoff_seconds", "deepl_timeout_seconds", "tmdb_timeout_seconds")
    @classmethod
    def validate_non_negative_float(cls, value: float) -> float:
        if value < 0:
            raise ValueError("value must not be negative")
        return value

    @field_validator("upstream_retry_attempts")
    @classmethod
    def validate_retry_attempts(cls, value: int) -> int:
        if value < 1:
            raise ValueError("upstream_retry_attempts must be at least 1")
        return value

    @field_validator("log_format")
    @classmethod
    def validate_log_format(cls, value: str) -> str:
        normalized = value.strip().lower()
        if normalized not in {"text", "json"}:
            raise ValueError("log_format must be either 'text' or 'json'")
        return normalized

    @field_validator("trusted_proxy_ips")
    @classmethod
    def validate_trusted_proxy_ips(cls, value: str) -> str:
        entries = [entry.strip() for entry in value.split(",") if entry.strip()]
        for entry in entries:
            try:
                ip_network(entry, strict=False)
            except ValueError as exc:
                raise ValueError("trusted_proxy_ips must contain IP addresses or CIDR ranges") from exc
        return ",".join(entries)

    @field_validator("trusted_device_cookie_name")
    @classmethod
    def validate_trusted_device_cookie_name(cls, value: str) -> str:
        normalized = value.strip()
        if not normalized or any(char in normalized for char in " ;,\r\n\t"):
            raise ValueError("trusted_device_cookie_name must be a valid cookie name")
        return normalized

    @field_validator("trusted_device_cookie_domain")
    @classmethod
    def validate_trusted_device_cookie_domain(cls, value: str | None) -> str | None:
        if value is None:
            return value
        normalized = value.strip().lower().lstrip(".").rstrip(".")
        if not normalized:
            return None
        if "/" in normalized or ":" in normalized or " " in normalized:
            raise ValueError("trusted_device_cookie_domain must be a bare domain")
        return normalized

    @field_validator("public_base_url")
    @classmethod
    def validate_public_base_url(cls, value: str) -> str:
        parsed = urlparse(value)
        try:
            _ = parsed.port
        except ValueError as exc:
            raise ValueError("public_base_url contains an invalid port") from exc

        if parsed.scheme != "https":
            raise ValueError("public_base_url must use https")
        if not parsed.hostname or parsed.username is not None or parsed.password is not None:
            raise ValueError("public_base_url must be an https origin without credentials")
        if parsed.path not in ("", "/") or parsed.params or parsed.query or parsed.fragment:
            raise ValueError("public_base_url must not include path, query, or fragment")
        return value

    @field_validator("public_base_domain")
    @classmethod
    def validate_public_base_domain(cls, value: str | None) -> str | None:
        if value is None:
            return value

        candidate = value.strip()
        if not candidate or "://" in candidate:
            raise ValueError("public_base_domain must be a bare host name")

        parsed = urlparse(f"//{candidate}", scheme="https")
        try:
            port = parsed.port
        except ValueError as exc:
            raise ValueError("public_base_domain contains an invalid port") from exc

        if not parsed.hostname or port is not None:
            raise ValueError("public_base_domain must be a bare host name")
        if parsed.username is not None or parsed.password is not None:
            raise ValueError("public_base_domain must not include credentials")
        if parsed.path or parsed.params or parsed.query or parsed.fragment:
            raise ValueError("public_base_domain must not include path, query, or fragment")
        return parsed.hostname.lower().rstrip(".")

    @property
    def public_base_host(self) -> str:
        parsed = urlparse(self.public_base_url)
        return parsed.hostname or parsed.netloc or self.public_base_url

    @property
    def wildcard_base_domain(self) -> str:
        if self.public_base_domain:
            return self.public_base_domain
        parsed = urlparse(self.public_base_url)
        return parsed.netloc or self.public_base_url

    @property
    def trusted_proxy_networks(self) -> tuple[str, ...]:
        return tuple(entry for entry in self.trusted_proxy_ips.split(",") if entry)


@lru_cache
def get_settings() -> Settings:
    return Settings()
