###############################################################################
# utils/cosmos_client.py — Cosmos DB service helpers
###############################################################################

import uuid
from datetime import datetime
from app.config import settings

try:
    from azure.cosmos import CosmosClient
    COSMOS_AVAILABLE = True
except ImportError:
    COSMOS_AVAILABLE = False


class CosmosService:
    def __init__(self):
        self.client = None
        self.database = None
        if COSMOS_AVAILABLE and settings.cosmos_endpoint:
            try:
                from azure.identity import DefaultAzureCredential
                credential = DefaultAzureCredential()
                self.client = CosmosClient(settings.cosmos_endpoint, credential)
                self.database = self.client.get_database_client(settings.cosmos_database)
            except Exception as e:
                print(f"Cosmos init failed (non-fatal): {e}")

    def _get_container(self, name: str):
        if not self.database:
            return None
        try:
            return self.database.get_container_client(name)
        except Exception:
            return None

    async def save_analysis(self, user_id: str, analysis_id: str, blob_url: str, result) -> str:
        """Save photo analysis result to Cosmos DB."""
        container = self._get_container("photoAnalyses")
        if container:
            try:
                container.upsert_item({
                    "id": analysis_id,
                    "userId": user_id,
                    "blobUrl": blob_url,
                    "timestamp": datetime.utcnow().isoformat(),
                    "compositionScore": result.composition_score,
                    "visionTags": result.vision_tags,
                })
            except Exception as e:
                print(f"Failed to save analysis (non-fatal): {e}")
        return analysis_id

    async def get_user_analyses(self, user_id: str, limit: int = 10) -> list:
        """Get recent analyses for a user."""
        container = self._get_container("photoAnalyses")
        if not container:
            return []
        try:
            items = list(container.query_items(
                query="SELECT TOP @limit * FROM c WHERE c.userId = @userId ORDER BY c.timestamp DESC",
                parameters=[
                    {"name": "@userId", "value": user_id},
                    {"name": "@limit", "value": limit},
                ],
                enable_cross_partition_query=True,
            ))
            return items
        except Exception:
            return []

    async def get_skill_profile(self, user_id: str) -> dict:
        """Get user skill profile. Returns defaults if not found."""
        container = self._get_container("skillProfiles")
        if not container:
            return {"level": "beginner", "strengths": [], "areas_to_improve": []}
        try:
            item = container.read_item(item=user_id, partition_key=user_id)
            return item
        except Exception:
            return {"level": "beginner", "strengths": [], "areas_to_improve": []}

    async def append_chat_message(self, user_id: str, message: dict):
        """Append a message to chat history."""
        container = self._get_container("chatHistory")
        if container:
            try:
                container.upsert_item({
                    "id": str(uuid.uuid4()),
                    "userId": user_id,
                    "role": message.get("role"),
                    "content": message.get("content"),
                    "timestamp": datetime.utcnow().isoformat(),
                })
            except Exception as e:
                print(f"Failed to save chat message (non-fatal): {e}")

    async def get_chat_history(self, user_id: str, limit: int = 10) -> list:
        """Get recent chat history for a user, formatted for OpenAI messages."""
        container = self._get_container("chatHistory")
        if not container:
            return []
        try:
            items = list(container.query_items(
                query="SELECT TOP @limit * FROM c WHERE c.userId = @userId ORDER BY c.timestamp DESC",
                parameters=[
                    {"name": "@userId", "value": user_id},
                    {"name": "@limit", "value": limit},
                ],
                enable_cross_partition_query=True,
            ))
            # Reverse so oldest first, format for OpenAI
            return [{"role": i["role"], "content": i["content"]} for i in reversed(items)]
        except Exception:
            return []
