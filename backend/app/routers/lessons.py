###############################################################################
# routers/lessons.py — Lesson search + AI content expansion
###############################################################################

import logging
import typesense
import typesense.exceptions
from fastapi import APIRouter, Depends, HTTPException
from openai import AsyncOpenAI

from app.config import settings
from app.middleware.auth import get_current_user
from app.utils.supabase_client import get_supabase_client

router = APIRouter()
logger = logging.getLogger(__name__)


# ── Typesense client (injectable for testing) ─────────────────────────────────

def get_typesense_client():
    return typesense.Client({
        "nodes": [{
            "host": settings.typesense_host,
            "port": "443",
            "protocol": "https",
        }],
        "api_key": settings.typesense_api_key,
        "connection_timeout_seconds": 10,
    })


# ── Lesson list ───────────────────────────────────────────────────────────────

@router.get("")
async def get_lessons(
    q: str = "*",
    difficulty: str = None,
    category: str = None,
    page: int = 1,
    page_size: int = 20,
    current_user: dict = Depends(get_current_user),
    typesense_client=Depends(get_typesense_client),
):
    search_params = {
        "q": q,
        "query_by": "title,description,tags",
        "page": page,
        "per_page": page_size,
        "sort_by": "order:asc",
    }

    filter_parts = []
    if difficulty:
        filter_parts.append(f"difficulty:={difficulty}")
    if category:
        filter_parts.append(f"category:={category}")
    if filter_parts:
        search_params["filter_by"] = " && ".join(filter_parts)

    try:
        results = typesense_client.collections["lessons"].documents.search(search_params)
        # Strip content field from list results — only return in detail view
        lessons = []
        for hit in results.get("hits", []):
            doc = {k: v for k, v in hit["document"].items() if k != "content"}
            lessons.append(doc)
        return {
            "lessons": lessons,
            "total_count": results.get("found", 0),
            "page": page,
            "page_size": page_size,
        }
    except Exception as e:
        logger.error(f"Typesense search failed: {e}")
        raise HTTPException(status_code=502, detail="Failed to fetch lessons")


# ── Lesson detail ─────────────────────────────────────────────────────────────

@router.get("/{lesson_id}/content")
async def get_lesson_content(
    lesson_id: str,
    current_user: dict = Depends(get_current_user),
    typesense_client=Depends(get_typesense_client),
):
    """
    Returns AI-expanded lesson content. Checks Supabase cache first.
    If not cached, generates with GPT-4o and caches the result.
    """
    supabase = get_supabase_client()

    # ── Check cache ───────────────────────────────────────────────────────────
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

    # ── Fetch lesson from Typesense ───────────────────────────────────────────
    try:
        lesson = typesense_client.collections["lessons"].documents[lesson_id].retrieve()
    except typesense.exceptions.ObjectNotFound:
        raise HTTPException(status_code=404, detail="Lesson not found")
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
        logger.error(f"GPT-4o content generation failed for {lesson_id}: {e}")
        return {"lesson_id": lesson_id, "content": summary}


@router.get("/{lesson_id}")
async def get_lesson(
    lesson_id: str,
    current_user: dict = Depends(get_current_user),
    typesense_client=Depends(get_typesense_client),
):
    try:
        doc = typesense_client.collections["lessons"].documents[lesson_id].retrieve()
        return doc
    except typesense.exceptions.ObjectNotFound:
        raise HTTPException(status_code=404, detail="Lesson not found")
    except Exception as e:
        logger.error(f"Failed to fetch lesson {lesson_id}: {e}")
        raise HTTPException(status_code=502, detail="Failed to fetch lesson")
