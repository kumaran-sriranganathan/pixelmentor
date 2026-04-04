"""
tests/test_lessons.py
----------------------
Unit tests for the /api/v1/lessons router using mocked Typesense.
"""

from __future__ import annotations

from unittest.mock import MagicMock

import pytest
import typesense.exceptions
from fastapi.testclient import TestClient

from app.main import app
from app.middleware.auth import get_current_user
from app.routers.lessons import get_typesense_client

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

SAMPLE_DOCS = [
    {
        "id": "lesson-001",
        "title": "Understanding Your Camera",
        "description": "Get comfortable with your camera body.",
        "content": "Full content here.",
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
        "content": "Full content here.",
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
        "content": "Full content here.",
        "category": "lighting",
        "difficulty": "advanced",
        "duration_minutes": 50,
        "is_pro": True,
        "order": 9,
        "tags": ["flash", "advanced"],
    },
]


def _make_search_response(docs: list[dict], total: int | None = None) -> dict:
    return {
        "hits": [{"document": doc} for doc in docs],
        "found": total if total is not None else len(docs),
    }


def _make_mock(docs: list[dict], *, total: int | None = None) -> MagicMock:
    mock = MagicMock()
    mock.collections.__getitem__.return_value.documents.search.return_value = \
        _make_search_response(docs, total)
    return mock


async def _mock_auth():
    return {"sub": "dev-user-123", "email": "dev@pixelmentor.com", "name": "Dev User"}


# ---------------------------------------------------------------------------
# Fixtures
# ---------------------------------------------------------------------------

@pytest.fixture()
def client():
    """Returns a TestClient with auth mocked. Each test sets its own Typesense mock."""
    app.dependency_overrides[get_current_user] = _mock_auth
    app.dependency_overrides[get_typesense_client] = lambda: _make_mock([])
    yield TestClient(app)
    app.dependency_overrides.clear()


# ---------------------------------------------------------------------------
# GET /api/v1/lessons — list
# ---------------------------------------------------------------------------

class TestListLessons:
    def test_returns_all_lessons(self, client: TestClient) -> None:
        mock = _make_mock(SAMPLE_DOCS)
        app.dependency_overrides[get_typesense_client] = lambda: mock

        resp = client.get("/api/v1/lessons")

        assert resp.status_code == 200
        body = resp.json()
        assert body["total_count"] == 3
        assert len(body["lessons"]) == 3
        assert body["page"] == 1
        assert body["page_size"] == 20

    def test_passes_search_text_when_q_provided(self, client: TestClient) -> None:
        mock = _make_mock([SAMPLE_DOCS[0]])
        app.dependency_overrides[get_typesense_client] = lambda: mock

        resp = client.get("/api/v1/lessons?q=camera")

        assert resp.status_code == 200
        call_kwargs = mock.collections.__getitem__.return_value.documents.search.call_args[0][0]
        assert call_kwargs["q"] == "camera"

    def test_uses_wildcard_when_no_q(self, client: TestClient) -> None:
        mock = _make_mock(SAMPLE_DOCS)
        app.dependency_overrides[get_typesense_client] = lambda: mock

        client.get("/api/v1/lessons")

        call_kwargs = mock.collections.__getitem__.return_value.documents.search.call_args[0][0]
        assert call_kwargs["q"] == "*"

    def test_filter_by_category(self, client: TestClient) -> None:
        mock = _make_mock([SAMPLE_DOCS[2]])
        app.dependency_overrides[get_typesense_client] = lambda: mock

        resp = client.get("/api/v1/lessons?category=lighting")

        assert resp.status_code == 200
        call_kwargs = mock.collections.__getitem__.return_value.documents.search.call_args[0][0]
        assert "category:=lighting" in call_kwargs["filter_by"]

    def test_filter_by_difficulty(self, client: TestClient) -> None:
        mock = _make_mock([SAMPLE_DOCS[2]])
        app.dependency_overrides[get_typesense_client] = lambda: mock

        client.get("/api/v1/lessons?difficulty=advanced")

        call_kwargs = mock.collections.__getitem__.return_value.documents.search.call_args[0][0]
        assert "difficulty:=advanced" in call_kwargs["filter_by"]

    def test_combined_filters(self, client: TestClient) -> None:
        mock = _make_mock([])
        app.dependency_overrides[get_typesense_client] = lambda: mock

        client.get("/api/v1/lessons?category=lighting&difficulty=advanced")

        call_kwargs = mock.collections.__getitem__.return_value.documents.search.call_args[0][0]
        assert "category:=lighting" in call_kwargs["filter_by"]
        assert "difficulty:=advanced" in call_kwargs["filter_by"]

    def test_pagination(self, client: TestClient) -> None:
        mock = _make_mock(SAMPLE_DOCS[1:2], total=3)
        app.dependency_overrides[get_typesense_client] = lambda: mock

        resp = client.get("/api/v1/lessons?page=2&page_size=1")

        assert resp.status_code == 200
        call_kwargs = mock.collections.__getitem__.return_value.documents.search.call_args[0][0]
        assert call_kwargs["page"] == 2
        assert call_kwargs["per_page"] == 1

    def test_returns_502_on_search_error(self, client: TestClient) -> None:
        mock = MagicMock()
        mock.collections.__getitem__.return_value.documents.search.side_effect = \
            Exception("Simulated failure")
        app.dependency_overrides[get_typesense_client] = lambda: mock

        resp = client.get("/api/v1/lessons")

        assert resp.status_code == 502

    def test_response_does_not_include_content(self, client: TestClient) -> None:
        mock = _make_mock(SAMPLE_DOCS)
        app.dependency_overrides[get_typesense_client] = lambda: mock

        resp = client.get("/api/v1/lessons")

        for lesson in resp.json()["lessons"]:
            assert "content" not in lesson


# ---------------------------------------------------------------------------
# GET /api/v1/lessons/{lesson_id} — detail
# ---------------------------------------------------------------------------

class TestGetLesson:
    def test_returns_lesson_with_content(self, client: TestClient) -> None:
        mock = MagicMock()
        mock.collections.__getitem__.return_value.documents.__getitem__.return_value.retrieve.return_value = \
            SAMPLE_DOCS[0]
        app.dependency_overrides[get_typesense_client] = lambda: mock

        resp = client.get("/api/v1/lessons/lesson-001")

        assert resp.status_code == 200
        body = resp.json()
        assert body["id"] == "lesson-001"
        assert "content" in body

    def test_returns_404_for_missing_lesson(self, client: TestClient) -> None:
        mock = MagicMock()
        mock.collections.__getitem__.return_value.documents.__getitem__.return_value.retrieve.side_effect = \
            typesense.exceptions.ObjectNotFound("lesson", "not found")
        app.dependency_overrides[get_typesense_client] = lambda: mock

        resp = client.get("/api/v1/lessons/does-not-exist")

        assert resp.status_code == 404

    def test_returns_502_on_search_error(self, client: TestClient) -> None:
        mock = MagicMock()
        mock.collections.__getitem__.return_value.documents.__getitem__.return_value.retrieve.side_effect = \
            Exception("Upstream error")
        app.dependency_overrides[get_typesense_client] = lambda: mock

        resp = client.get("/api/v1/lessons/lesson-001")

        assert resp.status_code == 502
