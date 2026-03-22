###############################################################################
# tests/test_health.py — PixelMentor backend tests
#
# Auth strategy
# -------------
# get_current_user() has a hard bypass: if settings.environment == "dev" it
# returns a mock user without touching JWT logic at all.  CI runs with
# ENVIRONMENT=dev, so ALL tests use FastAPI dependency_overrides to inject a
# known user — this works regardless of the environment value and avoids any
# real network calls to Entra.
#
# Tests that need to exercise real auth logic (401, JWKS cache) patch
# app.middleware.auth.settings directly on the module-level name so that
# get_current_user() sees a non-dev environment.
###############################################################################

import asyncio
import pytest
from unittest.mock import AsyncMock, MagicMock, patch
from fastapi.testclient import TestClient
from jose import JWTError

from app.main import app
from app.middleware.auth import get_current_user
import app.middleware.auth as auth_module

# ── Shared mock identity ───────────────────────────────────────────────────────

MOCK_USER = {
    "sub": "dev-user-123",
    "email": "dev@pixelmentor.com",
    "name": "Dev User",
    "given_name": "Dev",
    "family_name": "User",
}


async def _mock_user():
    return MOCK_USER


# ── Helpers ────────────────────────────────────────────────────────────────────

def _make_settings(environment: str):
    """Return a settings-like object with environment overridden."""
    from app.config import get_settings
    original = get_settings()

    class _Patched:
        def __getattr__(self, name):
            if name == "environment":
                return environment
            return getattr(original, name)

    return _Patched()


# ── Fixtures ───────────────────────────────────────────────────────────────────

@pytest.fixture
def client():
    """
    Standard test client — auth dependency overridden with MOCK_USER.
    Use this for all endpoint logic tests.
    """
    app.dependency_overrides[get_current_user] = lambda: _mock_user
    with TestClient(app) as c:
        yield c
    app.dependency_overrides.clear()


@pytest.fixture
def client_real_auth():
    """
    Test client with no dependency override and environment forced to prod
    so real JWT validation runs inside get_current_user.
    """
    app.dependency_overrides.clear()
    with patch.object(auth_module, "settings", _make_settings("prod")):
        with TestClient(app) as c:
            yield c


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


# ── Auth enforcement ───────────────────────────────────────────────────────────

class TestAuth:
    def test_no_token_returns_401(self, client_real_auth):
        """No Authorization header → 401."""
        assert client_real_auth.get("/api/v1/lessons/").status_code == 401

    def test_malformed_token_returns_401(self, client_real_auth):
        """
        A syntactically invalid JWT causes jose to raise JWTError, which
        get_current_user catches and returns 401 — not 503.
        503 only happens on httpx.HTTPError (JWKS fetch failure), so we
        mock get_jwks to avoid any network call.
        """
        with patch.object(auth_module, "get_jwks",
                          new=AsyncMock(return_value={"keys": []})):
            resp = client_real_auth.get(
                "/api/v1/lessons/",
                headers={"Authorization": "Bearer not.a.valid.jwt"},
            )
        assert resp.status_code == 401

    def test_missing_auth_header_detail(self, client_real_auth):
        resp = client_real_auth.get("/api/v1/lessons/")
        assert resp.status_code == 401
        assert "authorization" in resp.json()["detail"].lower()


# ── Entra ISSUER regression ────────────────────────────────────────────────────

class TestEntraConfig:
    def test_issuer_uses_tenant_id_not_tenant_name(self):
        """
        Regression: duplicate ISSUER bug used entra_tenant_name ('pixelmentor')
        as the CIAM subdomain instead of entra_tenant_id (GUID).
        """
        from app.middleware.auth import ISSUER
        from app.config import settings
        wrong_prefix = f"https://{settings.entra_tenant_name}.ciamlogin.com"
        assert not ISSUER.startswith(wrong_prefix), (
            f"ISSUER uses tenant_name as subdomain (duplicate-ISSUER bug). Got: {ISSUER}"
        )
        assert ISSUER.startswith("https://")

    def test_issuer_and_jwks_same_subdomain(self):
        from app.middleware.auth import ISSUER, JWKS_URL
        assert ISSUER.split(".ciamlogin.com")[0] == JWKS_URL.split(".ciamlogin.com")[0]


# ── Lessons ────────────────────────────────────────────────────────────────────

class TestLessons:
    def test_list_returns_200_and_list(self, client):
        resp = client.get("/api/v1/lessons/")
        assert resp.status_code == 200
        assert isinstance(resp.json(), list)

    def test_list_items_have_required_fields(self, client):
        lessons = client.get("/api/v1/lessons/").json()
        assert len(lessons) > 0
        for lesson in lessons:
            assert {"id", "title", "skill_level"} <= lesson.keys()

    def test_filter_by_skill_level(self, client):
        resp = client.get("/api/v1/lessons/?skill_level=beginner")
        assert resp.status_code == 200
        assert all(l["skill_level"] == "beginner" for l in resp.json())

    def test_get_by_id(self, client):
        lessons = client.get("/api/v1/lessons/").json()
        lid = lessons[0]["id"]
        resp = client.get(f"/api/v1/lessons/{lid}")
        assert resp.status_code == 200
        assert resp.json()["id"] == lid

    def test_not_found_returns_404(self, client):
        assert client.get("/api/v1/lessons/does-not-exist-999").status_code == 404


# ── Users ──────────────────────────────────────────────────────────────────────

class TestUsers:
    def test_own_profile_returns_200(self, client):
        # MOCK_USER sub is "dev-user-123" — must match the user_id in the path
        resp = client.get("/api/v1/users/dev-user-123")
        assert resp.status_code == 200
        assert resp.json()["user_id"] == "dev-user-123"

    def test_own_profile_has_expected_fields(self, client):
        data = client.get("/api/v1/users/dev-user-123").json()
        for field in ("user_id", "display_name", "skill_level", "plan", "photos_analyzed"):
            assert field in data

    def test_other_user_returns_403(self, client):
        """
        Router checks current_user["sub"] != user_id — any other ID is forbidden.
        NOTE: users.py previously had `and sub != "dev-user-123"` which made this
        branch unreachable. That line is removed in the accompanying users.py fix.
        """
        resp = client.get("/api/v1/users/someone-else-456")
        assert resp.status_code == 403


# ── Headers ────────────────────────────────────────────────────────────────────

class TestHeaders:
    def test_correlation_id_added(self, client):
        assert "x-correlation-id" in client.get("/health").headers

    def test_correlation_id_echoed(self, client):
        resp = client.get("/health", headers={"X-Correlation-ID": "trace-abc"})
        assert resp.headers.get("x-correlation-id") == "trace-abc"


# ── JWKS cache ─────────────────────────────────────────────────────────────────

class TestJWKSCache:
    def test_cache_cleared_after_jwt_error(self):
        """
        When jwt.decode raises JWTError, _jwks_cache must be set to None
        so the next request fetches fresh keys (handles key rotation).

        Must patch settings to non-dev: otherwise get_current_user returns
        the mock user immediately and never reaches the except JWTError block.
        """
        auth_module._jwks_cache = {"keys": [{"kid": "stale"}]}

        mock_request = MagicMock()
        mock_request.headers = {"Authorization": "Bearer any.token.here"}

        with patch.object(auth_module, "settings", _make_settings("prod")):
            with patch.object(auth_module, "get_jwks",
                              new=AsyncMock(return_value={"keys": []})):
                with patch.object(auth_module.jwt, "decode",
                                  side_effect=JWTError("bad token")):
                    try:
                        asyncio.get_event_loop().run_until_complete(
                            auth_module.get_current_user(mock_request)
                        )
                    except Exception:
                        pass

        assert auth_module._jwks_cache is None, (
            "_jwks_cache must be None after JWTError so rotated keys are refetched"
        )
