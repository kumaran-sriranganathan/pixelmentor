"""
tests/test_lessons.py
----------------------
Unit tests for the /lessons router using mocked Azure AI Search.
Run with:  pytest tests/test_lessons.py -v
"""

from __future__ import annotations

from typing import Any
from unittest.mock import MagicMock, patch

import pytest
from fastapi.testclient import TestClient

from app.main import app
from app.routers.lessons import get_search_client

# ---------------------------------------------------------------------------
# Helpers / fixtures
# ---------------------------------------------------------------------------

SAMPLE_LESSONS = [
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


def _make_search_results(docs: list[dict], total: int | None = None) -> MagicMock:
    """Return a mock that behaves like azure.search.documents.SearchItemPaged."""
    mock_results = MagicMock()
    mock_results.__iter__ = MagicMock(return_value=iter(docs))
    mock_results.get_count = MagicMock(return_value=total if total is not None else len(docs))
    return mock_results


def _mock_search_client(docs: list[dict], *, total: int | None = None) -> MagicMock:
    client = MagicMock()
    client.search.return_value = _make_search_results(docs, total)
    return client


# ---------------------------------------------------------------------------
# Override FastAPI dependency
# ---------------------------------------------------------------------------

@pytest.fixture(autouse=True)
def override_search_client():
    """Replace the real SearchClient with a mock for every test."""
    # Tests that need custom behaviour can call app.dependency_overrides directly.
    yield


@pytest.fixture()
def client():
    return TestClient(app)


# ---------------------------------------------------------------------------
# GET /lessons — list
# ---------------------------------------------------------------------------

class TestListLessons:
    def test_returns_all_lessons(self, client: TestClient) -> None:
        mock = _mock_search_client(SAMPLE_LESSONS)
        app.dependency_overrides[get_search_client] = lambda: mock

        resp = client.get("/lessons")

        assert resp.status_code == 200
        body = resp.json()
        assert body["total_count"] == 3
        assert len(body["lessons"]) == 3
        assert body["page"] == 1
        assert body["page_size"] == 20

    def test_passes_search_text_when_q_provided(self, client: TestClient) -> None:
        mock = _mock_search_client([SAMPLE_LESSONS[0]])
        app.dependency_overrides[get_search_client] = lambda: mock

        resp = client.get("/lessons?q=camera")

        assert resp.status_code == 200
        _, kwargs = mock.search.call_args
        assert kwargs["search_text"] == "camera"

    def test_uses_wildcard_when_no_q(self, client: TestClient) -> None:
        mock = _mock_search_client(SAMPLE_LESSONS)
        app.dependency_overrides[get_search_client] = lambda: mock

        client.get("/lessons")

        _, kwargs = mock.search.call_args
        assert kwargs["search_text"] == "*"

    def test_filter_by_category(self, client: TestClient) -> None:
        mock = _mock_search_client([SAMPLE_LESSONS[2]])
        app.dependency_overrides[get_search_client] = lambda: mock

        resp = client.get("/lessons?category=lighting")

        assert resp.status_code == 200
        _, kwargs = mock.search.call_args
        assert "category eq 'lighting'" in kwargs["filter"]

    def test_filter_by_difficulty(self, client: TestClient) -> None:
        mock = _mock_search_client([SAMPLE_LESSONS[2]])
        app.dependency_overrides[get_search_client] = lambda: mock

        client.get("/lessons?difficulty=advanced")

        _, kwargs = mock.search.call_args
        assert "difficulty eq 'advanced'" in kwargs["filter"]

    def test_combined_filters(self, client: TestClient) -> None:
        mock = _mock_search_client([])
        app.dependency_overrides[get_search_client] = lambda: mock

        client.get("/lessons?category=lighting&difficulty=advanced")

        _, kwargs = mock.search.call_args
        f = kwargs["filter"]
        assert "category eq 'lighting'" in f
        assert "difficulty eq 'advanced'" in f

    def test_pagination(self, client: TestClient) -> None:
        mock = _mock_search_client(SAMPLE_LESSONS[1:2], total=3)
        app.dependency_overrides[get_search_client] = lambda: mock

        resp = client.get("/lessons?page=2&page_size=1")

        assert resp.status_code == 200
        _, kwargs = mock.search.call_args
        assert kwargs["skip"] == 1
        assert kwargs["top"] == 1

    def test_returns_502_on_search_error(self, client: TestClient) -> None:
        from azure.core.exceptions import HttpResponseError

        broken = MagicMock()
        broken.search.side_effect = HttpResponseError(message="Simulated failure")
        app.dependency_overrides[get_search_client] = lambda: broken

        resp = client.get("/lessons")

        assert resp.status_code == 502

    def test_response_does_not_include_content(self, client: TestClient) -> None:
        mock = _mock_search_client(SAMPLE_LESSONS)
        app.dependency_overrides[get_search_client] = lambda: mock

        resp = client.get("/lessons")

        for lesson in resp.json()["lessons"]:
            assert "content" not in lesson


# ---------------------------------------------------------------------------
# GET /lessons/{lesson_id} — detail
# ---------------------------------------------------------------------------

class TestGetLesson:
    def test_returns_lesson_with_content(self, client: TestClient) -> None:
        mock = MagicMock()
        mock.get_document.return_value = SAMPLE_LESSONS[0]
        app.dependency_overrides[get_search_client] = lambda: mock

        resp = client.get("/lessons/lesson-001")

        assert resp.status_code == 200
        body = resp.json()
        assert body["id"] == "lesson-001"
        assert "content" in body

    def test_returns_404_for_missing_lesson(self, client: TestClient) -> None:
        from azure.core.exceptions import HttpResponseError

        mock = MagicMock()
        err = HttpResponseError(message="Not found")
        err.status_code = 404
        mock.get_document.side_effect = err
        app.dependency_overrides[get_search_client] = lambda: mock

        resp = client.get("/lessons/does-not-exist")

        assert resp.status_code == 404

    def test_returns_502_on_search_error(self, client: TestClient) -> None:
        from azure.core.exceptions import HttpResponseError

        mock = MagicMock()
        err = HttpResponseError(message="Upstream error")
        err.status_code = 500
        mock.get_document.side_effect = err
        app.dependency_overrides[get_search_client] = lambda: mock

        resp = client.get("/lessons/lesson-001")

        assert resp.status_code == 502
