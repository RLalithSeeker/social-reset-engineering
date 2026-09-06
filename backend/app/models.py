import uuid
from datetime import datetime, timezone

from sqlmodel import Field, SQLModel


def _now() -> datetime:
    # Naive UTC: SQLite round-trips datetimes without tzinfo; everything
    # stored/computed in the app stays naive so comparisons never mix
    # aware and naive.
    return datetime.now(timezone.utc).replace(tzinfo=None)


class Device(SQLModel, table=True):
    __tablename__ = "devices"

    id: int | None = Field(default=None, primary_key=True)
    device_id: str = Field(index=True, unique=True)
    protocol_version: int
    display_name: str = ""
    signing_public_key: str
    created_at: datetime = Field(default_factory=_now)


class PairingSession(SQLModel, table=True):
    __tablename__ = "pairing_sessions"

    id: int | None = Field(default=None, primary_key=True)
    session_id: str = Field(index=True, unique=True)
    code_hash: str
    request_id: str | None = Field(default=None, unique=True)
    creator_device_id: str | None = None
    creator_ephemeral_public_key: str | None = None
    peer_device_id: str | None = None
    peer_signing_public_key: str | None = None
    peer_ephemeral_public_key: str | None = None
    status: str = "open"
    created_at: datetime = Field(default_factory=_now)
    expires_at: datetime
    joined_at: datetime | None = None
    completed_at: datetime | None = None


class ResetSession(SQLModel, table=True):
    __tablename__ = "reset_sessions"

    id: int | None = Field(default=None, primary_key=True)
    session_id: str = Field(index=True, unique=True)
    request_id: str = Field(index=True, unique=True)
    requester_device_id: str
    peer_device_id: str
    blocked_package: str
    reset_type: str
    created_at: datetime = Field(default_factory=_now)
    expires_at: datetime
    state: str = "ACTIVE"


class RelayedEvent(SQLModel, table=True):
    __tablename__ = "relayed_events"

    id: int | None = Field(default=None, primary_key=True)
    event_id: str = Field(index=True, unique=True)
    session_id: str = Field(index=True)
    sender_device_id: str
    recipient_device_id: str
    protocol_version: int
    type: str
    created_at: datetime = Field(default_factory=_now)
    expires_at: datetime
    raw_payload: bytes = Field(default=b"")
