###############################################################################
# PixelMentor Backend — main.py
###############################################################################

import logging
import uuid
from contextlib import asynccontextmanager

from fastapi import FastAPI, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.middleware.trustedhost import TrustedHostMiddleware
from fastapi.responses import JSONResponse

from app.config import settings
from app.middleware.rate_limiter import RateLimitMiddleware
from app.routers import analyze, tutor, lessons, users, health

# ── Logging ───────────────────────────────────────────────────────────────────
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# Setup Azure Monitor if connection string is available
if settings.app_insights_connection_string:
    try:
        from azure.monitor.opentelemetry import configure_azure_monitor
        configure_azure_monitor(
            connection_string=settings.app_insights_connection_string
        )
        logger.info("Azure Monitor configured")
    except Exception as e:
        logger.warning(f"Azure Monitor setup failed: {e}")


# ── App Lifecycle ─────────────────────────────────────────────────────────────
@asynccontextmanager
async def lifespan(app: FastAPI):
    logger.info(f"PixelMentor API starting — environment: {settings.environment}")
    yield
    logger.info("PixelMentor API shutting down")


# ── FastAPI App ───────────────────────────────────────────────────────────────
app = FastAPI(
    title="PixelMentor API",
    description="AI-powered photography education backend",
    version="1.0.0",
    docs_url="/docs" if settings.environment != "prod" else None,
    redoc_url="/redoc" if settings.environment != "prod" else None,
    lifespan=lifespan,
)

# ── Middleware ────────────────────────────────────────────────────────────────
app.add_middleware(
    TrustedHostMiddleware,
    allowed_hosts=settings.allowed_hosts,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.allowed_origins,
    allow_credentials=True,
    allow_methods=["GET", "POST", "PUT", "DELETE"],
    allow_headers=["Authorization", "Content-Type", "X-Correlation-ID"],
)

app.add_middleware(RateLimitMiddleware)


# ── Correlation ID ────────────────────────────────────────────────────────────
@app.middleware("http")
async def add_correlation_id(request: Request, call_next):
    correlation_id = request.headers.get("X-Correlation-ID", str(uuid.uuid4()))
    request.state.correlation_id = correlation_id
    response = await call_next(request)
    response.headers["X-Correlation-ID"] = correlation_id
    return response


# ── Global Exception Handler ──────────────────────────────────────────────────
@app.exception_handler(Exception)
async def global_exception_handler(request: Request, exc: Exception):
    logger.error(f"Unhandled exception: {exc}", exc_info=True)
    return JSONResponse(
        status_code=500,
        content={
            "detail": "An internal error occurred.",
            "correlation_id": getattr(request.state, "correlation_id", "unknown"),
        },
    )


# ── Routers ───────────────────────────────────────────────────────────────────
app.include_router(health.router,   tags=["Health"])
app.include_router(analyze.router,  prefix="/api/v1/analyze",  tags=["Photo Analysis"])
app.include_router(tutor.router,    prefix="/api/v1/tutor",    tags=["AI Tutor"])
app.include_router(lessons.router,  prefix="/api/v1/lessons",  tags=["Lessons"])
app.include_router(users.router,    prefix="/api/v1/users",    tags=["Users"])
