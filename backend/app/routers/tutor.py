###############################################################################
# routers/tutor.py — Streaming AI tutor
###############################################################################

import json
import logging
import uuid
from typing import AsyncGenerator

from fastapi import APIRouter, Depends
from fastapi.responses import StreamingResponse
from openai import AsyncOpenAI
from pydantic import BaseModel

from app.config import settings
from app.utils.supabase_client import SupabaseService
from app.middleware.auth import get_current_user

router = APIRouter()
logger = logging.getLogger(__name__)

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
    client = AsyncOpenAI(api_key=settings.openai_api_key)
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


# ── Endpoints ─────────────────────────────────────────────────────────────────

@router.post("/chat")
async def tutor_chat(
    request: ChatRequest,
    current_user: dict = Depends(get_current_user),
):
    """Send a message to the AI tutor. Returns a streaming SSE response."""
    service = SupabaseService()
    user_id = current_user["sub"]

    skill_profile = await service.get_skill_profile(user_id)

    await service.append_chat_message(user_id, {
        "role": "user",
        "content": request.message,
    })

    history = await service.get_chat_history(user_id, limit=10)

    return StreamingResponse(
        stream_tutor_response(history, skill_profile),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "X-Accel-Buffering": "no",
        },
    )


@router.post("/quiz")
async def generate_quiz(
    request: QuizRequest,
    current_user: dict = Depends(get_current_user),
):
    """Generate a multiple-choice photography quiz."""
    client = AsyncOpenAI(api_key=settings.openai_api_key)
    service = SupabaseService()
    skill_profile = await service.get_skill_profile(current_user["sub"])

    response = await client.chat.completions.create(
        model="gpt-4o",
        messages=[{
            "role": "user",
            "content": f"""Generate a photography quiz about: {request.topic}
Difficulty: {request.difficulty}
Number of questions: {request.num_questions}
Skill level: {skill_profile.get('level', 'intermediate')}

Return ONLY valid JSON with this exact structure:
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
Return ONLY JSON, no other text."""
        }],
        response_format={"type": "json_object"},
        max_tokens=1500,
    )

    data = json.loads(response.choices[0].message.content)

    return {
        "quiz_id": str(uuid.uuid4()),
        "topic": request.topic,
        "questions": data.get("questions", [])
    }
