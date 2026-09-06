from functools import lru_cache
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_prefix="SR_",
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
    )

    database_url: str = "sqlite:///./socialreset.db"
    server_secret: str = ""
    jwt_ttl_seconds: int = 300
    pairing_ttl_seconds: int = 300
    reset_session_ttl_seconds: int = 86400
    event_ttl_seconds: int = 86400
    cleanup_interval_seconds: int = 60
    max_request_bytes: int = 8192
    max_concurrent_ws_per_device: int = 2
    ws_idle_timeout_seconds: int = 120
    cors_allowed_origins: str = ""
    log_level: str = "INFO"


@lru_cache
def get_settings() -> Settings:
    settings = Settings()
    if not settings.server_secret:
        raise RuntimeError("SR_SERVER_SECRET must be set (via env or .env)")
    return settings