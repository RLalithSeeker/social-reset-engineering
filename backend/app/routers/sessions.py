import json
import logging

from fastapi import APIRouter, Depends, HTTPException, Request
from sqlmodel import Session

from app.config import get_settings
from app.db import get_session
from app.logging import redact_device
from app.schemas import (
    RelayEventRequest,
    RelayEventResponse,
    ResetSessionCreateRequest,
    ResetSessionResponse,
)
from app.security import enforce_rate_limit, require_device
from app.services.relay_service import (
    create_reset_session,
    get_reset_session,
    relay_event,
    validate_relay_event_structure,
    ws_fanout,
)

logger = logging.getLogger("socialreset")
router = APIRouter(prefix="/v1/sessions", tags=["sessions"])

EVENT_RELAY_PER_MIN = 60


@router.post("", response_model=ResetSessionResponse, status_code=201)
def create_session(
    payload: ResetSessionCreateRequest,
    request: Request,
    session: Session = Depends(get_session),
) -> ResetSessionResponse:
    device = require_device(request)
    if payload.requesterDeviceId != device.device_id:
        raise HTTPException(status_code=403, detail="requesterDeviceId does not match authenticated device")

    reset = create_reset_session(
        session,
        session_id=payload.sessionId,
        request_id=payload.requestId,
        requester_device_id=payload.requesterDeviceId,
        peer_device_id=payload.peerDeviceId,
        blocked_package=payload.blockedPackage,
        reset_type=payload.resetType,
        ttl_seconds=payload.ttl_seconds,
    )
    logger.info(
        "session_created session=%s requester=%s peer=%s",
        reset.session_id[:8],
        redact_device(reset.requester_device_id),
        redact_device(reset.peer_device_id),
    )
    return ResetSessionResponse(
        sessionId=reset.session_id,
        requesterDeviceId=reset.requester_device_id,
        peerDeviceId=reset.peer_device_id,
        blockedPackage=reset.blocked_package,
        resetType=reset.reset_type,
        expiresAt=reset.expires_at,
        state=reset.state,
    )


@router.post("/{session_id}/events", response_model=RelayEventResponse)
async def relay_event_endpoint(
    session_id: str,
    request: Request,
    session: Session = Depends(get_session),
) -> RelayEventResponse:
    device = require_device(request)
    settings = get_settings()
    enforce_rate_limit(device.device_id, "event_relay", EVENT_RELAY_PER_MIN, 60)

    raw_body = await request.body()
    if len(raw_body) > settings.max_request_bytes:
        raise HTTPException(status_code=413, detail="request body too large")

    try:
        payload = json.loads(raw_body)
    except json.JSONDecodeError:
        raise HTTPException(status_code=400, detail="invalid JSON body")

    if not isinstance(payload, dict):
        raise HTTPException(status_code=400, detail="body must be a JSON object")

    # Strict object shape gate from the OpenAPI contract (task requires it)
    try:
        RelayEventRequest.model_validate(payload)
    except Exception:
        raise HTTPException(status_code=400, detail="invalid event envelope")

    validate_relay_event_structure(payload, max_bytes=settings.max_request_bytes)

    reset = get_reset_session(session, session_id)
    if reset is None:
        raise HTTPException(status_code=404, detail="session not found")

    if payload.get("sessionId") != session_id:
        raise HTTPException(status_code=400, detail="sessionId mismatch")

    relayed = relay_event(
        session,
        reset=reset,
        payload=payload,
        raw_bytes=raw_body,
    )

    # Fan out EXACT raw bytes to the recipient; never re-serialized.
    delivered = await ws_fanout.publish(relayed.recipient_device_id, raw_body)
    logger.info(
        "relay fanned_out=%d recipient=%s", delivered, redact_device(relayed.recipient_device_id)
    )
    return RelayEventResponse(eventId=relayed.event_id)


@router.get("/{session_id}", response_model=ResetSessionResponse)
def get_session(
    session_id: str,
    request: Request,
    session: Session = Depends(get_session),
) -> ResetSessionResponse:
    device = require_device(request)
    reset = get_reset_session(session, session_id)
    if reset is None:
        raise HTTPException(status_code=404, detail="session not found")
    if device.device_id not in (reset.requester_device_id, reset.peer_device_id):
        raise HTTPException(status_code=403, detail="not a participant of this session")
    return ResetSessionResponse(
        sessionId=reset.session_id,
        requesterDeviceId=reset.requester_device_id,
        peerDeviceId=reset.peer_device_id,
        blockedPackage=reset.blocked_package,
        resetType=reset.reset_type,
        expiresAt=reset.expires_at,
        state=reset.state,
    )