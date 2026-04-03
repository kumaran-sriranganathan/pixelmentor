###############################################################################
# routers/users.py — User profile endpoints (Supabase-backed)
###############################################################################

from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel
from app.middleware.auth import get_current_user
from app.utils.supabase_client import SupabaseService

router = APIRouter()


class UserProfile(BaseModel):
    user_id: str
    display_name: str
    skill_level: str = "beginner"
    photos_analyzed: int = 0
    lessons_completed: int = 0
    streak_days: int = 0
    plan: str = "free"


@router.get("/{user_id}", response_model=UserProfile)
async def get_user(
    user_id: str,
    current_user: dict = Depends(get_current_user),
):
    if current_user.get("sub") != user_id:
        raise HTTPException(status_code=403, detail="Access denied")

    supabase = SupabaseService()
    profile = await supabase.get_user_profile(
        user_id=user_id,
        display_name=current_user.get("name", "PixelMentor User"),
    )
    return UserProfile(**profile)