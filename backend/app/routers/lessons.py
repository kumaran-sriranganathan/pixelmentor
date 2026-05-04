###############################################################################
# routers/lessons.py — Lesson search + AI content expansion
# Uses Supabase PostgreSQL for lesson storage and full-text search.
# Typesense dependency removed.
###############################################################################

import logging
from fastapi import APIRouter, Depends, HTTPException
from openai import AsyncOpenAI

from app.config import settings
from app.middleware.auth import get_current_user
from app.utils.supabase_client import get_supabase_client

router = APIRouter()
logger = logging.getLogger(__name__)


# ── Lesson list ───────────────────────────────────────────────────────────────

@router.get("")
async def get_lessons(
    q: str = None,
    difficulty: str = None,
    category: str = None,
    page: int = 1,
    page_size: int = 20,
    current_user: dict = Depends(get_current_user),
):
    try:
        supabase = get_supabase_client()

        query = supabase.table("lessons") \
            .select("id, title, description, category, difficulty, duration_minutes, is_pro, order, tags")

        # Full-text search
        if q:
            query = query.text_search("search_vector", q, config="english")

        # Filters
        if difficulty:
            query = query.eq("difficulty", difficulty)
        if category:
            query = query.eq("category", category)

        # Pagination and ordering
        offset = (page - 1) * page_size
        query = query.order("order").range(offset, offset + page_size - 1)

        response = query.execute()

        # Get total count
        count_query = supabase.table("lessons").select("id", count="exact")
        if q:
            count_query = count_query.text_search("search_vector", q, config="english")
        if difficulty:
            count_query = count_query.eq("difficulty", difficulty)
        if category:
            count_query = count_query.eq("category", category)
        count_response = count_query.execute()

        return {
            "lessons": response.data,
            "total_count": count_response.count or 0,
            "page": page,
            "page_size": page_size,
        }

    except Exception as e:
        logger.error(f"Failed to fetch lessons: {e}")
        raise HTTPException(status_code=502, detail="Failed to fetch lessons")


# ── Lesson detail ─────────────────────────────────────────────────────────────

@router.get("/{lesson_id}/content")
async def get_lesson_content(
    lesson_id: str,
    current_user: dict = Depends(get_current_user),
):
    """
    Returns AI-expanded lesson content.
    Checks cache first — only calls GPT-4o on first access per lesson.
    """
    supabase = get_supabase_client()

    # ── Check content cache ───────────────────────────────────────────────────
    try:
        cached = supabase.table("lesson_content_cache") \
            .select("content") \
            .eq("lesson_id", lesson_id) \
            .single() \
            .execute()
        if cached.data:
            logger.info(f"Returning cached content for lesson {lesson_id}")
            return {"lesson_id": lesson_id, "content": cached.data["content"]}
    except Exception:
        pass  # Cache miss — generate content

    # ── Fetch lesson from Supabase ────────────────────────────────────────────
    try:
        lesson_response = supabase.table("lessons") \
            .select("*") \
            .eq("id", lesson_id) \
            .single() \
            .execute()

        if not lesson_response.data:
            raise HTTPException(status_code=404, detail="Lesson not found")

        lesson = lesson_response.data

    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Failed to fetch lesson {lesson_id}: {e}")
        raise HTTPException(status_code=502, detail="Failed to fetch lesson")

    # ── Generate with GPT-4o ──────────────────────────────────────────────────
    openai_client = AsyncOpenAI(api_key=settings.openai_api_key)

    duration = lesson.get("duration_minutes", 15)
    difficulty = lesson.get("difficulty", "beginner")
    title = lesson.get("title", "")
    description = lesson.get("description", "")
    category = lesson.get("category", "")
    tags = lesson.get("tags", [])
    summary = lesson.get("content", description)

    if duration <= 15:
        depth = "3-4 sections, concise explanations, 1 exercise"
    elif duration <= 30:
        depth = "5-6 sections, detailed explanations with examples, 2 exercises"
    else:
        depth = "7-8 sections, comprehensive explanations, multiple examples with camera settings, 3 exercises, common mistakes section"

    prompt = f"""You are an expert photography educator creating a structured lesson for the PixelMentor app.

Lesson details:
- Title: {title}
- Category: {category}
- Difficulty: {difficulty}
- Duration: {duration} minutes
- Summary: {summary}
- Tags: {', '.join(tags)}

Create a complete, engaging lesson that would take approximately {duration} minutes to read and practice.
Depth required: {depth}

Format the lesson using this structure:
## [Section Title]
Content here...

Use these formatting conventions:
- ## for main section headers
- ### for sub-headers
- Start bullet points with "- "
- Start tips with "💡 "
- Include specific camera settings (f-stop, ISO, shutter speed) where relevant
- End with a "## Practice Exercise" section with a concrete hands-on task
- For advanced lessons, include a "## Common Mistakes" section

Adapt the language complexity to {difficulty} level photographers.
Be specific, practical and encouraging. Include real-world examples."""

    try:
        response = await openai_client.chat.completions.create(
            model="gpt-4o",
            messages=[{"role": "user", "content": prompt}],
            max_tokens=3000,
            temperature=0.7,
        )
        content = response.choices[0].message.content

        # ── Cache in Supabase ─────────────────────────────────────────────────
        try:
            supabase.table("lesson_content_cache").upsert({
                "lesson_id": lesson_id,
                "content": content,
            }).execute()
            logger.info(f"Cached generated content for lesson {lesson_id}")
        except Exception as e:
            logger.error(f"Failed to cache lesson content: {e}")

        return {"lesson_id": lesson_id, "content": content}

    except Exception as e:
        logger.error(f"GPT-4o generation failed for {lesson_id}: {e}")
        return {"lesson_id": lesson_id, "content": summary}


@router.get("/{lesson_id}")
async def get_lesson(
    lesson_id: str,
    current_user: dict = Depends(get_current_user),
):
    try:
        supabase = get_supabase_client()
        response = supabase.table("lessons") \
            .select("*") \
            .eq("id", lesson_id) \
            .single() \
            .execute()

        if not response.data:
            raise HTTPException(status_code=404, detail="Lesson not found")

        return response.data

    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Failed to fetch lesson {lesson_id}: {e}")
        raise HTTPException(status_code=502, detail="Failed to fetch lesson")
