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

    from app.utils.supabase_client import SupabaseService
    service = SupabaseService()

    # ── Plan-based monthly photo limit ───────────────────────────────────────
    plan = await service.get_user_plan(user_id)
    monthly_limit = settings.get_photo_limit(plan)
    photos_this_month = await service.get_photos_analyzed_this_month(user_id)

    if photos_this_month >= monthly_limit:
        plan_label = plan.capitalize()
        raise HTTPException(
            status_code=429,
            detail={
                "error": "photo_limit_reached",
                "message": f"You've used all {monthly_limit} photo analyses included in your {plan_label} plan this month.",
                "limit": monthly_limit,
                "used": photos_this_month,
                "plan": plan,
                "upgrade_required": plan == "free",
            }
        )

    analysis_id = str(uuid.uuid4())

    ext = CONTENT_TYPE_TO_EXT.get(request.content_type, "jpg")
    blob_path = f"{user_id}/{analysis_id}/photo.{ext}"

    logger.info(
        f"Photo analysis started — analysis_id={analysis_id} user_id={user_id} "
        f"plan={plan} used={photos_this_month}/{monthly_limit} size={len(image_data)//1024}KB"
    )

    agent = PhotoCoachAgent()

    # Upload to R2 first to get the presigned URL, then run GPT-4o vision analysis
    presigned_url = await upload_to_r2(image_data, blob_path, request.content_type)

    result = await agent.run(
        image_url=presigned_url,
        skill_level=request.skill_level,
        user_id=user_id,
        analysis_id=analysis_id,
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

        # NOTE: increment_photos_analyzed RPC removed — the monthly limit is enforced
        # by counting rows in photo_analyses directly (get_photos_analyzed_this_month),
        # so calling the RPC here was double-counting and causing 8/7-style overruns.

    except Exception as e:
        logger.error(f"Failed to save analysis to Supabase: {e}")

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


@router.get("/usage")
async def get_usage(
    current_user: dict = Depends(get_current_user),
):
    """Returns current month photo usage and limit for the user's plan."""
    from app.utils.supabase_client import SupabaseService
    service = SupabaseService()
    user_id = current_user["sub"]
    plan = await service.get_user_plan(user_id)
    used = await service.get_photos_analyzed_this_month(user_id)
    limit = settings.get_photo_limit(plan)
    return {
        "plan": plan,
        "photos_used_this_month": used,
        "photos_limit": limit,
        "photos_remaining": max(0, limit - used),
    }
