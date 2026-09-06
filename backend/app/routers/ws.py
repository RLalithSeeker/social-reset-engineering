import asyncio
import json
import logging

from fastapi import APIRouter, status, WebSocket, WebSocketDisconnect

from app.config import get_settings
from app.logging import redact_device
from app.security import decode_device_token
from app.services.relay_service import ws_fanout

logger = logging.getLogger("socialreset")
router = APIRouter(tags=["ws"])


async def _send_loop(websocket: WebSocket, outbox: asyncio.Queue) -> None:
    while True:
        raw = await outbox.get()
        await websocket.send_bytes(raw)


@router.websocket("/v1/ws/{device_id}")
async def device_ws(websocket: WebSocket, device_id: str) -> None:
    settings = get_settings()

    # Accept the transport first; authorization still gates subscription.
    await websocket.accept()

    # --- Authenticate BEFORE any subscription ---
    if "token" in websocket.query_params:
        await websocket.close(code=status.WS_1008_POLICY_VIOLATION, reason="query token rejected")
        return

    auth = websocket.headers.get("authorization", "")
    access_token = auth.removeprefix("Bearer ").strip()
    if not access_token:
        try:
            first = await asyncio.wait_for(websocket.receive_text(), timeout=settings.ws_idle_timeout_seconds)
            msg = json.loads(first)
            access_token = msg.get("token", "")
        except (asyncio.TimeoutError, json.JSONDecodeError):
            await websocket.close(code=status.WS_1008_POLICY_VIOLATION, reason="no token supplied")
            return
        except WebSocketDisconnect:
            return

    try:
        token_device = decode_device_token(access_token)
    except Exception:
        await websocket.close(code=status.WS_1008_POLICY_VIOLATION, reason="invalid token")
        return

    if token_device != device_id:
        await websocket.close(code=status.WS_1008_POLICY_VIOLATION, reason="token subject mismatch")
        return

    # --- Only subscribe after auth ---
    if ws_fanout.connection_count(device_id) >= settings.max_concurrent_ws_per_device:
        await websocket.close(code=status.WS_1008_POLICY_VIOLATION, reason="too many connections")
        return

    outbox: asyncio.Queue[bytes] = asyncio.Queue()
    ws_fanout.subscribe(device_id, outbox)
    send_task = asyncio.create_task(_send_loop(websocket, outbox))
    logger.info("ws_connected device=%s", redact_device(device_id))

    try:
        while True:
            try:
                message = await asyncio.wait_for(
                    websocket.receive_text(), timeout=settings.ws_idle_timeout_seconds
                )
                if message.strip() == "ping":
                    await websocket.send_text("pong")
            except asyncio.TimeoutError:
                await websocket.close(code=status.WS_1008_POLICY_VIOLATION, reason="idle timeout")
                break
            except WebSocketDisconnect:
                break
    finally:
        ws_fanout.unsubscribe(device_id, outbox)
        send_task.cancel()
        try:
            await send_task
        except asyncio.CancelledError:
            pass
        logger.info("ws_disconnected device=%s", redact_device(device_id))
