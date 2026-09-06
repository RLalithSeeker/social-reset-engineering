from datetime import datetime, timedelta, timezone

from sqlmodel import Session, select

from app.db import get_engine
from app.models import PairingSession, RelayedEvent, ResetSession
from app.services.cleanup import run_cleanup


def _old() -> datetime:
    return datetime.now(timezone.utc).replace(tzinfo=None) - timedelta(days=3)


def test_cleanup_removes_expired_pairings_sessions_and_relay_payloads() -> None:
    old = _old()
    with Session(get_engine()) as session:
        session.add(
            PairingSession(
                session_id="pair-expired",
                code_hash="hash",
                status="open",
                created_at=old,
                expires_at=old,
            )
        )
        session.add(
            ResetSession(
                session_id="reset-expired",
                request_id="req-expired",
                requester_device_id="dev-a",
                peer_device_id="dev-b",
                blocked_package="com.example.app",
                reset_type="FRIEND_ACCEPTED",
                created_at=old,
                expires_at=old,
                state="ACTIVE",
            )
        )
        session.add(
            RelayedEvent(
                event_id="evt-expired",
                session_id="reset-expired",
                sender_device_id="dev-a",
                recipient_device_id="dev-b",
                protocol_version=1,
                type="RESET_ACCEPTED",
                created_at=old,
                expires_at=old,
                raw_payload=b'{"deviceId":"dev-a","token":"sensitive"}',
            )
        )
        session.commit()

    assert run_cleanup() == 3

    with Session(get_engine()) as session:
        assert session.exec(select(PairingSession)).all() == []
        assert session.exec(select(ResetSession)).all() == []
        assert session.exec(select(RelayedEvent)).all() == []
