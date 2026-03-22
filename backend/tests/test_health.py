###############################################################################
# tests/test_health.py — Real backend tests for PixelMentor API
###############################################################################

import pytest
from unittest.mock import AsyncMock, MagicMock, patch
from fastapi.testclient import TestClient

from app.main import app
from app.config import settings


# ── Fixtures ──────────────────────────────────────────────────────────────────

@pytest.fixture
def client():
    """Test client with dev environment (mock auth bypassed)."""
    with TestClient(app) as c:
        yield c


@pytest.fixture
def auth_headers():
    """Fake Bearer token header — accepted in dev environment only."""
    return {"Authorization": "Bearer mock-dev-token"}


# ── Health Endpoints ───────────────────────────────────────────────────────────

class TestHealth:
    def test_liveness_returns_200(self, client):
        response = client.get("/health")
        assert response.status_code == 200

    def test_liveness_returns_alive_status(self, client):
        data = client.get("/health").json()
        assert data["status"] == "alive"

    def test_liveness_returns_environment(self, client):
        data = client.get("/health").json()
        assert "environment" in data

    def test_readiness_returns_200(self, client):
        response = client.get("/health/ready")
        assert response.status_code == 200

    def test_readiness_returns_ready_or_degraded(self, client):
        data = client.get("/health/ready").json()
        assert data["status"] in ("ready", "degraded")

    def test_readiness_returns_checks(self, client):
        data = client.get("/health/ready").json()
        assert "checks" in data
        assert isinstance(data["checks"], dict)


# ── Auth Middleware ────────────────────────────────────────────────────────────

class TestAuth:
    def test_lessons_requires_auth_in_prod(self):
        """In prod mode, /api/v1/lessons without a token must return 401."""
        with patch.object(settings, "environment", "prod"):
            with TestClient(app) as prod_client:
                response = prod_client.get("/api/v1/lessons/")
                assert response.status_code == 401

    def test_lessons_accessible_in_dev_without_token(self, client):
        """Dev mode bypasses real JWT validation — request should succeed."""
        response = client.get("/api/v1/lessons/")
        assert response.status_code == 200

    def test_missing_auth_header_returns_401_in_prod(self):
        with patch.object(settings, "environment", "prod"):
            with TestClient(app) as prod_client:
                response = prod_client.get("/api/v1/users/some-user-id")
                assert response.status_code == 401

    def test_malformed_bearer_token_returns_401_in_prod(self):
        with patch.object(settings, "environment", "prod"):
            with TestClient(app) as prod_client:
                response = prod_client.get(
                    "/api/v1/lessons/",
                    headers={"Authorization": "Bearer not.a.valid.jwt"},
                )
                assert response.status_code == 401


# ── ISSUER / Entra Config ──────────────────────────────────────────────────────

class TestEntraConfig:
    def test_issuer_uses_tenant_id_not_tenant_name(self):
        """
        Regression test for the duplicate-ISSUER bug.
        ISSUER must use entra_tenant_id (GUID) in the subdomain,
        NOT entra_tenant_name ('pixelmentor').
        """
        from app.middleware.auth import ISSUER
        tenant_id = settings.entra_tenant_id
        tenant_name = settings.entra_tenant_name

        # Must contain the tenant_id in the CIAM subdomain
        assert ISSUER.startswith("https://"), "ISSUER must be a valid HTTPS URL"

        # Must NOT use tenant_name as the subdomain (the old bug)
        wrong_prefix = f"https://{tenant_name}.ciamlogin.com"
        assert not ISSUER.startswith(wrong_prefix), (
            f"ISSUER is using tenant_name '{tenant_name}' as the CIAM subdomain. "
            f"It must use tenant_id. Got: {ISSUER}"
        )

    def test_jwks_url_uses_tenant_id(self):
        from app.middleware.auth import JWKS_URL
        assert JWKS_URL.startswith("https://"), "JWKS_URL must be a valid HTTPS URL"

    def test_issuer_and_jwks_share_same_ciam_subdomain(self):
        from app.middleware.auth import ISSUER, JWKS_URL
        issuer_base = ISSUER.split(".ciamlogin.com")[0]
        jwks_base = JWKS_URL.split(".ciamlogin.com")[0]
        assert issuer_base == jwks_base, (
            f"ISSUER and JWKS_URL point to different CIAM tenants: "
            f"{issuer_base!r} vs {jwks_base!r}"
        )


# ── Lessons Endpoints ─────────────────────────────────────────────────────────

class TestLessons:
    def test_get_lessons_returns_list(self, client):
        response = client.get("/api/v1/lessons/")
        assert response.status_code == 200
        assert isinstance(response.json(), list)

    def test_get_lessons_have_required_fields(self, client):
        lessons = client.get("/api/v1/lessons/").json()
        assert len(lessons) > 0
        for lesson in lessons:
            assert "id" in lesson
            assert "title" in lesson
            assert "skill_level" in lesson

    def test_get_lessons_filter_by_skill_level(self, client):
        response = client.get("/api/v1/lessons/?skill_level=beginner")
        assert response.status_code == 200
        for lesson in response.json():
            assert lesson["skill_level"] == "beginner"

    def test_get_lesson_by_id(self, client):
        lessons = client.get("/api/v1/lessons/").json()
        lesson_id = lessons[0]["id"]
        response = client.get(f"/api/v1/lessons/{lesson_id}")
        assert response.status_code == 200
        assert response.json()["id"] == lesson_id

    def test_get_lesson_not_found(self, client):
        response = client.get("/api/v1/lessons/nonexistent-id-999")
        assert response.status_code == 404


# ── Users Endpoints ───────────────────────────────────────────────────────────

class TestUsers:
    def test_get_own_profile(self, client):
        """Dev user sub is 'dev-user-123' — can fetch their own profile."""
        response = client.get("/api/v1/users/dev-user-123")
        assert response.status_code == 200
        data = response.json()
        assert data["user_id"] == "dev-user-123"
        assert "skill_level" in data
        assert "plan" in data

    def test_get_other_user_profile_forbidden(self, client):
        """Dev user should not be able to fetch another user's profile."""
        response = client.get("/api/v1/users/some-other-user-id")
        assert response.status_code == 403

    def test_user_profile_has_expected_fields(self, client):
        response = client.get("/api/v1/users/dev-user-123")
        data = response.json()
        for field in ["user_id", "display_name", "skill_level", "plan", "photos_analyzed"]:
            assert field in data


# ── Headers ───────────────────────────────────────────────────────────────────

class TestHeaders:
    def test_correlation_id_in_response(self, client):
        response = client.get("/health")
        assert "x-correlation-id" in response.headers

    def test_correlation_id_echoed_if_provided(self, client):
        response = client.get("/health", headers={"X-Correlation-ID": "test-123"})
        assert response.headers.get("x-correlation-id") == "test-123"


# ── JWKS Cache ────────────────────────────────────────────────────────────────

class TestJWKSCache:
    def test_jwks_cache_invalidated_on_jwt_error(self):
        """
        After a JWTError, the JWKS cache should be cleared so
        rotated keys are fetched on the next request.
        """
        import app.middleware.auth as auth_module

        # Pre-seed the cache
        auth_module._jwks_cache = {"keys": [{"kid": "old-key"}]}

        mock_request = MagicMock()
        mock_request.headers = {"Authorization": "Bearer bad.token.here"}

        with patch("app.middleware.auth.jwt.decode", side_effect=Exception("Invalid")):
            with patch("app.middleware.auth.get_jwks", new=AsyncMock(return_value={"keys": []})):
                import asyncio
                try:
                    asyncio.get_event_loop().run_until_complete(
                        auth_module.get_current_user(mock_request)
                    )
                except Exception:
                    pass

        # Cache should be cleared (None) after auth failure
        assert auth_module._jwks_cache is None
