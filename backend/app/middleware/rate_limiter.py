###############################################################################
# middleware/rate_limiter.py — In-memory rate limiting (Redis in prod)
###############################################################################

import time
import logging
from collections import defaultdict
from fastapi import Request
from fastapi.responses import JSONResponse

from app.config import settings

logger = logging.getLogger(__name__)

# Per-endpoint rate limits
RATE_LIMITS = {
    "/api/v1/analyze": settings.rate_limit_ai_per_minute,   # Stricter for AI
    "/api/v1/tutor":   settings.rate_limit_ai_per_minute,
    "default":         settings.rate_limit_per_minute,
}

# In-memory store — replace with Azure Cache for Redis in prod
_request_counts: dict = defaultdict(list)


class RateLimitMiddleware:
    def __init__(self, app):
        self.app = app

    async def __call__(self, scope, receive, send):
        if scope["type"] != "http":
            await self.app(scope, receive, send)
            return

        request = Request(scope, receive)
        client_ip = request.client.host if request.client else "unknown"
        path = scope.get("path", "")

        # Determine limit for this path
        limit = RATE_LIMITS.get("default", 60)
        for prefix, rate in RATE_LIMITS.items():
            if path.startswith(prefix):
                limit = rate
                break

        key = f"{client_ip}:{path}"
        now = time.time()
        window_start = now - 60  # 1 minute window

        # Clean old requests
        _request_counts[key] = [t for t in _request_counts[key] if t > window_start]

        if len(_request_counts[key]) >= limit:
            logger.warning(f"Rate limit exceeded for {client_ip} on {path}")
            response = JSONResponse(
                status_code=429,
                content={"detail": "Too many requests. Please slow down."},
                headers={"Retry-After": "60"},
            )
            await response(scope, receive, send)
            return

        _request_counts[key].append(now)
        await self.app(scope, receive, send)
