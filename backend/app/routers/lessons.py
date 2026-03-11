###############################################################################
# routers/lessons.py — Lesson library endpoints
###############################################################################

from fastapi import APIRouter
from typing import List
from pydantic import BaseModel

router = APIRouter()


class Lesson(BaseModel):
    id: str
    title: str
    description: str
    duration_minutes: int
    skill_level: str
    thumbnail_url: str = ""


SAMPLE_LESSONS = [
    Lesson(id="1", title="Rule of Thirds", description="Master the fundamental composition technique", duration_minutes=15, skill_level="beginner"),
    Lesson(id="2", title="Golden Hour Lighting", description="Capture stunning natural light", duration_minutes=20, skill_level="beginner"),
    Lesson(id="3", title="Depth of Field", description="Control focus for creative effect", duration_minutes=25, skill_level="intermediate"),
]


@router.get("/", response_model=List[Lesson])
async def get_lessons(skill_level: str = None):
    if skill_level:
        return [l for l in SAMPLE_LESSONS if l.skill_level == skill_level]
    return SAMPLE_LESSONS


@router.get("/{lesson_id}", response_model=Lesson)
async def get_lesson(lesson_id: str):
    for lesson in SAMPLE_LESSONS:
        if lesson.id == lesson_id:
            return lesson
    from fastapi import HTTPException
    raise HTTPException(status_code=404, detail="Lesson not found")
