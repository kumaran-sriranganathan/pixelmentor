###############################################################################
# routers/users.py — User profile endpoints (Supabase-backed)
###############################################################################

import asyncio
import logging
from fastapi import APIRouter, Depends, HTTPException, Request
from pydantic import BaseModel
from app.middleware.auth import get_current_user
from app.utils.supabase_client import SupabaseService, get_supabase_admin

router = APIRouter()
logger = logging.getLogger(__name__)


@router.post("/signup-check")
async def signup_check(request: Request):
    """
    Supabase Auth Hook — called before every new sign-up.
    Blocks re-registration from deleted accounts to prevent free tier abuse.

    Supabase sends: { "event": "signup", "user": { "email": "..." }, "secrets": "..." }
    Return 200 with {} to allow, or raise HTTPException to block.
    """
    try:
        body = await request.json()

        # ── Verify the request is from Supabase ───────────────────────────────
        hook_secret = settings.supabase_hook_secret
        if hook_secret:
            incoming_secret = body.get("secrets", "")
            if incoming_secret != hook_secret:
                logger.warning("signup-check called with invalid hook secret")
                raise HTTPException(status_code=401, detail="Unauthorized")

        # ── Check blocklist ───────────────────────────────────────────────────
        email = body.get("user", {}).get("email", "").lower().strip()
        if not email:
            return {}

        supabase = get_supabase_admin()
        result = supabase.table("deleted_accounts") \
            .select("email") \
            .eq("email", email) \
            .limit(1) \
            .execute()

        if result.data:
            logger.warning(f"Blocked re-registration attempt for deleted email={email}")
            raise HTTPException(
                status_code=422,
                detail="This email address is not eligible for registration."
            )

        return {}

    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Signup check error: {e}")
        # Fail open — don't block legitimate sign-ups due to our own errors
        return {}


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

        # ── Record email in blocklist before deleting auth user ───────────────
        # This prevents the user from re-registering with the same email to
        # bypass plan limits (e.g. use up free tier, delete, re-register).
        # Must be done before auth.admin.delete_user() since that wipes the
        # email from auth.users.
        try:
            email = current_user.get("email")
            if email:
                supabase.table("deleted_accounts").upsert({
                    "email": email.lower().strip(),
                }).execute()
                logger.info(f"Recorded deleted email={email} in blocklist")
        except Exception as e:
            # Non-fatal — log and continue with deletion
            logger.warning(f"Failed to record deleted account email: {e}")

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
