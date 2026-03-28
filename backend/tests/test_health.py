###############################################################################
# tests/test_health.py — PixelMentor backend tests
###############################################################################

import asyncio
import time
import pytest
from unittest.mock import AsyncMock, MagicMock, patch
from fastapi.testclient import TestClient
from jose import JWTError, ExpiredSignatureError

from app.main import app
from app.middleware.auth import get_current_user
import app.middleware.auth as auth_module

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


def _make_settings(environment: str):
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
    """Auth dependency overridden — use for all endpoint logic tests."""
    app.dependency_overrides[get_current_user] = _mock_get_current_user
    with TestClient(app) as c:
        yield c
    app.dependency_overrides.clear()


@pytest.fixture
def client_real_auth():
    """No dependency override, environment forced to prod — real JWT runs."""
    from app.routers.lessons import get_search_client
    app.dependency_overrides.clear()

    mock = MagicMock()
    mock_results = MagicMock()
    mock_results.__iter__ = MagicMock(return_value=iter([]))
    mock_results.get_count = MagicMock(return_value=0)
    mock.search.return_value = mock_results
    app.dependency_overrides[get_search_client] = lambda: mock

    with patch.object(auth_module, "settings", _make_settings("prod")):
        with TestClient(app) as c:
            yield c

    app.dependency_overrides.clear()


@pytest.fixture(autouse=True)
def reset_jwks_cache():
    """Reset JWKS cache state before every test."""
    auth_module._jwks_cache = None
    auth_module._jwks_cached_at = 0.0
    yield
    auth_module._jwks_cache = None
    auth_module._jwks_cached_at = 0.0


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
    def test_missing_header_returns_401(self, client_real_auth):
        resp = client_real_auth.get("/api/v1/lessons/")
        assert resp.status_code == 401
        assert resp.json()["detail"]["error"] == "token_missing"

    def test_expired_token_returns_token_expired(self, client_real_auth):
        """
        Expired token must return error=token_expired so Android knows
        to call acquireTokenSilently() rather than force re-login.
        """
        with patch.object(auth_module, "get_jwks",
                          new=AsyncMock(return_value={"keys": []})):
            with patch.object(auth_module.jwt, "decode",
                              side_effect=ExpiredSignatureError("expired")):
                resp = client_real_auth.get(
                    "/api/v1/lessons/",
                    headers={"Authorization": "Bearer any.token.here"},
                )
        assert resp.status_code == 401
        assert resp.json()["detail"]["error"] == "token_expired"

    def test_invalid_token_returns_token_invalid(self, client_real_auth):
        """
        Bad signature / wrong audience must return error=token_invalid so
        Android knows to force interactive re-login, not waste a refresh attempt.
        """
        with patch.object(auth_module, "get_jwks",
                          new=AsyncMock(return_value={"keys": []})):
            with patch.object(auth_module.jwt, "decode",
                              side_effect=JWTError("bad signature")):
                resp = client_real_auth.get(
                    "/api/v1/lessons/",
                    headers={"Authorization": "Bearer not.a.valid.jwt"},
                )
        assert resp.status_code == 401
        assert resp.json()["detail"]["error"] == "token_invalid"

    def test_malformed_bearer_returns_401(self, client_real_auth):
        with patch.object(auth_module, "get_jwks",
                          new=AsyncMock(return_value={"keys": []})):
            resp = client_real_auth.get(
                "/api/v1/lessons/",
                headers={"Authorization": "Bearer not.a.valid.jwt"},
            )
        assert resp.status_code == 401

    def test_valid_auth_override_returns_200(self, client):
        from app.routers.lessons import get_search_client
        mock = MagicMock()
        mock_results = MagicMock()
        mock_results.__iter__ = MagicMock(return_value=iter([]))
        mock_results.get_count = MagicMock(return_value=0)
        mock.search.return_value = mock_results
        app.dependency_overrides[get_search_client] = lambda: mock
        assert client.get("/api/v1/lessons").status_code == 200
        app.dependency_overrides.pop(get_search_client, None)


# ── Entra ISSUER regression ────────────────────────────────────────────────────

class TestEntraConfig:
    def test_issuer_uses_tenant_id_not_tenant_name(self):
        """Regression: duplicate ISSUER bug used tenant_name as CIAM subdomain."""
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


# ── JWKS cache ─────────────────────────────────────────────────────────────────

class TestJWKSCache:
    def test_cache_populated_on_first_fetch(self):
        assert auth_module._jwks_cache is None
        mock_keys = {"keys": [{"kid": "key1"}]}

        async def _run():
            with patch("httpx.AsyncClient") as mock_client_cls:
                mock_resp = MagicMock()
                mock_resp.json.return_value = mock_keys
                mock_client_cls.return_value.__aenter__.return_value.get = AsyncMock(
                    return_value=mock_resp
                )
                return await auth_module.get_jwks()

        result = asyncio.get_event_loop().run_until_complete(_run())
        assert result == mock_keys
        assert auth_module._jwks_cache == mock_keys

    def test_cache_reused_within_ttl(self):
        auth_module._jwks_cache = {"keys": [{"kid": "cached"}]}
        auth_module._jwks_cached_at = time.monotonic()

        async def _run():
            with patch("httpx.AsyncClient") as mock_client_cls:
                result = await auth_module.get_jwks()
                mock_client_cls.assert_not_called()
                return result

        result = asyncio.get_event_loop().run_until_complete(_run())
        assert result == {"keys": [{"kid": "cached"}]}

    def test_cache_refreshed_after_ttl(self):
        auth_module._jwks_cache = {"keys": [{"kid": "stale"}]}
        auth_module._jwks_cached_at = time.monotonic() - auth_module.JWKS_CACHE_TTL_SECONDS - 1

        fresh_keys = {"keys": [{"kid": "fresh"}]}

        async def _run():
            with patch("httpx.AsyncClient") as mock_client_cls:
                mock_resp = MagicMock()
                mock_resp.json.return_value = fresh_keys
                mock_client_cls.return_value.__aenter__.return_value.get = AsyncMock(
                    return_value=mock_resp
                )
                return await auth_module.get_jwks()

        result = asyncio.get_event_loop().run_until_complete(_run())
        assert result == fresh_keys

    def test_cache_cleared_on_expired_token(self):
        """ExpiredSignatureError must clear the cache."""
        auth_module._jwks_cache = {"keys": [{"kid": "stale"}]}
        auth_module._jwks_cached_at = time.monotonic()

        mock_request = MagicMock()
        mock_request.headers = {"Authorization": "Bearer any.token.here"}

        with patch.object(auth_module, "settings", _make_settings("prod")):
            with patch.object(auth_module, "get_jwks",
                              new=AsyncMock(return_value={"keys": []})):
                with patch.object(auth_module.jwt, "decode",
                                  side_effect=ExpiredSignatureError("expired")):
                    try:
                        asyncio.get_event_loop().run_until_complete(
                            auth_module.get_current_user(mock_request)
                        )
                    except Exception:
                        pass

        assert auth_module._jwks_cache is None

    def test_cache_cleared_on_jwt_error(self):
        """JWTError must also clear the cache (handles emergency key rotation)."""
        auth_module._jwks_cache = {"keys": [{"kid": "stale"}]}
        auth_module._jwks_cached_at = time.monotonic()

        mock_request = MagicMock()
        mock_request.headers = {"Authorization": "Bearer any.token.here"}

        with patch.object(auth_module, "settings", _make_settings("prod")):
            with patch.object(auth_module, "get_jwks",
                              new=AsyncMock(return_value={"keys": []})):
                with patch.object(auth_module.jwt, "decode",
                                  side_effect=JWTError("bad sig")):
                    try:
                        asyncio.get_event_loop().run_until_complete(
                            auth_module.get_current_user(mock_request)
                        )
                    except Exception:
                        pass

        assert auth_module._jwks_cache is None


# ── Lessons ────────────────────────────────────────────────────────────────────

class TestLessons:
    def test_list_returns_200_and_list(self, client):
        from app.routers.lessons import get_search_client
        mock = MagicMock()
        mock_results = MagicMock()
        mock_results.__iter__ = MagicMock(return_value=iter([
            {"id": "lesson-001", "title": "Test", "description": "Desc",
             "category": "fundamentals", "difficulty": "beginner",
             "duration_minutes": 15, "is_pro": False, "order": 1, "tags": []}
        ]))
        mock_results.get_count = MagicMock(return_value=1)
        mock.search.return_value = mock_results
        app.dependency_overrides[get_search_client] = lambda: mock

        resp = client.get("/api/v1/lessons")
        assert resp.status_code == 200
        assert isinstance(resp.json()["lessons"], list)
        app.dependency_overrides.pop(get_search_client, None)

    def test_list_items_have_required_fields(self, client):
        from app.routers.lessons import get_search_client
        mock = MagicMock()
        mock_results = MagicMock()
        mock_results.__iter__ = MagicMock(return_value=iter([
            {"id": "lesson-001", "title": "Test", "description": "Desc",
             "category": "fundamentals", "difficulty": "beginner",
             "duration_minutes": 15, "is_pro": False, "order": 1, "tags": []}
        ]))
        mock_results.get_count = MagicMock(return_value=1)
        mock.search.return_value = mock_results
        app.dependency_overrides[get_search_client] = lambda: mock

        lessons = client.get("/api/v1/lessons").json()["lessons"]
        assert len(lessons) > 0
        for lesson in lessons:
            assert {"id", "title", "difficulty"} <= lesson.keys()
        app.dependency_overrides.pop(get_search_client, None)

    def test_filter_by_skill_level(self, client):
        from app.routers.lessons import get_search_client
        mock = MagicMock()
        mock_results = MagicMock()
        mock_results.__iter__ = MagicMock(return_value=iter([]))
        mock_results.get_count = MagicMock(return_value=0)
        mock.search.return_value = mock_results
        app.dependency_overrides[get_search_client] = lambda: mock

        resp = client.get("/api/v1/lessons?difficulty=beginner")
        assert resp.status_code == 200
        app.dependency_overrides.pop(get_search_client, None)

    def test_get_by_id(self, client):
        from app.routers.lessons import get_search_client
        mock = MagicMock()
        mock.get_document.return_value = {
            "id": "lesson-001", "title": "Test", "description": "Desc",
            "category": "fundamentals", "difficulty": "beginner",
            "duration_minutes": 15, "is_pro": False, "order": 1,
            "tags": [], "content": "Full content."
        }
        app.dependency_overrides[get_search_client] = lambda: mock

        resp = client.get("/api/v1/lessons/lesson-001")
        assert resp.status_code == 200
        assert resp.json()["id"] == "lesson-001"
        app.dependency_overrides.pop(get_search_client, None)

    def test_not_found_returns_404(self, client):
        from azure.core.exceptions import HttpResponseError
        from app.routers.lessons import get_search_client
        mock = MagicMock()
        err = HttpResponseError(message="Not found")
        err.status_code = 404
        mock.get_document.side_effect = err
        app.dependency_overrides[get_search_client] = lambda: mock

        assert client.get("/api/v1/lessons/does-not-exist-999").status_code == 404
        app.dependency_overrides.pop(get_search_client, None)


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
