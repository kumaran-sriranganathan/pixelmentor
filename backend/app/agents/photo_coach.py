###############################################################################
# agents/photo_coach.py
# LangGraph agentic pipeline for photo analysis
###############################################################################

import asyncio
import json
import logging
from typing import TypedDict, Optional, List

from langgraph.graph import StateGraph, END
from openai import AsyncOpenAI

from app.config import settings
from app.models.analysis import (
    PhotoAnalysisResponse, EditSuggestion, LessonRecommendation, FeedbackItem
)

logger = logging.getLogger(__name__)

# ── Shared OpenAI client — reused across all requests ─────────────────────────
_openai_client: AsyncOpenAI | None = None

def get_openai_client() -> AsyncOpenAI:
    global _openai_client
    if _openai_client is None:
        _openai_client = AsyncOpenAI(
            api_key=settings.openai_api_key,
            timeout=25.0,
            max_retries=1,
        )
    return _openai_client


# ── Agent State ───────────────────────────────────────────────────────────────
class PhotoCoachState(TypedDict):
    analysis_id: str
    user_id: str
    image_url: str
    skill_level: str
    vision_metadata: Optional[dict]
    composition_score: Optional[float]
    feedback_items: List[dict]
    edit_suggestions: Optional[dict]
    lesson_recommendations: List[dict]
    error: Optional[str]


# ── Azure Clients (async) ─────────────────────────────────────────────────────
def get_openai_client() -> AsyncOpenAI:
    return AsyncOpenAI(api_key=settings.openai_api_key)

def get_search_client() -> SearchClient:
    return SearchClient(
        endpoint=settings.azure_search_endpoint,
        index_name=settings.lessons_index,
        credential=AzureKeyCredential(settings.azure_search_key),
    )


# ── Agent Nodes ────────────────────────────────────────────────────────────────
async def run_vision_and_scoring(state: PhotoCoachState) -> PhotoCoachState:
    """
    Combined Node 1+2: GPT-4o Vision — extract metadata AND score composition.
    Eliminates the separate Azure Vision call.
    """
    logger.info(f"[{state['analysis_id']}] Running GPT-4o vision and scoring")
    client = get_openai_client()

    system_prompt = f"""You are an expert photography coach analysing a photo.
Return ONLY valid JSON with this exact structure:
{{
  "composition_score": <int 0-100>,
  "lighting_score": <int 0-100>,
  "color_score": <int 0-100>,
  "overall_score": <int 0-100>,
  "dominant_colors": ["<color>"],
  "objects": ["<object>"],
  "tags": ["<tag>"],
  "caption": "<one sentence description>",
  "strengths": [{{"text": "<observation>", "category": "composition|lighting|color|focus"}}],
  "improvements": [{{"text": "<improvement>", "category": "composition|lighting|color|focus"}}],
  "one_sentence_tip": "<tip for {state['skill_level']} photographer>",
  "style_tags": ["<genre>"]
}}
Be specific and technical. Adapt complexity to {state['skill_level']} level."""

    try:
        response = await client.chat.completions.create(
            model="gpt-4o",
            messages=[
                {"role": "system", "content": system_prompt},
                {"role": "user", "content": [
                    {"type": "image_url",
                     "image_url": {"url": state["image_url"], "detail": "high"}},
                    {"type": "text", "text": "Analyse this photo."},
                ]},
            ],
            response_format={"type": "json_object"},
            max_tokens=1000,
            temperature=0.3,
        )

        analysis = json.loads(response.choices[0].message.content)
        vision_metadata = {
            "dominant_colors": analysis.get("dominant_colors", []),
            "objects": analysis.get("objects", []),
            "tags": analysis.get("tags", []),
            "caption": analysis.get("caption", ""),
        }
        feedback_items = [
            {"text": s["text"], "type": "strength", "category": s["category"]}
            for s in analysis.get("strengths", [])
        ] + [
            {"text": i["text"], "type": "improvement", "category": i["category"]}
            for i in analysis.get("improvements", [])
        ]

        return {
            **state,
            "vision_metadata": vision_metadata,
            "composition_score": analysis.get("overall_score", 70),
            "feedback_items": feedback_items,
        }
    except Exception as e:
        logger.error(f"[{state['analysis_id']}] Vision and scoring failed: {e}")
        return {**state, "vision_metadata": {}, "composition_score": 0,
                "feedback_items": [], "error": str(e)}

async def generate_edit_suggestions(state: PhotoCoachState) -> PhotoCoachState:
    """
    Node 2: GPT-4o-mini — generate Lightroom-style editing parameters.
    Uses mini model — edit suggestions are formulaic, don't need GPT-4o quality.
    """
    logger.info(f"[{state['analysis_id']}] Generating edit suggestions")
    client = get_openai_client()

    prompt = f"""Based on this photo analysis, provide specific Lightroom editing parameters as JSON:
{{
  "exposure": <float -3.0 to +3.0>,
  "contrast": <int -100 to +100>,
  "highlights": <int -100 to +100>,
  "shadows": <int -100 to +100>,
  "whites": <int -100 to +100>,
  "blacks": <int -100 to +100>,
  "clarity": <int -100 to +100>,
  "vibrance": <int -100 to +100>,
  "saturation": <int -100 to +100>,
  "color_grade": "<warm|cool|neutral>",
  "crop_suggestion": "<straighten|rule_of_thirds_reframe|none>",
  "estimated_improvement": "<brief description>"
}}

Photo context: {json.dumps(state.get('vision_metadata', {}))}
Feedback: {json.dumps(state.get('feedback_items', [])[:3])}
Return ONLY valid JSON."""

    try:
        response = await client.chat.completions.create(
            model="gpt-4o-mini",       # edit params are deterministic — mini is fine
            messages=[{"role": "user", "content": prompt}],
            response_format={"type": "json_object"},
            max_tokens=400,
            temperature=0.2,
        )
        edits = json.loads(response.choices[0].message.content)
        return {**state, "edit_suggestions": edits}
    except Exception as e:
        logger.error(f"[{state['analysis_id']}] Edit suggestion failed: {e}")
        return {**state, "edit_suggestions": {}}


async def embed_and_recommend(state: PhotoCoachState) -> PhotoCoachState:
    """
    Node 3 (combined): Embed feedback text and search Supabase for lesson recs.
    Replaces the old separate embed + Azure Search nodes.
    Uses Supabase full-text search — no Azure dependency, much faster.
    """
    logger.info(f"[{state['analysis_id']}] Embedding and finding lesson recommendations")
    client = get_openai_client()

    feedback_items = state.get("feedback_items", [])
    if not feedback_items:
        return {**state, "lesson_recommendations": []}

    # Build a search query from the top feedback categories
    categories = list({f.get("category", "") for f in feedback_items[:4] if f.get("category")})
    query_text = " ".join([f["text"] for f in feedback_items[:3]])

    try:
        # Use Supabase full-text search — already set up with tsvector trigger
        from app.utils.supabase_client import get_supabase_client
        supabase = get_supabase_client()

        # Search for lessons matching the weakness categories
        search_term = " | ".join(categories) if categories else query_text[:50]
        result = supabase.table("lessons") \
            .select("id, title, description, duration_minutes, skill_level, thumbnail_url") \
            .text_search("search_vector", search_term, config="english") \
            .limit(3) \
            .execute()

        lessons = [
            {
                "id": row["id"],
                "title": row["title"],
                "description": row["description"],
                "duration_minutes": row.get("duration_minutes", 10),
                "skill_level": row.get("skill_level", "intermediate"),
                "thumbnail_url": row.get("thumbnail_url", ""),
                "relevance_score": 1.0,
            }
            for row in (result.data or [])
        ]
        return {**state, "lesson_recommendations": lessons}
    except Exception as e:
        logger.error(f"[{state['analysis_id']}] Lesson recommendation failed: {e}")
        return {**state, "lesson_recommendations": []}


async def run_parallel_analysis(state: PhotoCoachState) -> PhotoCoachState:
    """
    Node 2+3 combined: runs edit suggestions and lesson recommendations
    in parallel after vision completes — saves ~3-4 seconds vs sequential.
    """
    edit_task = generate_edit_suggestions(state)
    recommend_task = embed_and_recommend(state)

    edit_result, recommend_result = await asyncio.gather(
        edit_task, recommend_task, return_exceptions=True
    )

    # Merge results safely even if one task failed
    merged = {**state}
    if isinstance(edit_result, dict):
        merged["edit_suggestions"] = edit_result.get("edit_suggestions", {})
    else:
        logger.error(f"[{state['analysis_id']}] Edit task failed: {edit_result}")
        merged["edit_suggestions"] = {}

    if isinstance(recommend_result, dict):
        merged["lesson_recommendations"] = recommend_result.get("lesson_recommendations", [])
    else:
        logger.error(f"[{state['analysis_id']}] Recommend task failed: {recommend_result}")
        merged["lesson_recommendations"] = []

    return merged


# ── Build the LangGraph ────────────────────────────────────────────────────────

def build_photo_coach_graph():
    graph = StateGraph(PhotoCoachState)

    graph.add_node("vision_and_scoring", run_vision_and_scoring)
    graph.add_node("parallel_analysis",  run_parallel_analysis)  # edits + recommendations in parallel

    graph.set_entry_point("vision_and_scoring")
    graph.add_edge("vision_and_scoring", "parallel_analysis")
    graph.add_edge("parallel_analysis",  END)

    return graph.compile()


# ── Agent Class ───────────────────────────────────────────────────────────────

class PhotoCoachAgent:
    def __init__(self):
        self.graph = build_photo_coach_graph()

    async def run(
        self,
        image_url: str,
        skill_level: str,
        user_id: str,
        analysis_id: str,
    ) -> PhotoAnalysisResponse:

        initial_state: PhotoCoachState = {
            "analysis_id": analysis_id,
            "user_id": user_id,
            "image_url": image_url,
            "skill_level": skill_level,
            "vision_metadata": None,
            "composition_score": None,
            "feedback_items": [],
            "edit_suggestions": None,
            "lesson_recommendations": [],
            "error": None,
        }

        final_state = await self.graph.ainvoke(initial_state)

        return PhotoAnalysisResponse(
            analysis_id=analysis_id,
            composition_score=final_state["composition_score"] or 0,
            feedback=[
                FeedbackItem(**f) for f in final_state["feedback_items"]
            ],
            edit_suggestions=EditSuggestion(**final_state["edit_suggestions"])
            if final_state["edit_suggestions"] else None,
            lesson_recommendations=[
                LessonRecommendation(**l) for l in final_state["lesson_recommendations"]
            ],
            vision_tags=final_state.get("vision_metadata", {}).get("tags", []),
        )
