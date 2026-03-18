###############################################################################
# config.py — Settings loaded from environment variables
###############################################################################

from functools import lru_cache
from typing import List
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", extra="ignore")

    # App
    environment: str = "dev"
    allowed_hosts: List[str] = ["*"]
    allowed_origins: List[str] = ["http://localhost:3000", "http://10.0.2.2:3000"]

    # Azure OpenAI
    azure_openai_endpoint: str = ""
    azure_openai_api_key: str = ""
    azure_openai_api_version: str = "2025-01-01"
    gpt4o_deployment: str = "gpt-4o"
    embedding_deployment: str = "text-embedding-3-large"

    # Azure AI Vision — optional
    azure_vision_endpoint: str = ""
    azure_vision_key: str = ""

    # Cosmos DB
    cosmos_endpoint: str = ""
    cosmos_connection_string: str = ""
    cosmos_database: str = "pixelmentor"

    # Azure Blob Storage
    storage_connection_string: str = ""
    storage_account_name: str = ""
    photos_container: str = "user-photos"
    processed_container: str = "processed-photos"

    # Azure AI Search
    azure_search_endpoint: str = ""
    azure_search_key: str = ""
    lessons_index: str = "photography-lessons"

    # Microsoft Entra External ID (replaces Azure AD B2C)
    entra_tenant_name: str = "pixelmentor"           # pixelmentor.onmicrosoft.com
    entra_tenant_id: str = ""                         # Directory (tenant) ID from Entra portal
    entra_api_client_id: str = ""                     # PixelMentor API app client ID
    entra_android_client_id: str = ""                 # PixelMentor Android app client ID

    # Monitoring
    app_insights_connection_string: str = ""

    # Rate limiting
    rate_limit_per_minute: int = 60
    rate_limit_ai_per_minute: int = 10


@lru_cache
def get_settings() -> Settings:
    return Settings()


settings = get_settings()
