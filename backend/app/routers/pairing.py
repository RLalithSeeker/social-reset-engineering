import logging
from datetime import timezone

from fastapi import APIRouter, Depends, HTTPException, Request
from sqlmodel import Session, select

from app.config import get_settings
from app.db import get_session
from app.logging import redact_device
from app.models import Device
from app.schemas import (
    PairingCompleteRequest,
    PairingCompleteResponse,
    PairingCreateRequest,
    PairingCreateResponse,
    PairingJoinRequest,
    PairingJoinResponse,
)
from app.security import enforce_rate_limit, require_device
from app.services.pairing_service import (
    complete_pairing_session,
    create_pairing_session,
    get_pairing_session,
    join_pairing_session,
)

logger = logging.getLogger("socialreset")
router = APIRouter(prefix="/v1/pairing", tags=["pairing"])

PAIRING_CREATE_PER_MIN = 5


def _expires_epoch(session_pk) -> int:
    return int(session_pk.expires_at.replace(tzinfo=timezone.utc).timestamp())


def _device(session: Session, device_id: str | None) -> Device | None:
    if not device_id:
        return None
    return session.exec(select(Device).where(Device.device_id == device_id)).first()


def _other_peer_fields(session: Session, pairing, caller_device_id: str) -> dict:
    if caller_device_id == pairing.creator_device_id:
        peer = _device(session, pairing.peer_device_id)
        return {
            "peerDeviceId": pairing.peer_device_id,
            "peerDisplayName": peer.display_name if peer else None,
            "peerSigningPublicKey": pairing.peer_signing_public_key,
            "peerEphemeralPublicKey": pairing.peer_ephemeral_public_key,
        }

    creator = _device(session, pairing.creator_device_id)
    return {
        "peerDeviceId": pairing.creator_device_id,
        "peerDisplayName": creator.display_name if creator else None,
        "peerSigningPublicKey": creator.signing_public_key if creator else None,
        "peerEphemeralPublicKey": pairing.creator_ephemeral_public_key,
    }


@router.post("/sessions", response_model=PairingCreateResponse, status_code=201)
def create_pairing(
    payload: PairingCreateRequest,
    request: Request,
    session: Session = Depends(get_session),
) -> PairingCreateResponse:
    device = require_device(request)
    enforce_rate_limit(device.device_id, "pairing_create", PAIRING_CREATE_PER_MIN, 60)

    pairing, code = create_pairing_session(
        session,
        creator_device_id=device.device_id,
        creator_ephemeral_public_key=payload.creatorEphemeralPublicKey,
        request_id=payload.requestId,
    )
    logger.info(
        "pairing_created pairing=%s device=%s ttl=%ds",
        pairing.session_id[:8],
        redact_device(device.device_id),
        get_settings().pairing_ttl_seconds,
    )
    return PairingCreateResponse(
        sessionId=pairing.session_id,
        code=code or "",
        expiresAt=pairing.expires_at,
    )


@router.post("/sessions/{pairing_id}/join", response_model=PairingJoinResponse)
def join_pairing(
    pairing_id: str,
    payload: PairingJoinRequest,
    request: Request,
    session: Session = Depends(get_session),
) -> PairingJoinResponse:
    device = require_device(request)
    pairing = get_pairing_session(session, pairing_id)
    if pairing is None:
        raise HTTPException(status_code=404, detail="pairing session not found")

    join_pairing_session(
        session,
        pairing,
        code=payload.code,
        device_id=device.device_id,
        signing_public_key=payload.signingPublicKey,
        ephemeral_public_key=payload.ephemeralPublicKey,
        protocol_version=payload.protocolVersion,
    )
    logger.info(
        "pairing_joined pairing=%s peer=%s",
        pairing.session_id[:8],
        redact_device(device.device_id),
    )
    return PairingJoinResponse(
        sessionId=pairing.session_id,
        status=pairing.status,
        **_other_peer_fields(session, pairing, device.device_id),
    )


@router.post("/sessions/{pairing_id}/complete", response_model=PairingCompleteResponse)
def complete_pairing(
    pairing_id: str,
    payload: PairingCompleteRequest,
    request: Request,
    session: Session = Depends(get_session),
) -> PairingCompleteResponse:
    device = require_device(request)
    pairing = get_pairing_session(session, pairing_id)
    if pairing is None:
        raise HTTPException(status_code=404, detail="pairing session not found")

    if device.device_id not in (pairing.creator_device_id, pairing.peer_device_id):
        raise HTTPException(status_code=403, detail="not a participant of this pairing")

    complete_pairing_session(session, pairing)
    logger.info(
        "pairing_completed pairing=%s device=%s",
        pairing.session_id[:8],
        redact_device(device.device_id),
    )
    return PairingCompleteResponse(
        sessionId=pairing.session_id,
        status=pairing.status,
        **_other_peer_fields(session, pairing, device.device_id),
    )
