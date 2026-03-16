from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_prefix="AUTH_BRIDGE_", extra="ignore")

    public_base_url: str = "https://auth.example.test"
    lostfilm_base_url: str = "https://www.lostfilm.today"
    pairing_ttl_seconds: int = 600
    pairing_poll_interval_seconds: int = 5
    claim_lease_ttl_seconds: int = 60


@lru_cache
def get_settings() -> Settings:
    return Settings()
