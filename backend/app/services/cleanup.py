import asyncio
import logging
from datetime import datetime, timedelta, timezone

from sqlmodel import Session, select

from app.config import get_settings
from app.db import get_engine
from app.models import PairingSession, RelayedEvent, ResetSession

logger = logging.getLogger("socialreset")


def _now() -> datetime:
    return datetime.now(timezone.utc).replace(tzinfo=None)


def run_cleanup() -> int:
    settings = get_settings()
    count = 0
    with Session(get_engine()) as session:
        pairing_cutoff = _now() - timedelta(seconds=settings.pairing_ttl_seconds)
        reset_cutoff = _now() - timedelta(seconds=settings.reset_session_ttl_seconds)
        event_cutoff = _now() - timedelta(seconds=settings.event_ttl_seconds)

        for row in session.exec(select(PairingSession).where(PairingSession.created_at < pairing_cutoff)).all():
            session.delete(row)
            count += 1

        for row in session.exec(select(ResetSession).where(ResetSession.created_at < reset_cutoff)).all():
            session.delete(row)
            count += 1

        for row in session.exec(select(RelayedEvent).where(RelayedEvent.created_at < event_cutoff)).all():
            session.delete(row)
            count += 1

        session.commit()
    if count:
        logger.info("cleanup_expired deleted=%d", count)
    return count


async def cleanup_loop() -> None:
    settings = get_settings()
    while True:
        await asyncio.sleep(settings.cleanup_interval_seconds)
        await asyncio.to_thread(run_cleanup)
