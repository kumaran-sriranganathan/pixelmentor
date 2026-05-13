###############################################################################
# middleware/auth.py — Supabase JWT validation (ES256 / ECC P-256)
# Fetches public keys dynamically from Supabase JWKS endpoint at startup.
# Keys are cached in memory — restart to pick up rotated keys.
###############################################################################

import logging
import time
import httpx
from fastapi import HTTPException, Request, status
from jose import jwt, JWTError, ExpiredSignatureError

from app.config import settings

logger = logging.getLogger(__name__)

# ── JWKS cache with 24-hour TTL ───────────────────────────────────────────────
# Keys are refreshed automatically so key rotations don't require a redeploy.

_jwks_cache: list[dict] | None = None
_jwks_fetched_at: float = 0.0
_JWKS_TTL_SECONDS = 86400  # 24 hours


async def _get_jwks() -> list[dict]:
    """Fetch and cache JWKS keys from Supabase. Refreshes every 24 hours."""
    global _jwks_cache, _jwks_fetched_at

    now = time.monotonic()
    if _jwks_cache is not None and (now - _jwks_fetched_at) < _JWKS_TTL_SECONDS:
        return _jwks_cache

    jwks_url = f"{settings.supabase_url}/auth/v1/.well-known/jwks.json"
    logger.info(f"Fetching JWKS from {jwks_url} (cache {'refresh' if _jwks_cache else 'init'})")

    try:
        async with httpx.AsyncClient() as client:
            response = await client.get(jwks_url, timeout=10.0)
            response.raise_for_status()
            data = response.json()
            _jwks_cache = data.get("keys", [])
            _jwks_fetched_at = now
            logger.info(f"JWKS loaded: {len(_jwks_cache)} key(s)")
            return _jwks_cache
    except Exception as e:
        logger.error(f"Failed to fetch JWKS: {e}")
        # If we have a stale cache, use it rather than failing completely
        if _jwks_cache:
            logger.warning("Using stale JWKS cache due to fetch failure")
            return _jwks_cache
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="Authentication service unavailable"
        )


# ── Auth middleware ───────────────────────────────────────────────────────────

async def get_current_user(request: Request) -> dict:
    """
    Validate Supabase JWT token using dynamically fetched JWKS.
    Returns decoded token claims (sub, email, etc.)

    401 error codes:
      token_missing  → no Authorization header
      token_expired  → valid token, past exp
      token_invalid  → bad signature / malformed
    """
    auth_header = request.headers.get("Authorization")
    if not auth_header or not auth_header.startswith("Bearer "):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail={"error": "token_missing", "message": "Authorization header required"},
            headers={"WWW-Authenticate": "Bearer"},
        )

    token = auth_header.split(" ")[1]
    keys = await _get_jwks()

    # Try each key in the JWKS until one works
    last_error = None
    for jwk in keys:
        try:
            payload = jwt.decode(
                token,
                jwk,
                algorithms=[jwk.get("alg", "ES256")],
                audience="authenticated",
                options={"verify_aud": True},
            )

            if not payload.get("sub"):
                raise HTTPException(
                    status_code=status.HTTP_401_UNAUTHORIZED,
                    detail={"error": "token_invalid", "message": "Token missing required claims"},
                )

            return payload

        except ExpiredSignatureError:
            logger.info("Expired token rejected")
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail={"error": "token_expired", "message": "Token has expired — refresh and retry"},
                headers={"WWW-Authenticate": "Bearer"},
            )

        except JWTError as e:
            last_error = e
            continue

    logger.warning(f"Invalid token rejected: {last_error}")
    raise HTTPException(
        status_code=status.HTTP_401_UNAUTHORIZED,
        detail={"error": "token_invalid", "message": "Token is invalid"},
        headers={"WWW-Authenticate": "Bearer"},
    )
