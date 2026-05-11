"""
backend/app/config.py
"""

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    # ── Azure AI Search (legacy — kept for migration period) ──────────────────
    azure_search_endpoint: str = ""
    azure_search_key: str = ""
    search_index_name: str = "photography-lessons"

    # ── Security ──────────────────────────────────────────────────────────────
    # Dev: ["*"] is acceptable. Prod: lock to your actual domain/Railway URL.
    # Set via environment variable: ALLOWED_ORIGINS='["https://pixelmentor.app"]'
    allowed_hosts: list[str] = ["*"]
    allowed_origins: list[str] = ["*"]

    # ── Rate limiting ─────────────────────────────────────────────────────────
    rate_limit_per_minute: int = 60
    rate_limit_ai_per_minute: int = 20
    # Per-user photo analysis limit — prevents runaway OpenAI costs
    max_photos_per_user_per_day: int = 20

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

    # ── OpenAI ────────────────────────────────────────────────────────────────
    openai_api_key: str = ""

    # ── Monitoring ────────────────────────────────────────────────────────────
    sentry_dsn: str = ""

    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", extra="ignore")

    @property
    def is_prod(self) -> bool:
        return self.environment == "prod"

    @property
    def effective_allowed_origins(self) -> list[str]:
        """
        In prod, never allow wildcard origins.
        Raises at startup if prod is misconfigured with wildcard.
        """
        if self.is_prod and self.allowed_origins == ["*"]:
            raise ValueError(
                "ALLOWED_ORIGINS must be explicitly set in production. "
                "Set it to your Railway URL, e.g. "
                "ALLOWED_ORIGINS='[\"https://pixelmentor-production.up.railway.app\"]'"
            )
        return self.allowed_origins

    @property
    def effective_allowed_hosts(self) -> list[str]:
        """In prod, never allow wildcard hosts."""
        if self.is_prod and self.allowed_hosts == ["*"]:
            raise ValueError(
                "ALLOWED_HOSTS must be explicitly set in production. "
                "Set it to your Railway domain, e.g. "
                "ALLOWED_HOSTS='[\"pixelmentor-production.up.railway.app\"]'"
            )
        return self.allowed_hosts


settings = Settings()  # type: ignore[call-arg]  # values come from env


def get_settings() -> Settings:
    return settings
