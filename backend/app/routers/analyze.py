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
from app.utils.supabase_client import get_supabase_client, get_supabase_admin

router = APIRouter()
logger = logging.getLogger(__name__)

ALLOWED_CONTENT_TYPES = {"image/jpeg", "image/png", "image/heic", "image/webp"}
CONTENT_TYPE_TO_EXT = {
    "image/jpeg": "jpg",
    "image/png": "png",
    "image/heic": "heic",
    "image/webp": "webp",
}
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

    user_id = current_user["sub"]

    # ── Fix 5: Per-user daily rate limit ────────────────────────────────────
    # Prevents a single user from burning OpenAI budget
    from app.utils.supabase_client import SupabaseService
    service = SupabaseService()
    photos_today = await service.get_photos_analyzed_today(user_id)
    if photos_today >= settings.max_photos_per_user_per_day:
        raise HTTPException(
            status_code=429,
            detail=f"Daily photo analysis limit reached ({settings.max_photos_per_user_per_day}/day). Try again tomorrow."
        )

    analysis_id = str(uuid.uuid4())

    # ── Fix 7: Sanitise blob path — never use user-supplied filename ─────────
    # Derive extension from validated content_type only — immune to path traversal
    ext = CONTENT_TYPE_TO_EXT.get(request.content_type, "jpg")
    blob_path = f"{user_id}/{analysis_id}/photo.{ext}"

    logger.info(f"Photo analysis started — analysis_id={analysis_id} user_id={user_id} size={len(image_data)//1024}KB")

    # ── Run R2 upload and GPT-4o analysis in parallel ────────────────────────
    # GPT-4o receives the image as base64 directly while R2 upload runs
    # concurrently — saves 1-3 seconds vs sequential execution.
    import asyncio

    agent = PhotoCoachAgent()

    async def run_analysis():
        return await agent.run(
            image_base64=request.image_base64,
            image_content_type=request.content_type,
            skill_level=request.skill_level,
            user_id=user_id,
            analysis_id=analysis_id,
        )

    presigned_url, result = await asyncio.gather(
        upload_to_r2(image_data, blob_path, request.content_type),
        run_analysis(),
    )

    # Persist to Supabase
    try:
        supabase = get_supabase_admin()
        supabase.table("photo_analyses").insert({
            "id": analysis_id,
            "user_id": user_id,
            "composition_score": int(result.composition_score),
            "feedback": [f.dict() for f in result.feedback],
            "edit_suggestions": result.edit_suggestions.dict() if result.edit_suggestions else None,
            "vision_tags": result.vision_tags,
            "blob_path": blob_path,
        }).execute()

        # Privileged RPC — use admin client
        get_supabase_admin().rpc("increment_photos_analyzed", {"p_user_id": user_id}).execute()

    except Exception as e:
        logger.error(f"Failed to save analysis to Supabase: {e}")
        # Don't fail the request just because persistence failed

    logger.info(f"Photo analysis complete — analysis_id={analysis_id} score={result.composition_score}")

    return result


@router.get("/health")
async def health_check():
    """Keep-alive endpoint — pinged every 5 minutes to prevent Railway cold starts."""
    return {"status": "ok"}


@router.get("/history")
async def get_analysis_history(
    limit: int = 10,
    current_user: dict = Depends(get_current_user),
):
    supabase = get_supabase_admin()
    response = supabase.table("photo_analyses") \
        .select("*") \
        .eq("user_id", current_user["sub"]) \
        .order("created_at", desc=True) \
        .limit(limit) \
        .execute()
    return response.data
