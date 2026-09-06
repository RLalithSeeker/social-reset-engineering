import hashlib
import hmac
import secrets
import uuid
from datetime import datetime, timedelta, timezone

from fastapi import HTTPException
from sqlmodel import Session, select

from app.config import get_settings
from app.models import PairingSession


def _now() -> datetime:
    return datetime.now(timezone.utc).replace(tzinfo=None)


def _hash_code(code: str) -> str:
    return hashlib.sha256(code.encode("utf-8")).hexdigest()


def create_pairing_session(
    session: Session,
    *,
    creator_device_id: str | None = None,
    creator_ephemeral_public_key: str | None = None,
    request_id: str | None = None,
) -> tuple[PairingSession, str | None]:
    """Create a pairing session, returning (row, plaintext_code).

    plaintext_code is only non-None for a newly created session. If an
    existing session is returned (idempotent requestId), the code is unknown.
    """
    settings = get_settings()
    if request_id:
        existing = session.exec(
            select(PairingSession).where(PairingSession.request_id == request_id)
        ).first()
        if existing:
            return existing, None

    code = secrets.token_urlsafe(12)
    pairing = PairingSession(
        session_id=str(uuid.uuid4()),
        code_hash=_hash_code(code),
        creator_device_id=creator_device_id,
        creator_ephemeral_public_key=creator_ephemeral_public_key,
        request_id=request_id,
        status="open",
        expires_at=_now() + timedelta(seconds=settings.pairing_ttl_seconds),
    )
    session.add(pairing)
    session.commit()
    session.refresh(pairing)
    return pairing, code


def get_pairing_session(session: Session, session_id: str) -> PairingSession | None:
    return session.exec(
        select(PairingSession).where(PairingSession.session_id == session_id)
    ).first()


def join_pairing_session(
    session: Session,
    pairing: PairingSession,
    *,
    code: str,
    device_id: str,
    signing_public_key: str,
    ephemeral_public_key: str,
    protocol_version: int,
) -> None:
    now = _now()
    if pairing.expires_at < now:
        raise HTTPException(status_code=410, detail="pairing code expired")

    if pairing.status != "open" or pairing.peer_device_id is not None:
        raise HTTPException(status_code=409, detail="pairing code already used")

    if not hmac.compare_digest(pairing.code_hash.encode(), _hash_code(code).encode()):
        raise HTTPException(status_code=403, detail="invalid pairing code")

    pairing.peer_device_id = device_id
    pairing.peer_signing_public_key = signing_public_key
    pairing.peer_ephemeral_public_key = ephemeral_public_key
    pairing.status = "joined"
    pairing.joined_at = now
    session.add(pairing)
    session.commit()


def complete_pairing_session(session: Session, pairing: PairingSession) -> None:
    now = _now()
    if pairing.expires_at < now:
        raise HTTPException(status_code=410, detail="pairing session expired")

    pairing.status = "completed"
    pairing.completed_at = now
    session.add(pairing)
    session.commit()
