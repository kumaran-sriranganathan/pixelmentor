"""
backend/app/config.py
----------------------
Pydantic-Settings configuration for PixelMentor backend.
All values are injected as environment variables by Terraform / Container App secrets.
"""

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    # ── Auth ──────────────────────────────────────────────────────────────
    entra_tenant_id: str
    entra_api_client_id: str

    # ── Azure AI Search ───────────────────────────────────────────────────
    azure_search_endpoint: str
    azure_search_key: str
    search_index_name: str = "photography-lessons"

    # ── General ───────────────────────────────────────────────────────────
    environment: str = "dev"
    log_level: str = "INFO"

    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", extra="ignore")


settings = Settings()  # type: ignore[call-arg]  # values come from env
