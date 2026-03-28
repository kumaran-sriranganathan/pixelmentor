"""
backend/app/routers/lessons.py
-------------------------------
Replaces the hardcoded lessons list with real Azure AI Search queries.
"""

from __future__ import annotations

import logging
from typing import Optional

from azure.core.credentials import AzureKeyCredential
from azure.core.exceptions import HttpResponseError
from azure.search.documents import SearchClient
from azure.search.documents.models import QueryType
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
# Search client dependency
# ---------------------------------------------------------------------------

def get_search_client() -> SearchClient:
    return SearchClient(
        endpoint=settings.azure_search_endpoint,
        index_name=settings.search_index_name,
        credential=AzureKeyCredential(settings.azure_search_key),
    )


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

SELECT_SUMMARY_FIELDS = (
    "id,title,description,category,difficulty,duration_minutes,is_pro,order,tags"
)
SELECT_DETAIL_FIELDS = SELECT_SUMMARY_FIELDS + ",content"


def _build_filter(category: Optional[str], difficulty: Optional[str]) -> Optional[str]:
    parts: list[str] = []
    if category:
        safe = category.replace("'", "''")
        parts.append(f"category eq '{safe}'")
    if difficulty:
        safe = difficulty.replace("'", "''")
        parts.append(f"difficulty eq '{safe}'")
    return " and ".join(parts) if parts else None


def _doc_to_summary(doc: dict) -> LessonSummary:
    return LessonSummary(
        id=doc["id"],
        title=doc["title"],
        description=doc["description"],
        category=doc["category"],
        difficulty=doc["difficulty"],
        duration_minutes=doc["duration_minutes"],
        is_pro=doc.get("is_pro", False),
        order=doc.get("order", 0),
        tags=doc.get("tags") or [],
    )


def _doc_to_detail(doc: dict) -> LessonDetail:
    return LessonDetail(**_doc_to_summary(doc).model_dump(), content=doc.get("content", ""))


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
    search_client: SearchClient = Depends(get_search_client),
) -> LessonsResponse:
    skip = (page - 1) * page_size
    filter_expr = _build_filter(category, difficulty)

    try:
        results = search_client.search(
            search_text=q or "*",
            query_type=QueryType.SIMPLE,
            select=SELECT_SUMMARY_FIELDS,
            filter=filter_expr,
            order_by=[] if q else ["order asc"],
            skip=skip,
            top=page_size,
            include_total_count=True,
        )
        lessons = [_doc_to_summary(doc) for doc in results]
        total = results.get_count() or 0

    except HttpResponseError as exc:
        logger.error("Azure AI Search error: %s", exc.message)
        raise HTTPException(status_code=502, detail="Search service unavailable") from exc

    return LessonsResponse(
        lessons=lessons,
        total_count=total,
        page=page,
        page_size=page_size,
    )


@router.get("/{lesson_id}", response_model=LessonDetail)
async def get_lesson(
    lesson_id: str,
    current_user: dict = Depends(get_current_user),
    search_client: SearchClient = Depends(get_search_client),
) -> LessonDetail:
    try:
        doc = search_client.get_document(
            key=lesson_id,
            selected_fields=SELECT_DETAIL_FIELDS.split(","),
        )
    except HttpResponseError as exc:
        if exc.status_code == 404:
            raise HTTPException(status_code=404, detail="Lesson not found")
        logger.error("Azure AI Search error: %s", exc.message)
        raise HTTPException(status_code=502, detail="Search service unavailable") from exc

    return _doc_to_detail(doc)
