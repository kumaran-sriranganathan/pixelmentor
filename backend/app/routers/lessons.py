###############################################################################
# routers/lessons.py — Lesson library endpoints (Typesense-backed)
# Replaces Azure AI Search — do not delete old version from git history
###############################################################################

from __future__ import annotations

import logging
from typing import Optional

import typesense
import typesense.exceptions
from fastapi import APIRouter, Depends, HTTPException, Query
from pydantic import BaseModel

from app.config import settings
from app.middleware.auth import get_current_user

logger = logging.getLogger(__name__)

router = APIRouter()


# ---------------------------------------------------------------------------
# Pydantic response models
# ---------------------------------------------------------------------------

class LessonSummary(BaseModel):
    id: str
    title: str
    description: str
    category: str
    difficulty: str
    duration_minutes: int
    is_pro: bool
    order: int
    tags: list[str]


class LessonDetail(LessonSummary):
    content: str


class LessonsResponse(BaseModel):
    lessons: list[LessonSummary]
    total_count: int
    page: int
    page_size: int


# ---------------------------------------------------------------------------
# Typesense client dependency
# ---------------------------------------------------------------------------

def get_typesense_client() -> typesense.Client:
    return typesense.Client({
        "nodes": [{"host": settings.typesense_host, "port": "443", "protocol": "https"}],
        "api_key": settings.typesense_api_key,
        "connection_timeout_seconds": 5,
    })


# ---------------------------------------------------------------------------
# Routes
# ---------------------------------------------------------------------------

@router.get("", response_model=LessonsResponse)
async def list_lessons(
    q: Optional[str] = Query(default=None),
    category: Optional[str] = Query(default=None),
    difficulty: Optional[str] = Query(default=None),
    page: int = Query(default=1, ge=1),
    page_size: int = Query(default=20, ge=1, le=100),
    current_user: dict = Depends(get_current_user),
    typesense_client: typesense.Client = Depends(get_typesense_client),
) -> LessonsResponse:
    filter_parts = []
    if category:
        filter_parts.append(f"category:={category}")
    if difficulty:
        filter_parts.append(f"difficulty:={difficulty}")

    params = {
        "q": q or "*",
        "query_by": "title,description,content",
        "sort_by": "order:asc" if not q else "_text_match:desc",
        "page": page,
        "per_page": page_size,
        "include_fields": "id,title,description,category,difficulty,duration_minutes,is_pro,order,tags",
    }
    if filter_parts:
        params["filter_by"] = " && ".join(filter_parts)

    try:
        result = typesense_client.collections["photography-lessons"].documents.search(params)
        lessons = [LessonSummary(**hit["document"]) for hit in result["hits"]]
        return LessonsResponse(
            lessons=lessons,
            total_count=result["found"],
            page=page,
            page_size=page_size,
        )
    except Exception as exc:
        logger.error("Typesense search error: %s", exc)
        raise HTTPException(status_code=502, detail="Search service unavailable") from exc


@router.get("/{lesson_id}", response_model=LessonDetail)
async def get_lesson(
    lesson_id: str,
    current_user: dict = Depends(get_current_user),
    typesense_client: typesense.Client = Depends(get_typesense_client),
) -> LessonDetail:
    try:
        doc = typesense_client.collections["photography-lessons"].documents[lesson_id].retrieve()
        return LessonDetail(**doc)
    except typesense.exceptions.ObjectNotFound:
        raise HTTPException(status_code=404, detail="Lesson not found")
    except Exception as exc:
        logger.error("Typesense error: %s", exc)
        raise HTTPException(status_code=502, detail="Search service unavailable") from exc