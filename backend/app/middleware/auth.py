###############################################################################
# middleware/auth.py — Microsoft Entra External ID JWT validation
###############################################################################

import logging
from fastapi import HTTPException, Request, status
from fastapi.security import HTTPBearer
from jose import jwt, JWTError
import httpx

from app.config import settings

logger = logging.getLogger(__name__)
security = HTTPBearer()

# Microsoft Entra External ID JWKS endpoint
# Format: https://<tenant>.ciamlogin.com/<tenant>.onmicrosoft.com/discovery/v2.0/keys
JWKS_URL = (
    f"https://{settings.entra_tenant_id}.ciamlogin.com"
    f"/{settings.entra_tenant_id}/discovery/v2.0/keys"
)

# Expected token issuer
ISSUER = (
    f"https://{settings.entra_tenant_id}.ciamlogin.com"
    f"/{settings.entra_tenant_id}/v2.0"
)

_jwks_cache = None


async def get_jwks():
    """Fetch and cache JWKS from Entra External ID."""
    global _jwks_cache
    if _jwks_cache is None:
        async with httpx.AsyncClient() as client:
            response = await client.get(JWKS_URL)
            response.raise_for_status()
            _jwks_cache = response.json()
    return _jwks_cache


async def get_current_user(request: Request) -> dict:
    """
    Validate Microsoft Entra External ID Bearer token.
    Returns decoded token claims (sub, email, name, etc.)
    In dev mode, accepts a mock token for local testing.
    """
    if settings.environment == "dev":
        # Allow mock auth in dev — never in prod
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
            detail="Missing or invalid authorization header",
            headers={"WWW-Authenticate": "Bearer"},
        )

    token = auth_header.split(" ")[1]

    try:
        jwks = await get_jwks()

        payload = jwt.decode(
            token,
            jwks,
            algorithms=["RS256"],
            audience=settings.entra_api_client_id,   # PixelMentor API client ID
            issuer=ISSUER,
            options={"verify_exp": True},
        )

        # Ensure required claims are present
        if not payload.get("sub"):
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail="Token missing required claims",
            )

        return payload

    except JWTError as e:
        logger.warning(f"JWT validation failed: {e}")
        # Invalidate JWKS cache in case keys rotated
        global _jwks_cache
        _jwks_cache = None
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid or expired token",
            headers={"WWW-Authenticate": "Bearer"},
        )
    except httpx.HTTPError as e:
        logger.error(f"Failed to fetch JWKS: {e}")
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="Authentication service unavailable",
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
