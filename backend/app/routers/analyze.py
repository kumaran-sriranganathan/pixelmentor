###############################################################################
# routers/analyze.py — Photo analysis endpoint
# Accepts base64-encoded image in JSON body
###############################################################################

import logging
import uuid
import base64
import boto3
from botocore.client import Config

from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel

from app.agents.photo_coach import PhotoCoachAgent
from app.models.analysis import PhotoAnalysisResponse
from app.middleware.auth import get_current_user
from app.config import settings
from app.utils.supabase_client import get_supabase_client

router = APIRouter()
logger = logging.getLogger(__name__)

ALLOWED_CONTENT_TYPES = {"image/jpeg", "image/png", "image/heic", "image/webp"}
MAX_SIZE_BYTES = 20 * 1024 * 1024  # 20MB


class PhotoAnalysisRequest(BaseModel):
    image_base64: str
    content_type: str = "image/jpeg"
    filename: str = "photo.jpg"
    skill_level: str = "intermediate"


def get_r2_client():
    return boto3.client(
        "s3",
        endpoint_url=f"https://{settings.cloudflare_account_id}.r2.cloudflarestorage.com",
        aws_access_key_id=settings.r2_access_key_id,
        aws_secret_access_key=settings.r2_secret_access_key,
        config=Config(signature_version="s3v4"),
        region_name="auto",
    )


async def upload_to_r2(image_data: bytes, blob_path: str, content_type: str) -> str:
    import asyncio
    r2 = get_r2_client()
    loop = asyncio.get_event_loop()
    await loop.run_in_executor(
        None,
        lambda: r2.put_object(
            Bucket=settings.r2_bucket_name,
            Key=blob_path,
            Body=image_data,
            ContentType=content_type,
        )
    )
    # Return a presigned URL valid for 10 minutes for the AI pipeline
    url = await loop.run_in_executor(
        None,
        lambda: r2.generate_presigned_url(
            "get_object",
            Params={"Bucket": settings.r2_bucket_name, "Key": blob_path},
            ExpiresIn=600,
        )
    )
    return url


@router.post("/photo", response_model=PhotoAnalysisResponse)
async def analyze_photo(
    request: PhotoAnalysisRequest,
    current_user: dict = Depends(get_current_user),
):
    if request.content_type not in ALLOWED_CONTENT_TYPES:
        raise HTTPException(status_code=400, detail="Only JPEG, PNG, HEIC, WEBP images supported")

    try:
        image_data = base64.b64decode(request.image_base64)
    except Exception:
        raise HTTPException(status_code=400, detail="Invalid base64 image data")

    if len(image_data) > MAX_SIZE_BYTES:
        raise HTTPException(status_code=413, detail="Image must be under 20MB")

    analysis_id = str(uuid.uuid4())
    user_id = current_user["sub"]

    logger.info(f"Photo analysis started — analysis_id={analysis_id} user_id={user_id} size={len(image_data)//1024}KB")

    # Upload to Cloudflare R2 and get presigned URL for AI
    blob_path = f"{user_id}/{analysis_id}/{request.filename}"
    presigned_url = await upload_to_r2(image_data, blob_path, request.content_type)

    # Run PhotoCoach agent pipeline
    agent = PhotoCoachAgent()
    result = await agent.run(
        image_url=presigned_url,
        skill_level=request.skill_level,
        user_id=user_id,
        analysis_id=analysis_id,
    )

    # Persist to Supabase
    try:
        supabase = get_supabase_client()
        supabase.table("photo_analyses").insert({
            "id": analysis_id,
            "user_id": user_id,
            "composition_score": int(result.composition_score),
            "feedback": [f.dict() for f in result.feedback],
            "edit_suggestions": result.edit_suggestions.dict() if result.edit_suggestions else None,
            "vision_tags": result.vision_tags,
            "blob_path": blob_path,
        }).execute()
    except Exception as e:
        logger.error(f"Failed to save analysis to Supabase: {e}")
        # Don't fail the request just because persistence failed

    logger.info(f"Photo analysis complete — analysis_id={analysis_id} score={result.composition_score}")

    return result


@router.get("/history")
async def get_analysis_history(
    limit: int = 10,
    current_user: dict = Depends(get_current_user),
):
    supabase = get_supabase_client()
    response = supabase.table("photo_analyses") \
        .select("*") \
        .eq("user_id", current_user["sub"]) \
        .order("created_at", desc=True) \
        .limit(limit) \
        .execute()
    return response.data
