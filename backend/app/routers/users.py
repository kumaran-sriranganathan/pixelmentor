###############################################################################
# routers/users.py — User profile endpoints (Supabase-backed)
###############################################################################

import asyncio
import logging
from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel
from app.middleware.auth import get_current_user
from app.utils.supabase_client import SupabaseService, get_supabase_admin

router = APIRouter()
logger = logging.getLogger(__name__)


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

    # ── Guard against Railway cold-start / Supabase hangs ─────────────────────
    # Without a timeout the coroutine can hang indefinitely, causing the Android
    # client to show a perpetual spinner. We fail fast with a 504 so the app can
    # show a "Retry" button instead.
    try:
        profile = await asyncio.wait_for(
            supabase.get_user_profile(
                user_id=user_id,
                display_name=current_user.get("name", "PixelMentor User"),
            ),
            timeout=8.0,
        )
    except asyncio.TimeoutError:
        logger.warning(f"Profile fetch timed out for user_id={user_id}")
        raise HTTPException(
            status_code=504,
            detail="Profile load timed out. Please try again.",
        )

    return UserProfile(**profile)


@router.delete("/{user_id}", status_code=200)
async def delete_account(
    user_id: str,
    current_user: dict = Depends(get_current_user),
):
    """
    Permanently deletes the user's account and all associated data.
    Only the authenticated user can delete their own account.

    Deletes in order:
      1. All user data from application tables
      2. The Supabase Auth user record

    This operation is irreversible.
    """
    # Only allow users to delete their own account
    if current_user.get("sub") != user_id:
        raise HTTPException(status_code=403, detail="Access denied")

    supabase = get_supabase_admin()
    logger.info(f"Account deletion requested for user_id={user_id}")

    try:
        # ── Delete all user data from application tables ───────────────────
        # Order matters — delete child records before parent records

        tables = [
            "lesson_completions",
            "photo_analyses",
            "chat_history",
            "skill_profiles",
            "quiz_cache",       # only user-specific rows if any
            "user_profiles",
        ]

        for table in tables:
            try:
                supabase.table(table) \
                    .delete() \
                    .eq("user_id", user_id) \
                    .execute()
                logger.info(f"Deleted {table} records for user_id={user_id}")
            except Exception as e:
                # Log but continue — don't fail the whole deletion if one
                # table delete fails (e.g. table doesn't have user_id column)
                logger.warning(f"Failed to delete from {table} for user_id={user_id}: {e}")

        # ── Delete the Supabase Auth user ──────────────────────────────────
        # This uses the admin auth API — requires service key
        supabase.auth.admin.delete_user(user_id)
        logger.info(f"Deleted Supabase Auth user user_id={user_id}")

        return {"message": "Account deleted successfully"}

    except Exception as e:
        logger.error(f"Failed to delete account for user_id={user_id}: {e}")
        raise HTTPException(
            status_code=500,
            detail="Failed to delete account. Please contact support."
        )
