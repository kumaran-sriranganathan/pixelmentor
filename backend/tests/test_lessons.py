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


def _make_supabase_mock(lessons=None, lesson=None, count=None, raise_on_table=False):
    """Create a fully chained mock Supabase client."""
    mock = MagicMock()

    if raise_on_table:
        mock.table.side_effect = Exception("DB error")
        return mock

    list_response = MagicMock()
    list_response.data = lessons if lessons is not None else SAMPLE_LESSONS
    list_response.count = count if count is not None else len(SAMPLE_LESSONS)

    single_response = MagicMock()
    single_response.data = lesson if lesson is not None else SAMPLE_LESSON_DETAIL

    mock_query = MagicMock()
    mock_query.select.return_value = mock_query
    mock_query.eq.return_value = mock_query
    mock_query.order.return_value = mock_query
    mock_query.range.return_value = mock_query
    mock_query.limit.return_value = mock_query
    mock_query.text_search.return_value = mock_query
    mock_query.delete.return_value = mock_query
    mock_query.execute.return_value = list_response
    # single() returns a separate chain that resolves to the single item
    mock_single = MagicMock()
    mock_single.execute.return_value = single_response
    mock_query.single.return_value = mock_single

    mock.table.return_value = mock_query
    mock.rpc.return_value = mock_query

    return mock


# ---------------------------------------------------------------------------
# Fixtures
# ---------------------------------------------------------------------------

@pytest.fixture()
def client():
    app.dependency_overrides[get_current_user] = _mock_auth
    mock_sb = _make_supabase_mock()
    # Patch Client.create to prevent the get_session() network call
    with patch("supabase.Client.create", return_value=mock_sb), \
         patch("app.utils.supabase_client.get_supabase_admin", return_value=mock_sb), \
         patch("app.utils.supabase_client.get_supabase_client", return_value=mock_sb):
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
        err_mock = _make_supabase_mock(raise_on_table=True)
        with patch("supabase.Client.create", return_value=err_mock), \
             patch("app.utils.supabase_client.get_supabase_admin", return_value=err_mock), \
             patch("app.utils.supabase_client.get_supabase_client", return_value=err_mock):
            resp = client.get("/api/v1/lessons")
        assert resp.status_code == 502


# ---------------------------------------------------------------------------
# GET /api/v1/lessons/{lesson_id} — detail
# ---------------------------------------------------------------------------

class TestGetLesson:
    def test_returns_200(self, client: TestClient) -> None:
        resp = client.get("/api/v1/lessons/lesson-001")
        assert resp.status_code == 200

    def test_returns_lesson_fields(self, client: TestClient) -> None:
        resp = client.get("/api/v1/lessons/lesson-001")
        assert resp.status_code == 200
        body = resp.json()
        assert body["id"] == "lesson-001"
        assert "content" in body

    def test_returns_404_for_missing_lesson(self, client: TestClient) -> None:
        missing_mock = _make_supabase_mock(lesson=None)
        missing_mock.table.return_value.select.return_value.eq.return_value \
            .single.return_value.execute.return_value.data = None
        with patch("supabase.Client.create", return_value=missing_mock), \
             patch("app.utils.supabase_client.get_supabase_admin", return_value=missing_mock), \
             patch("app.utils.supabase_client.get_supabase_client", return_value=missing_mock):
            resp = client.get("/api/v1/lessons/does-not-exist")
        assert resp.status_code == 404

    def test_returns_502_on_db_error(self, client: TestClient) -> None:
        err_mock = _make_supabase_mock(raise_on_table=True)
        with patch("supabase.Client.create", return_value=err_mock), \
             patch("app.utils.supabase_client.get_supabase_admin", return_value=err_mock), \
             patch("app.utils.supabase_client.get_supabase_client", return_value=err_mock):
            resp = client.get("/api/v1/lessons/lesson-001")
        assert resp.status_code == 502
