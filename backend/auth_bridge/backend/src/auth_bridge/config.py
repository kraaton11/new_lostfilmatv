from functools import lru_cache
from urllib.parse import urlparse

from pydantic import AliasChoices, Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_prefix="AUTH_BRIDGE_",
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
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

    # Rate limiting: max login/challenge submissions per phone_verifier per window
    login_rate_limit_max_requests: int = 10
    login_rate_limit_window_seconds: int = 300  # 5 minutes

    @property
    def wildcard_base_domain(self) -> str:
        if self.public_base_domain:
            return self.public_base_domain
        parsed = urlparse(self.public_base_url)
        return parsed.netloc or self.public_base_url


@lru_cache
def get_settings() -> Settings:
    return Settings()
