"""
backend/app/config.py
"""

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    # ── Azure AI Search (legacy) ───────────────────────────────────────────────
    azure_search_endpoint: str = ""
    azure_search_key: str = ""
    search_index_name: str = "photography-lessons"

    # ── Security ──────────────────────────────────────────────────────────────
    allowed_hosts: list[str] = ["*"]
    allowed_origins: list[str] = ["*"]

    # ── Rate limiting ─────────────────────────────────────────────────────────
    rate_limit_per_minute: int = 60
    rate_limit_ai_per_minute: int = 20

    # ── General ───────────────────────────────────────────────────────────────
    environment: str = "dev"
    log_level: str = "INFO"

    # ── Supabase ──────────────────────────────────────────────────────────────
    supabase_url: str = ""
    supabase_anon_key: str = ""
    supabase_service_key: str = ""
    supabase_jwt_secret: str = ""

    # ── Cloudflare R2 ─────────────────────────────────────────────────────────
    cloudflare_account_id: str = ""
    r2_access_key_id: str = ""
    r2_secret_access_key: str = ""
    r2_bucket_name: str = "pixelmentor-photos"
    r2_public_domain: str = ""

    # ── Typesense ─────────────────────────────────────────────────────────────
    typesense_host: str = ""
    typesense_api_key: str = ""
    lessons_index: str = "lessons"

    # ── OpenAI ────────────────────────────────────────────────────────────────
    openai_api_key: str = ""

    # ── Monitoring ────────────────────────────────────────────────────────────
    sentry_dsn: str = ""

    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", extra="ignore")


settings = Settings()  # type: ignore[call-arg]  # values come from env


def get_settings() -> Settings:
    return settings
