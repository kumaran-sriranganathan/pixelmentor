###############################################################################
# routers/analyze.py — Photo analysis endpoint
# Accepts base64-encoded image in JSON body to avoid python-multipart dependency
###############################################################################

import logging
import uuid
import base64

from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel
from typing import Optional

from app.agents.photo_coach import PhotoCoachAgent
from app.models.analysis import PhotoAnalysisResponse
from app.utils.storage import upload_photo, generate_sas_url
from app.utils.cosmos_client import CosmosService
from app.middleware.auth import get_current_user
from app.config import settings

router = APIRouter()
logger = logging.getLogger(__name__)

ALLOWED_CONTENT_TYPES = {"image/jpeg", "image/png", "image/heic", "image/webp"}
MAX_SIZE_BYTES = 20 * 1024 * 1024  # 20MB


class PhotoAnalysisRequest(BaseModel):
    image_base64: str           # Base64-encoded image bytes
    content_type: str = "image/jpeg"
    filename: str = "photo.jpg"
    skill_level: str = "intermediate"


@router.post("/photo", response_model=PhotoAnalysisResponse)
async def analyze_photo(
    request: PhotoAnalysisRequest,
    current_user: dict = Depends(get_current_user),
):
    """
    Upload a photo and receive AI-powered photography coaching.
    Send image as base64-encoded string in JSON body.
    """
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

    # Upload to Blob Storage
    blob_path = f"{user_id}/{analysis_id}/{request.filename}"
    blob_url = await upload_photo(image_data, blob_path, request.content_type)

    # Generate short-lived SAS URL for AI services
    sas_url = await generate_sas_url(blob_path, expiry_minutes=10)

    # Run PhotoCoach agent pipeline
    agent = PhotoCoachAgent()
    result = await agent.run(
        image_url=sas_url,
        skill_level=request.skill_level,
        user_id=user_id,
        analysis_id=analysis_id,
    )

    # Persist to Cosmos DB
    cosmos = CosmosService()
    await cosmos.save_analysis(
        user_id=user_id,
        analysis_id=analysis_id,
        blob_url=blob_url,
        result=result,
    )

    logger.info(f"Photo analysis complete — analysis_id={analysis_id} score={result.composition_score}")

    return result


@router.get("/history")
async def get_analysis_history(
    limit: int = 10,
    current_user: dict = Depends(get_current_user),
):
    """Return the user's recent photo analysis history."""
    cosmos = CosmosService()
    return await cosmos.get_user_analyses(current_user["sub"], limit=limit)
