###############################################################################
# utils/supabase_client.py — Supabase service helpers
# Replaces cosmos_client.py — do not delete cosmos_client.py yet
###############################################################################

from supabase import create_client, Client
from app.config import settings


def get_supabase() -> Client:
    return create_client(settings.supabase_url, settings.supabase_service_key)

# Alias used by analyze.py
def get_supabase_client() -> Client:
    return get_supabase()

class SupabaseService:
    def __init__(self):
        self.db = get_supabase()

    async def save_analysis(self, user_id: str, analysis_id: str, blob_url: str, result) -> str:
        """Save photo analysis result to Supabase."""
        self.db.table("photo_analyses").upsert({
            "id": analysis_id,
            "user_id": user_id,
            "blob_url": blob_url,
            "composition_score": int(result.composition_score),
            "vision_tags": result.vision_tags,
        }).execute()
        self.db.rpc("increment_photos_analyzed", {"p_user_id": user_id}).execute()
        return analysis_id

    async def get_user_analyses(self, user_id: str, limit: int = 10) -> list:
        """Get recent analyses for a user."""
        response = (
            self.db.table("photo_analyses")
            .select("*")
            .eq("user_id", user_id)
            .order("created_at", desc=True)
            .limit(limit)
            .execute()
        )
        return response.data

    async def get_skill_profile(self, user_id: str) -> dict:
        """Get user skill profile. Returns defaults if not found."""
        try:
            response = (
                self.db.table("skill_profiles")
                .select("*")
                .eq("user_id", user_id)
                .single()
                .execute()
            )
            return response.data if response.data else {
                "level": "beginner", "strengths": [], "areas_to_improve": []
            }
        except Exception:
            return {"level": "beginner", "strengths": [], "areas_to_improve": []}

    async def append_chat_message(self, user_id: str, message: dict):
        """Append a message to chat history."""
        self.db.table("chat_history").insert({
            "user_id": user_id,
            "role": message.get("role"),
            "content": message.get("content"),
        }).execute()

    async def get_chat_history(self, user_id: str, limit: int = 10) -> list:
        """Get recent chat history formatted for OpenAI messages."""
        response = (
            self.db.table("chat_history")
            .select("role, content")
            .eq("user_id", user_id)
            .order("created_at", desc=False)
            .limit(limit)
            .execute()
        )
        return response.data if response.data else []

    async def get_user_profile(self, user_id: str, display_name: str = None) -> dict:
        """Get user profile, creating it on first access."""
        try:
            response = (
                self.db.table("user_profiles")
                .select("*")
                .eq("user_id", user_id)
                .single()
                .execute()
            )
            if response.data:
                return response.data
        except Exception:
            pass

        # Create profile on first access
        profile = {
            "user_id": user_id,
            "display_name": display_name or "PixelMentor User",
            "skill_level": "beginner",
            "photos_analyzed": 0,
            "lessons_completed": 0,
            "streak_days": 0,
            "plan": "free",
        }
        self.db.table("user_profiles").upsert(profile).execute()
        return profile