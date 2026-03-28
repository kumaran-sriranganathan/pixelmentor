###############################################################################
# middleware/auth.py — Microsoft Entra External ID JWT validation
###############################################################################

import logging
import time
from fastapi import HTTPException, Request, status
from fastapi.security import HTTPBearer
from jose import jwt, JWTError, ExpiredSignatureError
import httpx

from app.config import settings

logger = logging.getLogger(__name__)
security = HTTPBearer(auto_error=False)

# Microsoft Entra External ID JWKS endpoint
JWKS_URL = (
    f"https://{settings.entra_tenant_id}.ciamlogin.com"
    f"/{settings.entra_tenant_id}/discovery/v2.0/keys"
)

# Expected token issuer
# Entra External ID (CIAM) format: https://<tenant-id>.ciamlogin.com/<tenant-id>/v2.0
ISSUER = (
    f"https://{settings.entra_tenant_id}.ciamlogin.com"
    f"/{settings.entra_tenant_id}/v2.0"
)

# JWKS cache — refreshed automatically every 24 hours so key rotations
# are picked up without needing a failed request to trigger it.
JWKS_CACHE_TTL_SECONDS = 86400  # 24 hours

_jwks_cache: dict | None = None
_jwks_cached_at: float = 0.0


async def get_jwks() -> dict:
    """
    Fetch and cache JWKS from Entra External ID.
    Cache expires after 24h (normal rotation) or immediately on JWTError
    (emergency rotation — bad token triggers a fresh fetch on next request).
    """
    global _jwks_cache, _jwks_cached_at

    age = time.monotonic() - _jwks_cached_at
    if _jwks_cache is None or age > JWKS_CACHE_TTL_SECONDS:
        async with httpx.AsyncClient() as client:
            response = await client.get(JWKS_URL)
            response.raise_for_status()
            _jwks_cache = response.json()
            _jwks_cached_at = time.monotonic()
            logger.info("JWKS cache refreshed")

    return _jwks_cache


def _clear_jwks_cache() -> None:
    global _jwks_cache, _jwks_cached_at
    _jwks_cache = None
    _jwks_cached_at = 0.0


async def get_current_user(request: Request) -> dict:
    """
    Validate Microsoft Entra External ID Bearer token.
    Returns decoded token claims (sub, email, name, etc.)

    401 error codes (keyed by Android MSAL client):
      token_missing  → no Authorization header — prompt login
      token_expired  → valid token, past exp — silent refresh via acquireTokenSilently()
      token_invalid  → bad signature / wrong audience / malformed — force re-login
    """
    if settings.environment == "dev":
        # Bypass JWT validation in dev — never runs in prod
        return {
            "sub": "dev-user-123",
            "email": "dev@pixelmentor.com",
            "name": "Dev User",
            "given_name": "Dev",
            "family_name": "User",
        }

    auth_header = request.headers.get("Authorization")
    if not auth_header or not auth_header.startswith("Bearer "):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail={"error": "token_missing", "message": "Authorization header required"},
            headers={"WWW-Authenticate": "Bearer"},
        )

    token = auth_header.split(" ")[1]

    try:
        jwks = await get_jwks()

        payload = jwt.decode(
            token,
            jwks,
            algorithms=["RS256"],
            audience=settings.entra_api_client_id,
            issuer=ISSUER,
            options={"verify_exp": True},
        )

        if not payload.get("sub"):
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail={"error": "token_invalid", "message": "Token missing required claims"},
            )

        return payload

    except ExpiredSignatureError:
        # Structurally valid token but past its exp claim.
        # Android: call acquireTokenSilently(), then retry the request.
        logger.info("Expired token rejected")
        _clear_jwks_cache()
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail={"error": "token_expired", "message": "Token has expired — refresh and retry"},
            headers={"WWW-Authenticate": "Bearer"},
        )

    except JWTError as e:
        # Bad signature, wrong audience/issuer, malformed — not recoverable by refresh.
        # Android: force interactive re-login.
        logger.warning(f"Invalid token rejected: {e}")
        _clear_jwks_cache()
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail={"error": "token_invalid", "message": "Token is invalid"},
            headers={"WWW-Authenticate": "Bearer"},
        )

    except httpx.HTTPError as e:
        logger.error(f"Failed to fetch JWKS: {e}")
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail={"error": "auth_unavailable", "message": "Authentication service unavailable"},
        )


class EntraExternalIDAuthMiddleware:
    """
    Middleware that skips auth on public routes (/health, /docs).
    All other routes require a valid Entra External ID token.
    """
    PUBLIC_PATHS = {"/health", "/health/ready", "/docs", "/redoc", "/openapi.json"}

    def __init__(self, app):
        self.app = app

    async def __call__(self, scope, receive, send):
        if scope["type"] == "http":
            path = scope.get("path", "")
            if path not in self.PUBLIC_PATHS and not path.startswith("/docs"):
                pass  # Token validated per-endpoint via Depends(get_current_user)
        await self.app(scope, receive, send)


# Keep old name as alias for backwards compatibility
AzureADAuthMiddleware = EntraExternalIDAuthMiddleware
