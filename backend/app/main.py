import asyncio
import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI, Request, Response, status
from starlette.middleware import Middleware
from starlette.middleware.cors import CORSMiddleware

from app.config import get_settings
from app.db import create_all, init_engine
from app.logging import setup_logging
from app.routers import devices, pairing, sessions, ws
from app.services.cleanup import cleanup_loop

logger = logging.getLogger("socialreset")


class MaxBodySizeMiddleware:
    """Reject requests whose Content-Length exceeds the configured cap (413)."""

    def __init__(self, app, max_bytes: int) -> None:
        self.app = app
        self.max_bytes = max_bytes

    async def __call__(self, scope, receive, send) -> None:
        if scope["type"] != "http":
            await self.app(scope, receive, send)
            return

        headers = dict(scope.get("headers", []))
        content_length = headers.get(b"content-length")
        if content_length is not None:
            try:
                length = int(content_length)
            except (TypeError, ValueError):
                length = 0
            if length > self.max_bytes:
                response = Response(
                    content=b'{"detail":"request body too large"}',
                    status_code=status.HTTP_413_REQUEST_ENTITY_TOO_LARGE,
                    media_type="application/json",
                )
                await response(scope, receive, send)
                return

        await self.app(scope, receive, send)


@asynccontextmanager
async def lifespan(app: FastAPI):
    settings = get_settings()
    init_engine()
    create_all()
    cleanup_task = None
    if settings.cleanup_interval_seconds > 0:
        cleanup_task = asyncio.create_task(cleanup_loop())
    yield
    if cleanup_task:
        cleanup_task.cancel()
        try:
            await cleanup_task
        except asyncio.CancelledError:
            pass


def create_app() -> FastAPI:
    setup_logging(get_settings().log_level)
    settings = get_settings()

    allowed_origins = [
        origin.strip()
        for origin in settings.cors_allowed_origins.split(",")
        if origin.strip()
    ]

    app = FastAPI(
        title="Social Reset Relay",
        version="1.0.0",
        lifespan=lifespan,
        middleware=[
            Middleware(CORSMiddleware, allow_origins=allowed_origins, allow_methods=["*"], allow_headers=["*"]),
        ],
    )
    app.add_middleware(MaxBodySizeMiddleware, max_bytes=settings.max_request_bytes)

    app.include_router(devices.router)
    app.include_router(pairing.router)
    app.include_router(sessions.router)
    app.include_router(ws.router)
    return app


app = create_app()