###############################################################################
# utils/supabase_client.py — Supabase service helpers
#
# Security design:
#   get_supabase_client()   → uses ANON key  — respects RLS policies
#   get_supabase_admin()    → uses SERVICE key — bypasses RLS, use sparingly
#
# Most read/write operations should use the anon key client.
# The service key client is only used for privileged RPCs (mark_lesson_complete,
# increment_photos_analyzed) that use SECURITY DEFINER functions in Postgres,
# where the function itself enforces the authorization logic.
###############################################################################

from supabase import create_client, Client
from app.config import settings


def get_supabase_client() -> Client:
    """
    Standard client — uses the anon key.
    Respects Row Level Security (RLS) policies on all tables.
    Use this for all regular data access.
    """
    return create_client(settings.supabase_url, settings.supabase_anon_key)


def get_supabase_admin() -> Client:
    """
    Admin client — uses the service role key.
    BYPASSES Row Level Security. Only use for:
      - SECURITY DEFINER RPCs that handle their own auth checks
      - Administrative tasks that genuinely need cross-user access
    Never use this for user-facing data reads.
    """
    return create_client(settings.supabase_url, settings.supabase_service_key)


class SupabaseService:
    def __init__(self):
        # Most operations use the anon client (RLS enforced)
        self.db = get_supabase_client()
        # Admin client available for privileged RPCs only
        self._admin = get_supabase_admin()

    async def save_analysis(self, user_id: str, analysis_id: str, blob_url: str, result) -> str:
        """Save photo analysis result to Supabase."""
        self._admin.table("photo_analyses").upsert({
            "id": analysis_id,
            "user_id": user_id,
            "blob_url": blob_url,
            "composition_score": int(result.composition_score),
            "vision_tags": result.vision_tags,
        }).execute()
        self._admin.rpc("increment_photos_analyzed", {"p_user_id": user_id}).execute()
        return analysis_id

    async def get_user_analyses(self, user_id: str, limit: int = 10) -> list:
        """Get recent analyses for a user."""
        try:
            response = (
                self._admin.table("photo_analyses")
                .select("*")
                .eq("user_id", user_id)
                .order("created_at", desc=True)
                .limit(limit)
                .execute()
            )
            return response.data if response.data else []
        except Exception:
            return []

    async def get_skill_profile(self, user_id: str) -> dict:
        """Get user skill profile. Returns defaults if not found."""
        try:
            response = (
                self._admin.table("skill_profiles")
                .select("*")
                .eq("user_id", user_id)
                .limit(1)
                .execute()
            )
            if response.data:
                return response.data[0]
        except Exception:
            pass
        return {"level": "beginner", "strengths": [], "areas_to_improve": []}

    async def append_chat_message(self, user_id: str, message: dict):
        """Append a message to chat history."""
        self._admin.table("chat_history").insert({
            "user_id": user_id,
            "role": message.get("role"),
            "content": message.get("content"),
        }).execute()

    async def get_chat_history(self, user_id: str, limit: int = 10) -> list:
        """Get recent chat history formatted for OpenAI messages."""
        try:
            response = (
                self._admin.table("chat_history")
                .select("role, content")
                .eq("user_id", user_id)
                .order("created_at", desc=False)
                .limit(limit)
                .execute()
            )
            return response.data if response.data else []
        except Exception:
            return []

    async def get_user_profile(self, user_id: str, display_name: str = None) -> dict:
        """Get user profile, creating it on first access.
        Uses admin client for upsert — RLS blocks anon key from inserting
        new rows without a user auth token in the request context.
        """
        try:
            response = (
                self._admin.table("user_profiles")
                .select("*")
                .eq("user_id", user_id)
                .limit(1)
                .execute()
            )
            if response.data:
                return response.data[0]
        except Exception:
            pass

        # Create profile on first access using admin client
        profile = {
            "user_id": user_id,
            "display_name": display_name or "PixelMentor User",
            "skill_level": "beginner",
            "photos_analyzed": 0,
            "lessons_completed": 0,
            "streak_days": 0,
            "plan": "free",
        }
        try:
            self._admin.table("user_profiles").upsert(profile).execute()
        except Exception as e:
            import logging
            logging.getLogger(__name__).error(f"Failed to create user profile: {e}")
        return profile

    async def get_photos_analyzed_today(self, user_id: str) -> int:
        """Count photo analyses submitted by the user in the last 24 hours."""
        try:
            from datetime import datetime, timezone, timedelta
            since = (datetime.now(timezone.utc) - timedelta(hours=24)).isoformat()
            response = (
                self._admin.table("photo_analyses")
                .select("id", count="exact")
                .eq("user_id", user_id)
                .gte("created_at", since)
                .execute()
            )
            return response.count or 0
        except Exception:
            return 0
