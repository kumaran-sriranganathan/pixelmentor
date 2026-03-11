###############################################################################
# routers/users.py — User profile endpoints
###############################################################################

from fastapi import APIRouter
from pydantic import BaseModel
from typing import Optional

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
async def get_user(user_id: str):
    return UserProfile(
        user_id=user_id,
        display_name="PixelMentor User",
        skill_level="beginner",
        photos_analyzed=0,
        lessons_completed=0,
        streak_days=0,
        plan="free",
    )
