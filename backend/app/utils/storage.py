###############################################################################
# utils/storage.py — Azure Blob Storage helpers
###############################################################################

import uuid
from datetime import datetime, timedelta, timezone
from app.config import settings

try:
    from azure.storage.blob import BlobServiceClient, generate_blob_sas, BlobSasPermissions
    STORAGE_AVAILABLE = True
except ImportError:
    STORAGE_AVAILABLE = False


def get_blob_service_client():
    if not STORAGE_AVAILABLE or not settings.storage_connection_string:
        return None
    try:
        return BlobServiceClient.from_connection_string(settings.storage_connection_string)
    except Exception:
        return None


async def upload_photo(image_data: bytes, blob_path: str, content_type: str = "image/jpeg") -> str:
    """Upload photo bytes to blob storage. Returns the blob URL."""
    client = get_blob_service_client()

    if not client:
        return f"https://{settings.storage_account_name}.blob.core.windows.net/{settings.photos_container}/{blob_path}"

    try:
        blob_client = client.get_blob_client(container=settings.photos_container, blob=blob_path)
        blob_client.upload_blob(image_data, overwrite=True, content_settings={"content_type": content_type})
        return blob_client.url
    except Exception as e:
        raise RuntimeError(f"Failed to upload photo: {e}")


async def generate_sas_url(blob_path: str, expiry_minutes: int = 10) -> str:
    """Generate a time-limited SAS URL for a blob."""
    client = get_blob_service_client()
    if not client or not settings.storage_account_name:
        return f"https://{settings.storage_account_name}.blob.core.windows.net/{settings.photos_container}/{blob_path}"

    try:
        account_key = client.credential.account_key
        sas_token = generate_blob_sas(
            account_name=settings.storage_account_name,
            container_name=settings.photos_container,
            blob_name=blob_path,
            account_key=account_key,
            permission=BlobSasPermissions(read=True),
            expiry=datetime.now(timezone.utc) + timedelta(minutes=expiry_minutes),
        )
        return f"https://{settings.storage_account_name}.blob.core.windows.net/{settings.photos_container}/{blob_path}?{sas_token}"
    except Exception as e:
        raise RuntimeError(f"Failed to generate SAS URL: {e}")
