import os
import uuid

os.environ.setdefault("SR_SERVER_SECRET", "test-secret-not-for-production")
os.environ.setdefault("SR_DATABASE_URL", "sqlite:///./test_socialreset.db")
os.environ.setdefault("SR_CORS_ALLOWED_ORIGINS", "")
os.environ.setdefault("SR_CLEANUP_INTERVAL_SECONDS", "0")
os.environ.setdefault("SR_WS_IDLE_TIMEOUT_SECONDS", "2")
os.environ.setdefault("SR_JWT_TTL_SECONDS", "300")
os.environ.setdefault("SR_EVENT_TTL_SECONDS", "86400")

import pytest
import pytest_asyncio
from httpx import ASGITransport, AsyncClient

from app.db import create_all, get_engine
from app.main import app
from app.models import Device, PairingSession, RelayedEvent, ResetSession
from app.security import RateLimiter


@pytest_asyncio.fixture
async def client():
    async with AsyncClient(
        transport=ASGITransport(app=app),
        base_url="http://testserver",
    ) as c:
        yield c


@pytest.fixture(autouse=True)
def _clean_state():
    engine = get_engine()
    create_all()
    with engine.connect() as conn:
        for model in (RelayedEvent, ResetSession, PairingSession, Device):
            conn.execute(model.__table__.delete())
        conn.commit()
    RateLimiter._buckets.clear()
    yield


async def register_device(client: AsyncClient, device_id: str | None = None, key: str | None = None) -> tuple[str, str]:
    device_id = device_id or f"dev-{uuid.uuid4()}"
    key = key or f"key-{uuid.uuid4()}"
    resp = await client.post(
        "/v1/devices/register",
        json={
            "deviceId": device_id,
            "protocolVersion": 1,
            "displayName": "test-device",
            "signingPublicKey": key,
        },
    )
    assert resp.status_code == 200
    return device_id, resp.json()["accessToken"]


def register_device_sync(
    client, device_id: str | None = None, key: str | None = None
) -> tuple[str, str]:
    device_id = device_id or f"dev-{uuid.uuid4()}"
    key = key or f"key-{uuid.uuid4()}"
    resp = client.post(
        "/v1/devices/register",
        json={
            "deviceId": device_id,
            "protocolVersion": 1,
            "displayName": "test-device",
            "signingPublicKey": key,
        },
    )
    assert resp.status_code == 200
    return device_id, resp.json()["accessToken"]


def auth_headers(token: str) -> dict:
    return {"Authorization": f"Bearer {token}"}


def event_envelope(
    session_id: str,
    sender: str,
    recipient: str,
    *,
    event_id: str | None = None,
    payload: dict | None = None,
) -> dict:
    return {
        "protocolVersion": 1,
        "eventId": event_id or f"evt-{uuid.uuid4()}",
        "sessionId": session_id,
        "senderDeviceId": sender,
        "recipientDeviceId": recipient,
        "sequence": 1,
        "type": "RESET_ACCEPTED",
        "timestamp": 1770000000,
        "nonce": "abc123",
        "payload": payload or {"ok": True},
        "signature": "c2lnbmF0dXJl",
    }