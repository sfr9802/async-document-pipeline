from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    service_name: str = "document-analysis-worker"
    environment: str = "local"
    version: str = "0.1.0"
    debug: bool = False

    # Analysis provider: "mock" or "local"
    analysis_provider: str = "local"

    # Core claim client
    core_claim_url: str = "http://localhost:8082/internal/jobs/claim"
    core_claim_timeout_seconds: float = 5.0
    core_claim_stub_mode: bool = True

    # Callback delivery
    callback_timeout_seconds: float = 5.0
    callback_stub_mode: bool = True
    callback_max_attempts: int = 3
    callback_retry_backoff_seconds: float = 1.0

    # Storage
    storage_base_dir: str = ".artifacts"
    storage_backend: str = "filesystem"

    # Internal auth
    internal_shared_secret: str = "local-dev-secret"


@lru_cache
def get_settings() -> Settings:
    return Settings()
