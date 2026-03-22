from functools import lru_cache
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
    proxy_rate_limit_max_requests: int = 60
    proxy_rate_limit_window_seconds: int = 60

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
    def wildcard_base_domain(self) -> str:
        if self.public_base_domain:
            return self.public_base_domain
        parsed = urlparse(self.public_base_url)
        return parsed.netloc or self.public_base_url


@lru_cache
def get_settings() -> Settings:
    return Settings()
