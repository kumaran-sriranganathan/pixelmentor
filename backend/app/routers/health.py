###############################################################################
# routers/health.py — Liveness + readiness probes for Container Apps
###############################################################################

from fastapi import APIRouter
from app.config import settings

router = APIRouter()


@router.get("/health")
async def liveness():
    """Liveness probe — is the app running?"""
    return {"status": "alive", "environment": settings.environment}


@router.get("/health/ready")
async def readiness():
    """
    Readiness probe — are all dependencies reachable?
    Container Apps won't route traffic until this returns 200.
    """
    checks = {"api": "ok"}

    # Add lightweight dependency checks here if needed
    # e.g. ping Cosmos, check OpenAI endpoint reachable

    all_ok = all(v == "ok" for v in checks.values())
    return {
        "status": "ready" if all_ok else "degraded",
        "checks": checks,
    }
