from datetime import datetime
from pydantic import BaseModel, ConfigDict, Field


class DeviceRegisterRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    deviceId: str = Field(min_length=1, max_length=128)
    protocolVersion: int
    displayName: str = Field(default="", max_length=128)
    signingPublicKey: str = Field(min_length=1, max_length=2048)


class DeviceRegisterResponse(BaseModel):
    deviceId: str
    accessToken: str
    expiresAt: datetime


class PairingCreateRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    requestId: str | None = None
    creatorEphemeralPublicKey: str | None = Field(default=None, max_length=2048)


class PairingCreateResponse(BaseModel):
    sessionId: str
    code: str
    expiresAt: datetime


class PairingJoinRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    code: str
    deviceId: str = Field(min_length=1, max_length=128)
    signingPublicKey: str = Field(min_length=1, max_length=2048)
    ephemeralPublicKey: str = Field(min_length=1, max_length=2048)
    protocolVersion: int = 1
    requestId: str | None = None


class PairingJoinResponse(BaseModel):
    sessionId: str
    status: str
    peerDeviceId: str | None = None
    peerDisplayName: str | None = None
    peerSigningPublicKey: str | None = None
    peerEphemeralPublicKey: str | None = None


class PairingCompleteRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    requestId: str | None = None


class PairingCompleteResponse(BaseModel):
    sessionId: str
    status: str
    peerDeviceId: str | None = None
    peerDisplayName: str | None = None
    peerSigningPublicKey: str | None = None
    peerEphemeralPublicKey: str | None = None


class ResetSessionCreateRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    sessionId: str
    requestId: str
    requesterDeviceId: str
    peerDeviceId: str
    blockedPackage: str
    resetType: str
    ttl_seconds: int | None = None


class ResetSessionResponse(BaseModel):
    sessionId: str
    requesterDeviceId: str
    peerDeviceId: str
    blockedPackage: str
    resetType: str
    expiresAt: datetime
    state: str


class RelayEventRequest(BaseModel):
    # Structure-only validation: extra fields are tolerated and forwarded
    # byte-for-byte. The server must never reject a client's signed payload
    # because of unknown envelope fields.
    model_config = ConfigDict(extra="ignore")

    protocolVersion: int
    requestId: str | None = None
    eventId: str
    sessionId: str
    senderDeviceId: str
    recipientDeviceId: str
    sequence: int
    type: str
    timestamp: int
    nonce: str
    payload: dict
    signature: str


class RelayEventResponse(BaseModel):
    eventId: str
    status: str = "relayed"
