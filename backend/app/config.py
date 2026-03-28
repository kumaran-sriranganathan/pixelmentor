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

    # ── Monitoring ────────────────────────────────────────────────────────
    app_insights_connection_string: str = ""

    # ── Security ──────────────────────────────────────────────────────────
    allowed_hosts: list[str] = ["*"]
    allowed_origins: list[str] = ["*"]

    # ── Rate limiting ─────────────────────────────────────────────────────
    rate_limit_per_minute: int = 60
    rate_limit_ai_per_minute: int = 20

    # ── General ───────────────────────────────────────────────────────────
    environment: str = "dev"
    log_level: str = "INFO"

    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", extra="ignore")