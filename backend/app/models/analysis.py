###############################################################################
# models/analysis.py — Pydantic models for photo analysis and tutor
###############################################################################

from typing import List, Optional
from pydantic import BaseModel, Field


class PhotoAnalysisRequest(BaseModel):
    user_id: str
    image_url: Optional[str] = None
    image_base64: Optional[str] = None


class FeedbackItem(BaseModel):
    text: str
    type: str = Field(pattern="^(strength|improvement)$")
    category: str = Field(pattern="^(composition|lighting|color|focus)$")


class EditSuggestion(BaseModel):
    exposure: float = Field(default=0.0, ge=-3.0, le=3.0)
    contrast: int = Field(default=0, ge=-100, le=100)
    highlights: int = Field(default=0, ge=-100, le=100)
    shadows: int = Field(default=0, ge=-100, le=100)
    whites: int = Field(default=0, ge=-100, le=100)
    blacks: int = Field(default=0, ge=-100, le=100)
    clarity: int = Field(default=0, ge=-100, le=100)
    vibrance: int = Field(default=0, ge=-100, le=100)
    saturation: int = Field(default=0, ge=-100, le=100)
    color_grade: str = "neutral"
    crop_suggestion: str = "none"
    estimated_improvement: str = ""


class LessonRecommendation(BaseModel):
    id: str
    title: str
    description: str
    duration_minutes: int
    skill_level: str
    thumbnail_url: str = ""
    relevance_score: float = 0.0


class PhotoAnalysisResponse(BaseModel):
    analysis_id: str
    composition_score: float = Field(ge=0, le=100)
    feedback: List[FeedbackItem]
    edit_suggestions: Optional[EditSuggestion]
    lesson_recommendations: List[LessonRecommendation]
    vision_tags: List[str] = []


class ChatRequest(BaseModel):
    message: str = Field(min_length=1, max_length=2000)
    quiz_mode: bool = False


class ChatResponse(BaseModel):
    response: str
    quiz_question: Optional[dict] = None
