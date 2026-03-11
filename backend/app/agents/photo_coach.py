###############################################################################
# agents/photo_coach.py
# LangGraph agentic pipeline for photo analysis
###############################################################################

import json
import logging
from typing import TypedDict, Optional, List

from langgraph.graph import StateGraph, END
from openai import AsyncAzureOpenAI
from azure.ai.vision.imageanalysis.aio import ImageAnalysisClient
from azure.ai.vision.imageanalysis.models import VisualFeatures
from azure.core.credentials import AzureKeyCredential
from azure.search.documents.aio import SearchClient
from azure.search.documents.models import VectorizedQuery

from app.config import settings
from app.models.analysis import (
    PhotoAnalysisResponse, EditSuggestion, LessonRecommendation, FeedbackItem
)

logger = logging.getLogger(__name__)


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
    embedding: Optional[List[float]]
    lesson_recommendations: List[dict]
    error: Optional[str]


# ── Azure Clients (async) ─────────────────────────────────────────────────────
def get_openai_client() -> AsyncAzureOpenAI:
    return AsyncAzureOpenAI(
        azure_endpoint=settings.azure_openai_endpoint,
        api_key=settings.azure_openai_api_key,
        api_version=settings.azure_openai_api_version,
    )

def get_vision_client() -> ImageAnalysisClient:
    return ImageAnalysisClient(
        endpoint=settings.azure_vision_endpoint,
        credential=AzureKeyCredential(settings.azure_vision_key),
    )

def get_search_client() -> SearchClient:
    return SearchClient(
        endpoint=settings.azure_search_endpoint,
        index_name=settings.lessons_index,
        credential=AzureKeyCredential(settings.azure_search_key),
    )


# ── Agent Nodes ────────────────────────────────────────────────────────────────

async def run_vision_analysis(state: PhotoCoachState) -> PhotoCoachState:
    """
    Node 1: Azure AI Vision — extract technical metadata.
    (Objects, colors, captions, tags)
    """
    logger.info(f"[{state['analysis_id']}] Running Azure AI Vision")
    try:
        async with get_vision_client() as client:
            result = await client.analyze(
                image_url=state["image_url"],
                visual_features=[
                    VisualFeatures.OBJECTS,
                    VisualFeatures.TAGS,
                    VisualFeatures.DENSE_CAPTIONS,
                    VisualFeatures.COLOR,
                    VisualFeatures.SMART_CROPS,
                ],
            )
        metadata = {
            "dominant_colors": [c.name for c in (result.color.dominant_colors or [])],
            "accent_color": result.color.accent_color if result.color else None,
            "objects": [o.name for o in (result.objects.list or [])],
            "tags": [t.name for t in (result.tags.list or []) if t.confidence > 0.7],
            "caption": result.dense_captions.list[0].text if result.dense_captions else "",
            "smart_crops": [
                {"aspect_ratio": c.aspect_ratio, "bounding_box": str(c.bounding_box)}
                for c in (result.smart_crops.list or [])
            ],
        }
        return {**state, "vision_metadata": metadata}
    except Exception as e:
        logger.error(f"[{state['analysis_id']}] Vision analysis failed: {e}")
        return {**state, "vision_metadata": {}, "error": str(e)}


async def run_composition_scoring(state: PhotoCoachState) -> PhotoCoachState:
    """
    Node 2: GPT-4o Vision — score composition, lighting, colour.
    Returns structured JSON: score + feedback items.
    """
    logger.info(f"[{state['analysis_id']}] Running GPT-4o composition scoring")
    client = get_openai_client()

    system_prompt = f"""You are an expert photography coach with 20 years of experience
teaching {state['skill_level']}-level photographers.

Analyse the provided photo and return ONLY valid JSON with this exact structure:
{{
  "composition_score": <integer 0-100>,
  "lighting_score": <integer 0-100>,
  "color_score": <integer 0-100>,
  "overall_score": <integer 0-100>,
  "strengths": [
    {{"text": "<specific strength observation>", "category": "composition|lighting|color|focus"}}
  ],
  "improvements": [
    {{"text": "<specific, actionable improvement>", "category": "composition|lighting|color|focus"}}
  ],
  "one_sentence_tip": "<single most impactful tip for a {state['skill_level']} photographer>",
  "style_tags": ["<genre tag>"]
}}

Be specific and technical. Reference actual photographic techniques (rule of thirds,
leading lines, golden ratio, Rembrandt lighting, etc.). Adapt complexity to {state['skill_level']} level.
Additional context from AI Vision: {json.dumps(state.get('vision_metadata', {}))}"""

    try:
        response = await client.chat.completions.create(
            model=settings.gpt4o_deployment,
            messages=[
                {"role": "system", "content": system_prompt},
                {
                    "role": "user",
                    "content": [
                        {
                            "type": "image_url",
                            "image_url": {"url": state["image_url"], "detail": "high"},
                        },
                        {"type": "text", "text": "Please analyse this photo."},
                    ],
                },
            ],
            response_format={"type": "json_object"},
            max_tokens=1000,
            temperature=0.3,   # Low temperature for consistent scoring
        )

        analysis = json.loads(response.choices[0].message.content)
        feedback_items = [
            {"text": s["text"], "type": "strength", "category": s["category"]}
            for s in analysis.get("strengths", [])
        ] + [
            {"text": i["text"], "type": "improvement", "category": i["category"]}
            for i in analysis.get("improvements", [])
        ]

        return {
            **state,
            "composition_score": analysis.get("overall_score", 70),
            "feedback_items": feedback_items,
        }
    except Exception as e:
        logger.error(f"[{state['analysis_id']}] Composition scoring failed: {e}")
        return {**state, "composition_score": 0, "feedback_items": [], "error": str(e)}


async def generate_edit_suggestions(state: PhotoCoachState) -> PhotoCoachState:
    """
    Node 3: GPT-4o — generate Lightroom-style editing parameters.
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
  "estimated_improvement": "<brief description of how these edits improve the photo>"
}}

Photo context: {json.dumps(state.get('vision_metadata', {}))}
Feedback: {json.dumps(state.get('feedback_items', [])[:3])}
Return ONLY valid JSON."""

    try:
        response = await client.chat.completions.create(
            model=settings.gpt4o_deployment,
            messages=[
                {
                    "role": "user",
                    "content": [
                        {"type": "image_url", "image_url": {"url": state["image_url"]}},
                        {"type": "text", "text": prompt},
                    ],
                }
            ],
            response_format={"type": "json_object"},
            max_tokens=500,
            temperature=0.2,
        )
        edits = json.loads(response.choices[0].message.content)
        return {**state, "edit_suggestions": edits}
    except Exception as e:
        logger.error(f"[{state['analysis_id']}] Edit suggestion failed: {e}")
        return {**state, "edit_suggestions": {}, "error": str(e)}


async def embed_feedback(state: PhotoCoachState) -> PhotoCoachState:
    """
    Node 4: Embed the feedback text for RAG lesson search.
    """
    client = get_openai_client()
    query_text = " ".join([f["text"] for f in state["feedback_items"][:3]])

    try:
        response = await client.embeddings.create(
            model=settings.embedding_deployment,
            input=query_text,
        )
        return {**state, "embedding": response.data[0].embedding}
    except Exception as e:
        logger.error(f"[{state['analysis_id']}] Embedding failed: {e}")
        return {**state, "embedding": None}


async def recommend_lessons(state: PhotoCoachState) -> PhotoCoachState:
    """
    Node 5: Vector search Azure AI Search for relevant lesson recommendations.
    """
    logger.info(f"[{state['analysis_id']}] Searching lesson recommendations")

    if not state.get("embedding"):
        return {**state, "lesson_recommendations": []}

    try:
        async with get_search_client() as client:
            results = await client.search(
                search_text=None,
                vector_queries=[
                    VectorizedQuery(
                        vector=state["embedding"],
                        k_nearest_neighbors=3,
                        fields="content_vector",
                    )
                ],
                select=["id", "title", "description", "duration_minutes", "skill_level", "thumbnail_url"],
                top=3,
            )
            lessons = []
            async for result in results:
                lessons.append({
                    "id": result["id"],
                    "title": result["title"],
                    "description": result["description"],
                    "duration_minutes": result.get("duration_minutes", 10),
                    "skill_level": result.get("skill_level", "intermediate"),
                    "thumbnail_url": result.get("thumbnail_url", ""),
                    "relevance_score": result["@search.score"],
                })
        return {**state, "lesson_recommendations": lessons}
    except Exception as e:
        logger.error(f"[{state['analysis_id']}] Lesson search failed: {e}")
        return {**state, "lesson_recommendations": []}


# ── Build the LangGraph ────────────────────────────────────────────────────────

def build_photo_coach_graph():
    graph = StateGraph(PhotoCoachState)

    graph.add_node("vision_analysis",     run_vision_analysis)
    graph.add_node("composition_scoring", run_composition_scoring)
    graph.add_node("edit_suggestions",    generate_edit_suggestions)
    graph.add_node("embed_feedback",      embed_feedback)
    graph.add_node("recommend_lessons",   recommend_lessons)

    # Sequential pipeline — each node feeds into the next
    graph.set_entry_point("vision_analysis")
    graph.add_edge("vision_analysis",     "composition_scoring")
    graph.add_edge("composition_scoring", "edit_suggestions")
    graph.add_edge("edit_suggestions",    "embed_feedback")
    graph.add_edge("embed_feedback",      "recommend_lessons")
    graph.add_edge("recommend_lessons",   END)

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
            "embedding": None,
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
