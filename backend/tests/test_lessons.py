"""
tests/test_lessons.py
----------------------
Unit tests for the /api/v1/lessons router using mocked Supabase.
"""

from __future__ import annotations
from unittest.mock import MagicMock, patch
import pytest
from fastapi.testclient import TestClient

from app.main import app
from app.middleware.auth import get_current_user
from app.utils.supabase_client import get_supabase_client

# ---------------------------------------------------------------------------
# Sample data
# ---------------------------------------------------------------------------

SAMPLE_LESSONS = [
    {
        "id": "lesson-001",
        "title": "Understanding Your Camera",
        "description": "Get comfortable with your camera body.",
        "category": "fundamentals",
        "difficulty": "beginner",
        "duration_minutes": 15,
        "is_pro": False,
        "order": 1,
        "tags": ["camera", "beginner"],
    },
    {
        "id": "lesson-002",
        "title": "The Exposure Triangle",
        "description": "Master aperture, shutter speed, and ISO.",
        "category": "fundamentals",
        "difficulty": "beginner",
        "duration_minutes": 20,
        "is_pro": False,
        "order": 2,
        "tags": ["exposure", "beginner"],
    },
    {
        "id": "lesson-009",
        "title": "Advanced Flash Techniques",
        "description": "Off-camera lighting for pros.",
        "category": "lighting",
        "difficulty": "advanced",
        "duration_minutes": 50,
        "is_pro": True,
        "order": 9,
        "tags": ["flash", "advanced"],
    },
]

SAMPLE_LESSON_DETAIL = {
    **SAMPLE_LESSONS[0],
    "content": "Full lesson content here.",
}


async def _mock_auth():
    return {"sub": "dev-user-123", "email": "dev@pixelmentor.com", "name": "Dev User"}


def _make_supabase_mock(lessons=None, lesson=None, count=None):
    """Create a mock Supabase client."""
    mock = MagicMock()

    # Mock for list query
    list_response = MagicMock()
    list_response.data = lessons if lessons is not None else SAMPLE_LESSONS
    list_response.count = count if count is not None else len(SAMPLE_LESSONS)

    # Mock for single lesson query
    detail_response = MagicMock()
    detail_response.data = lesson if lesson is not None else SAMPLE_LESSON_DETAIL

    # Chain the query builder
    mock_query = MagicMock()
    mock_query.select.return_value = mock_query
    mock_query.eq.return_value = mock_query
    mock_query.order.return_value = mock_query
    mock_query.range.return_value = mock_query
    mock_query.single.return_value = mock_query
    mock_query.text_search.return_value = mock_query
    mock_query.execute.return_value = list_response

    mock.table.return_value = mock_query

    return mock


# ---------------------------------------------------------------------------
# Fixtures
# ---------------------------------------------------------------------------

@pytest.fixture()
def client():
    app.dependency_overrides[get_current_user] = _mock_auth
    with patch("app.routers.lessons.get_supabase_client") as mock_get_client:
        mock_get_client.return_value = _make_supabase_mock()
        yield TestClient(app)
    app.dependency_overrides.clear()


# ---------------------------------------------------------------------------
# GET /api/v1/lessons — list
# ---------------------------------------------------------------------------

class TestListLessons:
    def test_returns_200(self, client: TestClient) -> None:
        resp = client.get("/api/v1/lessons")
        assert resp.status_code == 200

    def test_returns_lessons_list(self, client: TestClient) -> None:
        resp = client.get("/api/v1/lessons")
        body = resp.json()
        assert "lessons" in body
        assert isinstance(body["lessons"], list)

    def test_returns_total_count(self, client: TestClient) -> None:
        resp = client.get("/api/v1/lessons")
        body = resp.json()
        assert "total_count" in body

    def test_returns_page_info(self, client: TestClient) -> None:
        resp = client.get("/api/v1/lessons")
        body = resp.json()
        assert body["page"] == 1
        assert body["page_size"] == 20

    def test_accepts_difficulty_filter(self, client: TestClient) -> None:
        resp = client.get("/api/v1/lessons?difficulty=beginner")
        assert resp.status_code == 200

    def test_accepts_category_filter(self, client: TestClient) -> None:
        resp = client.get("/api/v1/lessons?category=fundamentals")
        assert resp.status_code == 200

    def test_accepts_search_query(self, client: TestClient) -> None:
        resp = client.get("/api/v1/lessons?q=camera")
        assert resp.status_code == 200

    def test_accepts_pagination(self, client: TestClient) -> None:
        resp = client.get("/api/v1/lessons?page=2&page_size=5")
        assert resp.status_code == 200

    def test_returns_502_on_db_error(self, client: TestClient) -> None:
        with patch("app.routers.lessons.get_supabase_client") as mock:
            mock.return_value.table.side_effect = Exception("DB error")
            resp = client.get("/api/v1/lessons")
        assert resp.status_code == 502


# ---------------------------------------------------------------------------
# GET /api/v1/lessons/{lesson_id} — detail
# ---------------------------------------------------------------------------

class TestGetLesson:
    def test_returns_200(self, client: TestClient) -> None:
        with patch("app.routers.lessons.get_supabase_client") as mock_get_client:
            mock_get_client.return_value = _make_supabase_mock(lesson=SAMPLE_LESSON_DETAIL)
            resp = client.get("/api/v1/lessons/lesson-001")
        assert resp.status_code == 200

    def test_returns_lesson_fields(self, client: TestClient) -> None:
        with patch("app.routers.lessons.get_supabase_client") as mock_get_client:
            mock_get_client.return_value = _make_supabase_mock(lesson=SAMPLE_LESSON_DETAIL)
            resp = client.get("/api/v1/lessons/lesson-001")
        body = resp.json()
        assert body["id"] == "lesson-001"
        assert "content" in body

    def test_returns_404_for_missing_lesson(self, client: TestClient) -> None:
        with patch("app.routers.lessons.get_supabase_client") as mock_get_client:
            mock_supabase = _make_supabase_mock()
            # Return None data for missing lesson
            mock_response = MagicMock()
            mock_response.data = None
            mock_supabase.table.return_value.select.return_value.eq.return_value.single.return_value.execute.return_value = mock_response
            mock_get_client.return_value = mock_supabase
            resp = client.get("/api/v1/lessons/does-not-exist")
        assert resp.status_code == 404

    def test_returns_502_on_db_error(self, client: TestClient) -> None:
        with patch("app.routers.lessons.get_supabase_client") as mock:
            mock.return_value.table.side_effect = Exception("DB error")
            resp = client.get("/api/v1/lessons/lesson-001")
        assert resp.status_code == 502
