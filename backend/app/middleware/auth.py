###############################################################################
# middleware/auth.py — Supabase JWT validation
# Replaces Microsoft Entra External ID
###############################################################################

import logging
from fastapi import HTTPException, Request, status
from jose import jwt, JWTError, ExpiredSignatureError

from app.config import settings

logger = logging.getLogger(__name__)


async def get_current_user(request: Request) -> dict:
    """
    Validate Supabase JWT token.
    Returns decoded token claims (sub, email, etc.)

    401 error codes:
      token_missing  → no Authorization header
      token_expired  → valid token, past exp
      token_invalid  → bad signature / malformed
    """
    if settings.environment == "dev":
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
        payload = jwt.decode(
            token,
            settings.supabase_jwt_secret,
            algorithms=["HS256"],
            audience="authenticated",
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
        logger.warning(f"Invalid token rejected: {e}")
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail={"error": "token_invalid", "message": "Token is invalid"},
            headers={"WWW-Authenticate": "Bearer"},
        )