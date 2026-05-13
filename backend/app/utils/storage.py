###############################################################################
# utils/storage.py — Cloudflare R2 storage helpers
# Replaces Azure Blob Storage — do not delete old version from git history
###############################################################################

import boto3
from botocore.config import Config
from app.config import settings


def get_r2_client():
    return boto3.client(
        "s3",
        endpoint_url=f"https://{settings.r2_account_id}.r2.cloudflarestorage.com",
        aws_access_key_id=settings.r2_access_key_id,
        aws_secret_access_key=settings.r2_secret_access_key,
        config=Config(signature_version="s3v4"),
        region_name="auto",
    )


async def upload_photo(image_data: bytes, blob_path: str, content_type: str = "image/jpeg") -> str:
    """Upload photo bytes to R2. Returns the public URL."""
    client = get_r2_client()
    client.put_object(
        Bucket=settings.r2_bucket_name,
        Key=blob_path,
        Body=image_data,
        ContentType=content_type,
    )
    if settings.r2_public_domain:
        return f"https://{settings.r2_public_domain}/{blob_path}"
    return f"https://{settings.r2_account_id}.r2.cloudflarestorage.com/{settings.r2_bucket_name}/{blob_path}"


async def generate_sas_url(blob_path: str, expiry_minutes: int = 10) -> str:
    """Generate a time-limited presigned URL for a photo."""
    client = get_r2_client()
    url = client.generate_presigned_url(
        "get_object",
        Params={"Bucket": settings.r2_bucket_name, "Key": blob_path},
        ExpiresIn=expiry_minutes * 60,
    )
    return url