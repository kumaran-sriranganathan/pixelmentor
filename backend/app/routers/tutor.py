###############################################################################
# routers/tutor.py — Streaming AI tutor + quiz with cache
###############################################################################

import asyncio
import json
import logging
import random
import uuid
from datetime import datetime, timezone, timedelta
from typing import AsyncGenerator

from fastapi import APIRouter, Depends, BackgroundTasks, HTTPException
from fastapi.responses import StreamingResponse
from openai import AsyncOpenAI
from pydantic import BaseModel

from app.config import settings
from app.utils.supabase_client import SupabaseService
from app.middleware.auth import get_current_user

router = APIRouter()
logger = logging.getLogger(__name__)

# ── Shared OpenAI client — connection pool reused across requests ──────────────
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

# ── Cache config ──────────────────────────────────────────────────────────────
QUIZ_POOL_SIZE   = 10   # questions generated and stored per topic+difficulty
QUIZ_SERVE_SIZE  = 5    # questions sampled from pool per request
CACHE_TTL_DAYS   = 7    # regenerate pool after this many days
CACHE_WARM_DAYS  = 5    # trigger background refresh after this many days

TUTOR_SYSTEM_PROMPT = """You are PhotoCoach AI, an expert photography tutor.

Your teaching philosophy:
- Adapt explanation complexity to the student's skill level
- Use concrete, visual examples they can try immediately
- Reference real camera settings (f-stop, ISO, shutter speed)
- End every response with one actionable "Try this now" challenge

Student profile: {skill_profile}

Keep responses concise and encouraging."""


# ── Request models ────────────────────────────────────────────────────────────

class ChatRequest(BaseModel):
    message: str
    topic: str | None = None
    session_id: str | None = None


class QuizRequest(BaseModel):
    topic: str
    difficulty: str = "intermediate"
    num_questions: int = 5


# ── Streaming helper ──────────────────────────────────────────────────────────

async def stream_tutor_response(
    messages: list,
    skill_profile: dict,
) -> AsyncGenerator[str, None]:
    """Stream GPT-4o tutor response as Server-Sent Events."""
    client = get_openai_client()
    system = TUTOR_SYSTEM_PROMPT.format(skill_profile=json.dumps(skill_profile))

    try:
        stream = await client.chat.completions.create(
            model="gpt-4o",
            messages=[{"role": "system", "content": system}] + messages,
            max_tokens=800,
            temperature=0.7,
            stream=True,
        )
        async for chunk in stream:
            if chunk.choices and chunk.choices[0].delta.content:
                delta = chunk.choices[0].delta.content
                yield f"data: {json.dumps({'content': delta})}\n\n"
        yield "data: [DONE]\n\n"
    except Exception as e:
        logger.error(f"Streaming failed: {e}")
        yield f"data: {json.dumps({'error': 'Tutor unavailable'})}\n\n"
        yield "data: [DONE]\n\n"


# ── Quiz cache helpers ────────────────────────────────────────────────────────

async def _generate_question_pool(
    topic: str,
    difficulty: str,
    skill_level: str,
    pool_size: int = QUIZ_POOL_SIZE,
) -> list[dict]:
    """
    Generate a pool of questions using gpt-4o-mini.
    Single API call regardless of pool size — fast and cheap.
    """
    client = AsyncOpenAI(
        api_key=settings.openai_api_key,
        timeout=60.0,   # ← 40 questions needs more time than default 25s
        max_retries=0,  # ← don't retry, just fail fast if it times out
    )
    logger.info(f"Generating {pool_size}-question pool for topic='{topic}' difficulty='{difficulty}'")

    response = await client.chat.completions.create(
        model="gpt-4o-mini",
        messages=[{
            "role": "user",
            "content": f"""Generate exactly {pool_size} unique photography quiz questions about: {topic}
Difficulty: {difficulty}
Skill level: {skill_level}

Rules:
- Every question must be distinct — no repeats or near-duplicates
- Vary the question style (what, why, how, which setting, scenario-based)
- All 4 options must be plausible — avoid obviously wrong answers
- Explanations should teach, not just state the answer

Return ONLY valid JSON:
{{
  "questions": [
    {{
      "id": "q1",
      "question": "...",
      "options": ["option A", "option B", "option C", "option D"],
      "correct_index": 0,
      "explanation": "..."
    }}
  ]
}}

correct_index is 0-based (0=A, 1=B, 2=C, 3=D).
Return ONLY the JSON object, no other text."""
        }],
        response_format={"type": "json_object"},
        max_tokens=6000,   # 40 questions × ~150 tokens each
        temperature=0.85,  # slightly higher for more variety in the pool
        timeout=55.0,
    )

    data = json.loads(response.choices[0].message.content)
    questions = data.get("questions", [])
    logger.info(f"Generated {len(questions)} questions for pool")
    return questions


async def _get_cached_pool(topic: str, difficulty: str) -> dict | None:
    try:
        from app.utils.supabase_client import get_supabase_admin
        supabase = get_supabase_admin()
        result = supabase.table("quiz_cache") \
            .select("questions, refreshed_at, hit_count") \
            .eq("topic", topic) \
            .eq("difficulty", difficulty) \
            .maybe_single() \
            .execute()

        if not result.data:
            return None
        # ... rest unchanged

        refreshed_at = datetime.fromisoformat(
            result.data["refreshed_at"].replace("Z", "+00:00")
        )
        age_days = (datetime.now(timezone.utc) - refreshed_at).days

        if age_days >= CACHE_TTL_DAYS:
            logger.info(f"Cache expired for topic='{topic}' difficulty='{difficulty}' (age={age_days}d)")
            return None

        result.data["age_days"] = age_days
        return result.data

    except Exception as e:
        logger.warning(f"Cache lookup failed: {e}")
        return None


async def _store_pool(topic: str, difficulty: str, questions: list[dict]) -> None:
    """Upsert the question pool into the cache table."""
    try:
        from app.utils.supabase_client import get_supabase_admin
        supabase = get_supabase_admin()
        supabase.table("quiz_cache").upsert({
            "topic": topic,
            "difficulty": difficulty,
            "questions": questions,
            "refreshed_at": datetime.now(timezone.utc).isoformat(),
            "hit_count": 0,
        }, on_conflict="topic,difficulty").execute()
        logger.info(f"Stored {len(questions)}-question pool for topic='{topic}' difficulty='{difficulty}'")
    except Exception as e:
        logger.error(f"Failed to store quiz cache: {e}")


async def _increment_hits(topic: str, difficulty: str) -> None:
    """Fire-and-forget hit counter increment."""
    try:
        from app.utils.supabase_client import get_supabase_admin
        supabase = get_supabase_admin()
        supabase.rpc("increment_quiz_cache_hits", {
            "p_topic": topic,
            "p_difficulty": difficulty,
        }).execute()
    except Exception:
        pass  # non-fatal


async def _background_refresh(topic: str, difficulty: str, skill_level: str) -> None:
    """
    Regenerate the question pool in the background.
    Called when the cache is between CACHE_WARM_DAYS and CACHE_TTL_DAYS old
    so the cache is always warm before it expires.
    """
    logger.info(f"Background refresh for topic='{topic}' difficulty='{difficulty}'")
    try:
        questions = await _generate_question_pool(topic, difficulty, skill_level)
        if questions:
            await _store_pool(topic, difficulty, questions)
    except Exception as e:
        logger.error(f"Background refresh failed: {e}")


def _sample_questions(pool: list[dict], n: int) -> list[dict]:
    """
    Sample n questions from the pool and re-index their IDs.
    C(40,5) = 658,008 combinations — users will never see the same quiz twice.
    """
    sampled = random.sample(pool, min(n, len(pool)))
    # Re-index so IDs are sequential (q1, q2, ...) regardless of pool position
    for i, q in enumerate(sampled, 1):
        q["id"] = f"q{i}"
    return sampled


# ── Endpoints ─────────────────────────────────────────────────────────────────

@router.post("/chat")
async def tutor_chat(
    request: ChatRequest,
    current_user: dict = Depends(get_current_user),
):
    service = SupabaseService()
    user_id = current_user["sub"]

    skill_profile = await service.get_skill_profile(user_id)

    # ── Fetch history BEFORE saving the new user message ─────────────────────
    # If we save first then fetch, the current message appears twice in the
    # context sent to GPT-4o (once in history, once appended below).
    history = await service.get_chat_history(user_id, limit=10, session_id=request.session_id)

    # Save user message AFTER fetching history
    await service.append_chat_message(user_id, {
        "role": "user",
        "content": request.message,
    }, session_id=request.session_id)

    # Manually append current message to history for this request
    history.append({"role": "user", "content": request.message})

    async def stream_and_save():
        full_response = []
        async for chunk in stream_tutor_response(history, skill_profile):
            yield chunk
            if chunk.startswith("data: ") and chunk.strip() != "data: [DONE]":
                try:
                    data = json.loads(chunk.removeprefix("data: ").strip())
                    if "content" in data:
                        full_response.append(data["content"])
                except Exception:
                    pass

        if full_response:
            try:
                # ── Pass session_id when saving assistant reply ────────────────
                # Without this, assistant messages get session_id=NULL and are
                # excluded from get_chat_history, breaking multi-turn context.
                await service.append_chat_message(user_id, {
                    "role": "assistant",
                    "content": "".join(full_response),
                }, session_id=request.session_id)
            except Exception as e:
                logger.error(f"Failed to save assistant message: {e}")

    return StreamingResponse(
        stream_and_save(),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "X-Accel-Buffering": "no",
        },
    )


@router.post("/quiz")
async def generate_quiz(
    request: QuizRequest,
    background_tasks: BackgroundTasks,
    current_user: dict = Depends(get_current_user),
):
    """
    Returns a quiz sampled from a cached 40-question pool.

    Flow:
      Cache hit (fresh)    → sample 5 from 40, return in <100ms
      Cache hit (warming)  → sample 5, trigger background refresh
      Cache miss / expired → generate 40, store, sample 5, return (~4s first time)

    C(40,5) = 658,008 combinations — users never see the same quiz twice.
    """
    service = SupabaseService()
    user_id = current_user["sub"]

    # ── Input sanitisation (Fix 4) ────────────────────────────────────────────
    # Cap topic length and strip control characters to prevent prompt injection
    topic = request.topic.strip()[:100]
    topic = "".join(c for c in topic if c.isprintable())
    if not topic:
        raise HTTPException(status_code=400, detail="Topic is required")

    # Whitelist difficulty — reject anything unexpected
    VALID_DIFFICULTIES = {"beginner", "intermediate", "advanced"}
    difficulty = request.difficulty.strip().lower()
    if difficulty not in VALID_DIFFICULTIES:
        difficulty = "intermediate"

    num_questions = min(max(request.num_questions, 1), QUIZ_POOL_SIZE)

    # ── Plan-based monthly quiz limit ─────────────────────────────────────────
    plan = await service.get_user_plan(user_id)
    quiz_limit = settings.get_quiz_limit(plan)
    quiz_attempts = await service.get_quiz_attempts_this_month(user_id)

    if quiz_attempts >= quiz_limit:
        raise HTTPException(
            status_code=429,
            detail={
                "error": "quiz_limit_reached",
                "message": f"You've used all {quiz_limit} quiz attempts included in your {plan.capitalize()} plan this month.",
                "limit": quiz_limit,
                "used": quiz_attempts,
                "plan": plan,
                "upgrade_required": plan == "free",
            }
        )

    # Get skill level for prompt context
    skill_profile = await service.get_skill_profile(user_id)
    skill_level = skill_profile.get("level", "intermediate")

    # ── 1. Check cache ────────────────────────────────────────────────────────
    cached = await _get_cached_pool(topic, difficulty)

    if cached:
        pool = cached["questions"]
        age_days = cached["age_days"]
        logger.info(
            f"Cache hit for topic='{topic}' difficulty='{difficulty}' "
            f"(age={age_days}d, pool={len(pool)}, hits={cached['hit_count']})"
        )

        # Warm refresh: cache still valid but getting old — refresh in background
        if age_days >= CACHE_WARM_DAYS:
            logger.info(f"Scheduling background refresh (age={age_days}d >= warm threshold)")
            background_tasks.add_task(
                _background_refresh, topic, difficulty, skill_level
            )

        # Increment hit counter (fire and forget)
        background_tasks.add_task(_increment_hits, topic, difficulty)

        # Record quiz attempt for rate limiting
        background_tasks.add_task(service.record_quiz_attempt, user_id, topic)

        return {
            "quiz_id": str(uuid.uuid4()),
            "topic": request.topic,
            "questions": _sample_questions(pool, num_questions),
            "from_cache": True,
        }

    # ── 2. Cache miss — generate full pool ───────────────────────────────────
    logger.info(f"Cache miss for topic='{topic}' difficulty='{difficulty}' — generating pool")
    questions = await _generate_question_pool(topic, difficulty, skill_level)

    # Store in background so response isn't delayed by the DB write
    background_tasks.add_task(_store_pool, topic, difficulty, questions)

    # Record quiz attempt for rate limiting
    background_tasks.add_task(service.record_quiz_attempt, user_id, topic)

    return {
        "quiz_id": str(uuid.uuid4()),
        "topic": request.topic,
        "questions": _sample_questions(questions, num_questions),
        "from_cache": False,
    }
