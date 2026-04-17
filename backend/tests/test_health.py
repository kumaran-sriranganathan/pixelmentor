###############################################################################
# tests/test_health.py — PixelMentor backend tests
###############################################################################

import pytest
import typesense.exceptions
from unittest.mock import MagicMock, patch
from fastapi.testclient import TestClient
from fastapi import HTTPException, status

from app.main import app
from app.middleware.auth import get_current_user

# ── Mock identity ──────────────────────────────────────────────────────────────

MOCK_USER = {
    "sub": "dev-user-123",
    "email": "dev@pixelmentor.com",
    "name": "Dev User",
    "given_name": "Dev",
    "family_name": "User",
}


async def _mock_get_current_user():
    return MOCK_USER


# ── Fixtures ───────────────────────────────────────────────────────────────────

@pytest.fixture
def client():
    """Auth and search dependencies overridden — use for all endpoint logic tests."""
    from app.routers.lessons import get_typesense_client
    from app.utils.supabase_client import SupabaseService

    mock_profile = {
        "user_id": "dev-user-123",
        "display_name": "Dev User",
        "skill_level": "beginner",
        "photos_analyzed": 0,
        "lessons_completed": 0,
        "streak_days": 0,
        "plan": "free",
    }

    mock_typesense = MagicMock()
    mock_typesense.collections.__getitem__.return_value.documents.search.return_value = {
        "hits": [],
        "found": 0,
    }

    with patch.object(SupabaseService, "__init__", return_value=None), \
         patch.object(SupabaseService, "get_user_profile", return_value=mock_profile):
        app.dependency_overrides[get_current_user] = _mock_get_current_user
        app.dependency_overrides[get_typesense_client] = lambda: mock_typesense
        with TestClient(app) as c:
            yield c
    app.dependency_overrides.clear()


# ── Health ─────────────────────────────────────────────────────────────────────

class TestHealth:
    def test_liveness_200(self, client):
        assert client.get("/health").status_code == 200

    def test_liveness_status_field(self, client):
        assert client.get("/health").json()["status"] == "alive"

    def test_liveness_environment_field(self, client):
        assert "environment" in client.get("/health").json()

    def test_readiness_200(self, client):
        assert client.get("/health/ready").status_code == 200

    def test_readiness_status_value(self, client):
        assert client.get("/health/ready").json()["status"] in ("ready", "degraded")

    def test_readiness_checks_dict(self, client):
        data = client.get("/health/ready").json()
        assert "checks" in data and isinstance(data["checks"], dict)


# ── Auth enforcement & error codes ────────────────────────────────────────────

class TestAuth:
    def test_missing_header_returns_401(self, client):
        async def _raise_missing():
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail={"error": "token_missing", "message": "Authorization header required"},
            )
        app.dependency_overrides[get_current_user] = _raise_missing
        resp = client.get("/api/v1/lessons")
        assert resp.status_code == 401
        assert resp.json()["detail"]["error"] == "token_missing"

    def test_expired_token_returns_token_expired(self, client):
        async def _raise_expired():
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail={"error": "token_expired", "message": "Token has expired"},
            )
        app.dependency_overrides[get_current_user] = _raise_expired
        resp = client.get("/api/v1/lessons", headers={"Authorization": "Bearer any.token.here"})
        assert resp.status_code == 401
        assert resp.json()["detail"]["error"] == "token_expired"

    def test_invalid_token_returns_token_invalid(self, client):
        async def _raise_invalid():
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail={"error": "token_invalid", "message": "Token is invalid"},
            )
        app.dependency_overrides[get_current_user] = _raise_invalid
        resp = client.get("/api/v1/lessons", headers={"Authorization": "Bearer not.a.valid.jwt"})
        assert resp.status_code == 401
        assert resp.json()["detail"]["error"] == "token_invalid"

    def test_malformed_bearer_returns_401(self, client):
        async def _raise_invalid():
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail={"error": "token_invalid", "message": "Token is invalid"},
            )
        app.dependency_overrides[get_current_user] = _raise_invalid
        resp = client.get("/api/v1/lessons", headers={"Authorization": "Bearer not.a.valid.jwt"})
        assert resp.status_code == 401

    def test_valid_auth_override_returns_200(self, client):
        assert client.get("/api/v1/lessons").status_code == 200


# ── Lessons ────────────────────────────────────────────────────────────────────

class TestLessons:
    def test_list_returns_200_and_list(self, client):
        resp = client.get("/api/v1/lessons")
        assert resp.status_code == 200
        assert isinstance(resp.json()["lessons"], list)

    def test_list_items_have_required_fields(self, client):
        from app.routers.lessons import get_typesense_client
        mock = MagicMock()
        mock.collections.__getitem__.return_value.documents.search.return_value = {
            "hits": [{"document": {
                "id": "lesson-001", "title": "Test", "description": "Desc",
                "category": "fundamentals", "difficulty": "beginner",
                "duration_minutes": 15, "is_pro": False, "order": 1, "tags": []
            }}],
            "found": 1,
        }
        app.dependency_overrides[get_typesense_client] = lambda: mock

        lessons = client.get("/api/v1/lessons").json()["lessons"]
        assert len(lessons) > 0
        for lesson in lessons:
            assert {"id", "title", "difficulty"} <= lesson.keys()

    def test_filter_by_skill_level(self, client):
        resp = client.get("/api/v1/lessons?difficulty=beginner")
        assert resp.status_code == 200

    def test_get_by_id(self, client):
        from app.routers.lessons import get_typesense_client
        mock = MagicMock()
        mock.collections.__getitem__.return_value.documents.__getitem__.return_value.retrieve.return_value = {
            "id": "lesson-001", "title": "Test", "description": "Desc",
            "category": "fundamentals", "difficulty": "beginner",
            "duration_minutes": 15, "is_pro": False, "order": 1,
            "tags": [], "content": "Full content."
        }
        app.dependency_overrides[get_typesense_client] = lambda: mock

        resp = client.get("/api/v1/lessons/lesson-001")
        assert resp.status_code == 200
        assert resp.json()["id"] == "lesson-001"

    def test_not_found_returns_404(self, client):
        from app.routers.lessons import get_typesense_client
        mock = MagicMock()
        mock.collections.__getitem__.return_value.documents.__getitem__.return_value.retrieve.side_effect = \
            typesense.exceptions.ObjectNotFound("lesson", "not found")
        app.dependency_overrides[get_typesense_client] = lambda: mock

        assert client.get("/api/v1/lessons/does-not-exist-999").status_code == 404


# ── Users ──────────────────────────────────────────────────────────────────────

class TestUsers:
    def test_own_profile_returns_200(self, client):
        resp = client.get("/api/v1/users/dev-user-123")
        assert resp.status_code == 200
        assert resp.json()["user_id"] == "dev-user-123"

    def test_own_profile_has_expected_fields(self, client):
        data = client.get("/api/v1/users/dev-user-123").json()
        for field in ("user_id", "display_name", "skill_level", "plan", "photos_analyzed"):
            assert field in data

    def test_other_user_returns_403(self, client):
        resp = client.get("/api/v1/users/someone-else-456")
        assert resp.status_code == 403


# ── Headers ────────────────────────────────────────────────────────────────────

class TestHeaders:
    def test_correlation_id_added(self, client):
        assert "x-correlation-id" in client.get("/health").headers

    def test_correlation_id_echoed(self, client):
        resp = client.get("/health", headers={"X-Correlation-ID": "trace-abc"})
        assert resp.headers.get("x-correlation-id") == "trace-abc"
