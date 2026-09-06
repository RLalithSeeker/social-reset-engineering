import logging
from datetime import datetime, timezone

from fastapi import APIRouter, Depends, HTTPException, Request
from sqlmodel import Session, select

from app.config import get_settings
from app.db import get_session
from app.logging import redact_device
from app.models import Device
from app.schemas import DeviceRegisterRequest, DeviceRegisterResponse
from app.security import create_device_token, enforce_rate_limit

logger = logging.getLogger("socialreset")
router = APIRouter(prefix="/v1/devices", tags=["devices"])

# Per-device register limit
REGISTER_PER_MIN = 3


@router.post("/register", response_model=DeviceRegisterResponse)
def register_device(
    payload: DeviceRegisterRequest,
    request: Request,
    session: Session = Depends(get_session),
) -> DeviceRegisterResponse:
    settings = get_settings()
    device_id = payload.deviceId

    enforce_rate_limit(device_id, "register", REGISTER_PER_MIN, 60)

    if payload.protocolVersion not in (1,):
        raise HTTPException(status_code=400, detail="unsupported protocolVersion")

    existing = session.exec(select(Device).where(Device.device_id == device_id)).first()
    if existing is None:
        device = Device(
            device_id=device_id,
            protocol_version=payload.protocolVersion,
            display_name=payload.displayName,
            signing_public_key=payload.signingPublicKey,
        )
        session.add(device)
        session.commit()
        session.refresh(device)
        logger.info("device_registered device=%s", redact_device(device_id))
    elif existing.signing_public_key != payload.signingPublicKey:
        raise HTTPException(
            status_code=409,
            detail="deviceId already bound to a different signing key",
        )
    else:
        device = existing

    token = create_device_token(device_id)
    expires_at = datetime.now(timezone.utc).timestamp() + settings.jwt_ttl_seconds
    return DeviceRegisterResponse(
        deviceId=device_id,
        accessToken=token,
        expiresAt=datetime.fromtimestamp(expires_at, tz=timezone.utc),
    )