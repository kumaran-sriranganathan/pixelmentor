###############################################################################
# routers/tutor.py — Streaming AI tutor
###############################################################################

import json
import logging
from typing import AsyncGenerator

from fastapi import APIRouter, Depends
from fastapi.responses import StreamingResponse
from openai import AsyncAzureOpenAI

from app.config import settings
from app.models.analysis import ChatRequest, ChatResponse
from app.utils.supabase_client import SupabaseService as CosmosService
from app.middleware.auth import get_current_user

router = APIRouter()
logger = logging.getLogger(__name__)

TUTOR_SYSTEM_PROMPT = """You are PhotoCoach AI, an expert photography tutor.

Your teaching philosophy:
- Adapt explanation complexity to the student's skill level
- Use concrete, visual examples they can try immediately
- Reference real camera settings (f-stop, ISO, shutter speed)
- End every response with one actionable "Try this now" challenge
- In quiz mode: ask ONE focused question, then evaluate their answer

Student profile: {skill_profile}

Keep responses concise and encouraging."""


async def stream_tutor_response(
    messages: list,
    skill_profile: dict,
) -> AsyncGenerator[str, None]:
    """Stream GPT-4o tutor response as Server-Sent Events."""
    if not settings.azure_openai_endpoint:
        yield f"data: {json.dumps({'content': 'AI tutor not configured yet.'})}\n\n"
        yield "data: [DONE]\n\n"
        return

    client = AsyncAzureOpenAI(
        azure_endpoint=settings.azure_openai_endpoint,
        api_key=settings.azure_openai_api_key,
        api_version=settings.azure_openai_api_version,
    )

    system = TUTOR_SYSTEM_PROMPT.format(skill_profile=json.dumps(skill_profile))

    try:
        # Use stream=True with create() for async streaming
        stream = await client.chat.completions.create(
            model=settings.gpt4o_deployment,
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


@router.post("/chat")
async def tutor_chat(
    request: ChatRequest,
    current_user: dict = Depends(get_current_user),
):
    """Send a message to the AI tutor. Returns a streaming SSE response."""
    cosmos = CosmosService()
    user_id = current_user["sub"]

    skill_profile = await cosmos.get_skill_profile(user_id)

    await cosmos.append_chat_message(user_id, {
        "role": "user",
        "content": request.message,
    })

    history = await cosmos.get_chat_history(user_id, limit=10)

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
    topic: str,
    current_user: dict = Depends(get_current_user),
):
    """Generate a quiz question on a specific photography topic."""
    if not settings.azure_openai_endpoint:
        return {"error": "AI not configured"}

    client = AsyncAzureOpenAI(
        azure_endpoint=settings.azure_openai_endpoint,
        api_key=settings.azure_openai_api_key,
        api_version=settings.azure_openai_api_version,
    )
    cosmos = CosmosService()
    skill_profile = await cosmos.get_skill_profile(current_user["sub"])

    response = await client.chat.completions.create(
        model=settings.gpt4o_deployment,
        messages=[{
            "role": "user",
            "content": f"""Generate a multiple-choice photography quiz question about: {topic}
Skill level: {skill_profile.get('level', 'intermediate')}
Return JSON: {{
  "question": "...",
  "options": {{"A": "...", "B": "...", "C": "...", "D": "..."}},
  "correct_answer": "A|B|C|D",
  "explanation": "..."
}}"""
        }],
        response_format={"type": "json_object"},
        max_tokens=400,
    )

    return json.loads(response.choices[0].message.content)
