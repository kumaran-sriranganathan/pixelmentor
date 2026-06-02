###############################################################################
# utils/supabase_client.py — Supabase service helpers
#
# Security design:
#   get_supabase_client()   → uses ANON key  — respects RLS policies
#   get_supabase_admin()    → uses SERVICE key — bypasses RLS, use sparingly
###############################################################################

from supabase import create_client, Client
from app.config import settings


def get_supabase_client() -> Client:
    return create_client(settings.supabase_url, settings.supabase_anon_key)


def get_supabase_admin() -> Client:
    return create_client(settings.supabase_url, settings.supabase_service_key)


class SupabaseService:
    def __init__(self):
        self.db = get_supabase_client()
        self._admin = get_supabase_admin()

    # ── Plan helpers ──────────────────────────────────────────────────────────

    async def get_user_plan(self, user_id: str) -> str:
        """Returns the user's current plan: 'free', 'pro', or 'premium'."""
        try:
            response = (
                self._admin.table("user_profiles")
                .select("plan")
                .eq("user_id", user_id)
                .limit(1)
                .execute()
            )
            if response.data:
                return response.data[0].get("plan", "free")
        except Exception:
            pass
        return "free"

    async def get_photos_analyzed_this_month(self, user_id: str) -> int:
        """Count photo analyses submitted by the user in the current calendar month."""
        try:
            from datetime import datetime, timezone
            now = datetime.now(timezone.utc)
            # First day of current month
            month_start = now.replace(day=1, hour=0, minute=0, second=0, microsecond=0).isoformat()
            response = (
                self._admin.table("photo_analyses")
                .select("id", count="exact")
                .eq("user_id", user_id)
                .gte("created_at", month_start)
                .execute()
            )
            return response.count or 0
        except Exception:
            return 0

    async def get_chat_messages_today(self, user_id: str) -> int:
        """Count user chat messages sent today."""
        try:
            from datetime import datetime, timezone
            now = datetime.now(timezone.utc)
            day_start = now.replace(hour=0, minute=0, second=0, microsecond=0).isoformat()
            response = (
                self._admin.table("chat_history")
                .select("id", count="exact")
                .eq("user_id", user_id)
                .eq("role", "user")
                .gte("created_at", day_start)
                .execute()
            )
            return response.count or 0
        except Exception:
            return 0

    async def get_quiz_attempts_this_month(self, user_id: str) -> int:
        """Count quiz attempts by the user in the current calendar month."""
        try:
            from datetime import datetime, timezone
            now = datetime.now(timezone.utc)
            month_start = now.replace(day=1, hour=0, minute=0, second=0, microsecond=0).isoformat()
            response = (
                self._admin.table("quiz_attempts")
                .select("id", count="exact")
                .eq("user_id", user_id)
                .gte("created_at", month_start)
                .execute()
            )
            return response.count or 0
        except Exception:
            return 0

    async def record_quiz_attempt(self, user_id: str, topic: str) -> None:
        """Record a quiz attempt for rate limiting purposes."""
        try:
            self._admin.table("quiz_attempts").insert({
                "user_id": user_id,
                "topic": topic,
            }).execute()
        except Exception:
            pass  # non-fatal

    # ── Existing methods ──────────────────────────────────────────────────────

    async def save_analysis(self, user_id: str, analysis_id: str, blob_url: str, result) -> str:
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

    async def append_chat_message(self, user_id: str, message: dict, session_id: str | None = None):
        self._admin.table("chat_history").insert({
            "user_id": user_id,
            "role": message.get("role"),
            "content": message.get("content"),
            "session_id": session_id,
        }).execute()

    async def get_chat_history(self, user_id: str, limit: int = 10, session_id: str | None = None) -> list:
        try:
            query = (
                self._admin.table("chat_history")
                .select("role, content")
                .eq("user_id", user_id)
            )
            if session_id:
                query = query.eq("session_id", session_id)
            response = (
                query
                .order("created_at", desc=False)
                .limit(limit)
                .execute()
            )
            return response.data if response.data else []
        except Exception:
            return []

    async def get_user_profile(self, user_id: str, display_name: str = None) -> dict:
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
        """Legacy daily counter — kept for backward compat."""
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
