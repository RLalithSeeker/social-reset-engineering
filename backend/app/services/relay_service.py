import asyncio
import logging
import threading
from datetime import datetime, timedelta, timezone

from fastapi import HTTPException
from sqlmodel import Session, select

from app.config import get_settings
from app.logging import redact_device
from app.models import RelayedEvent, ResetSession

logger = logging.getLogger("socialreset")


def _now() -> datetime:
    return datetime.now(timezone.utc).replace(tzinfo=None)


# ---------------- Reset sessions ----------------

def create_reset_session(
    session: Session,
    *,
    session_id: str,
    request_id: str,
    requester_device_id: str,
    peer_device_id: str,
    blocked_package: str,
    reset_type: str,
    ttl_seconds: int | None = None,
) -> ResetSession:
    existing = session.exec(
        select(ResetSession).where(ResetSession.request_id == request_id)
    ).first()
    if existing:
        return existing

    settings = get_settings()
    ttl = ttl_seconds or settings.reset_session_ttl_seconds
    reset = ResetSession(
        session_id=session_id,
        request_id=request_id,
        requester_device_id=requester_device_id,
        peer_device_id=peer_device_id,
        blocked_package=blocked_package,
        reset_type=reset_type,
        state="ACTIVE",
        expires_at=_now() + timedelta(seconds=ttl),
    )
    session.add(reset)
    session.commit()
    session.refresh(reset)
    return reset


def get_reset_session(session: Session, session_id: str) -> ResetSession | None:
    return session.exec(
        select(ResetSession).where(ResetSession.session_id == session_id)
    ).first()


# ---------------- Event relay ----------------

REQUIRED_EVENT_FIELDS = {
    "protocolVersion",
    "eventId",
    "sessionId",
    "senderDeviceId",
    "recipientDeviceId",
    "sequence",
    "type",
    "timestamp",
    "nonce",
    "payload",
    "signature",
}


def validate_relay_event_structure(payload: dict, *, max_bytes: int) -> None:
    missing = REQUIRED_EVENT_FIELDS - set(payload)
    if missing:
        raise HTTPException(status_code=400, detail=f"missing fields: {sorted(missing)}")

    raw = _estimate_raw_size(payload)
    if raw > max_bytes:
        raise HTTPException(status_code=413, detail="payload too large")


def _estimate_raw_size(payload: dict) -> int:
    import json

    return len(json.dumps(payload, separators=(",", ":")).encode("utf-8"))


def relay_event(
    db_session: Session,
    *,
    reset: ResetSession,
    payload: dict,
    raw_bytes: bytes,
) -> RelayedEvent:
    settings = get_settings()
    if reset.expires_at < _now():
        raise HTTPException(status_code=410, detail="session expired")

    protocol_version = payload.get("protocolVersion")
    if protocol_version != 1:
        raise HTTPException(status_code=400, detail="unsupported protocolVersion")

    participants = {reset.requester_device_id, reset.peer_device_id}
    sender = payload["senderDeviceId"]
    recipient = payload["recipientDeviceId"]
    if sender not in participants:
        raise HTTPException(status_code=403, detail="sender is not a participant")
    if recipient not in participants:
        raise HTTPException(status_code=403, detail="recipient is not a participant")

    existing = db_session.exec(
        select(RelayedEvent).where(RelayedEvent.event_id == payload["eventId"])
    ).first()
    if existing:
        logger.warning(
            "relay_duplicate event=%s sender=%s session=%s",
            payload["eventId"][:8],
            redact_device(sender),
            payload["sessionId"][:8],
        )
        return existing

    relayed = RelayedEvent(
        event_id=payload["eventId"],
        session_id=payload["sessionId"],
        sender_device_id=sender,
        recipient_device_id=recipient,
        protocol_version=protocol_version,
        type=payload["type"],
        created_at=_now(),
        expires_at=_now() + timedelta(seconds=settings.event_ttl_seconds),
        raw_payload=raw_bytes,
    )
    db_session.add(relayed)
    db_session.commit()
    db_session.refresh(relayed)

    logger.info(
        "relay type=%s event=%s sender=%s session=%s",
        payload["type"],
        payload["eventId"][:8],
        redact_device(sender),
        payload["sessionId"][:8],
    )
    return relayed


# ---------------- WebSocket fan-out ----------------
# The server relays the client's exact bytes: it never re-serializes or
# inspects signatures. Fan-out publishes the raw body to every live
# subscriber connection of the recipient device via a per-connection outbox.

class _WsFanout:
    def __init__(self) -> None:
        self._subscribers: dict[str, set[asyncio.Queue]] = {}
        self._lock = threading.Lock()

    def subscribe(self, device_id: str, outbox: asyncio.Queue) -> int:
        with self._lock:
            subs = self._subscribers.setdefault(device_id, set())
            subs.add(outbox)
            return len(subs)

    def unsubscribe(self, device_id: str, outbox: asyncio.Queue) -> None:
        with self._lock:
            subs = self._subscribers.get(device_id)
            if subs:
                subs.discard(outbox)
                if not subs:
                    self._subscribers.pop(device_id, None)

    def connection_count(self, device_id: str) -> int:
        with self._lock:
            return len(self._subscribers.get(device_id, set()))

    async def publish(self, device_id: str, raw_bytes: bytes) -> int:
        with self._lock:
            targets = list(self._subscribers.get(device_id, set()))
        for outbox in targets:
            outbox.put_nowait(raw_bytes)
        return len(targets)


ws_fanout = _WsFanout()