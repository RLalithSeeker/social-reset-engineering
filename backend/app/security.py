import time
from collections import defaultdict, deque
from dataclasses import dataclass, field

import jwt
from fastapi import HTTPException, Request
from sqlmodel import Session, select

from app.config import get_settings
from app.db import get_engine
from app.logging import redact_device
from app.models import Device

JWTAuthError = jwt.PyJWTError


def _lookup_device(device_id: str):
    with Session(get_engine()) as session:
        return session.exec(
            select(Device).where(Device.device_id == device_id)
        ).first()


# ---------------- Token helpers ----------------

def create_device_token(device_id: str) -> str:
    settings = get_settings()
    payload = {
        "sub": device_id,
        "exp": int(time.time()) + settings.jwt_ttl_seconds,
        "iat": int(time.time()),
    }
    return jwt.encode(payload, settings.server_secret, algorithm="HS256")


def decode_device_token(token: str) -> str:
    settings = get_settings()
    try:
        payload = jwt.decode(token, settings.server_secret, algorithms=["HS256"])
    except JWTAuthError:
        raise HTTPException(status_code=401, detail="invalid or expired token")
    return str(payload.get("sub", ""))


def require_device(request: Request):
    auth = request.headers.get("Authorization", "")
    token = auth.removeprefix("Bearer ").strip()
    if not token:
        raise HTTPException(status_code=401, detail="missing bearer token")
    device_id = decode_device_token(token)
    device = _lookup_device(device_id)
    if device is None:
        raise HTTPException(status_code=404, detail="device not registered")
    request.state.device = device
    return device


# ---------------- Rate limiter ----------------

@dataclass
class _Bucket:
    capacity: int
    refill_seconds: float
    timestamps: deque[float] = field(default_factory=deque)
    _start: float = field(default_factory=time.monotonic)

    def allow(self) -> bool:
        now = time.monotonic()
        cutoff = now - self.refill_seconds
        while self.timestamps and self.timestamps[0] < cutoff:
            self.timestamps.popleft()
        if len(self.timestamps) < self.capacity:
            self.timestamps.append(now)
            return True
        return False


class RateLimiter:
    _buckets: dict = defaultdict(dict)

    @classmethod
    def _bucket(cls, key: str, capacity: int, window: float) -> _Bucket:
        bucket = cls._buckets[key].get(capacity)
        if bucket is None:
            bucket = _Bucket(capacity=capacity, refill_seconds=window)
            cls._buckets[key][capacity] = bucket
        return bucket

    @classmethod
    def check(cls, key: str, capacity: int, window: float) -> bool:
        return cls._bucket(key, capacity, window).allow()


def enforce_rate_limit(device_id: str | None, route: str, capacity: int, window: float) -> None:
    key = f"{route}:{redact_device(device_id or 'anon')}"
    if not RateLimiter.check(key, capacity, window):
        raise HTTPException(status_code=429, detail="rate limit exceeded")