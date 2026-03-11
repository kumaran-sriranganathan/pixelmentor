###############################################################################
# middleware/auth.py — Azure AD B2C JWT validation
###############################################################################

import logging
from fastapi import HTTPException, Request, status
from fastapi.security import HTTPBearer, HTTPAuthorizationCredentials
from jose import jwt, JWTError
import httpx

from app.config import settings

logger = logging.getLogger(__name__)
security = HTTPBearer()

JWKS_URL = (
    f"https://login.microsoftonline.com/{settings.azure_tenant_id}"
    f"/discovery/v2.0/keys"
)

_jwks_cache = None


async def get_jwks():
    global _jwks_cache
    if _jwks_cache is None:
        async with httpx.AsyncClient() as client:
            response = await client.get(JWKS_URL)
            _jwks_cache = response.json()
    return _jwks_cache


async def get_current_user(request: Request) -> dict:
    """
    Validate Azure AD Bearer token.
    Returns decoded token claims (sub, email, name, etc.)
    In dev mode, accepts a mock token for local testing.
    """
    if settings.environment == "dev":
        # Allow mock auth in dev — never in prod
        return {"sub": "dev-user-123", "email": "dev@pixelmentor.com", "name": "Dev User"}

    auth_header = request.headers.get("Authorization")
    if not auth_header or not auth_header.startswith("Bearer "):
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Missing auth token")

    token = auth_header.split(" ")[1]

    try:
        jwks = await get_jwks()
        payload = jwt.decode(
            token,
            jwks,
            algorithms=["RS256"],
            audience=settings.azure_client_id,
        )
        return payload
    except JWTError as e:
        logger.warning(f"JWT validation failed: {e}")
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid token")


class AzureADAuthMiddleware:
    """
    Middleware that skips auth on public routes (/health, /docs).
    All other routes require a valid Azure AD token.
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
