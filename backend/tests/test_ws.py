import json
import uuid

import pytest
from starlette.testclient import TestClient
from starlette.websockets import WebSocketDisconnect

from app.main import app

from tests.conftest import event_envelope, register_device_sync


def test_ws_unauthenticated_subscribe_rejected() -> None:
    with TestClient(app) as client:
        with pytest.raises(WebSocketDisconnect) as exc:
            with client.websocket_connect("/v1/ws/device-unauth") as ws:
                ws.receive_text()
        assert exc.value.code in (1008, 1006)


def test_ws_token_device_mismatch_rejected() -> None:
    device_id = f"dev-{uuid.uuid4()}"
    with TestClient(app) as client:
        _, token = register_device_sync(client, device_id)
        with pytest.raises(WebSocketDisconnect) as exc:
            with client.websocket_connect(
                "/v1/ws/other-device",
                headers={"Authorization": f"Bearer {token}"},
            ) as ws:
                ws.receive_text()
        assert exc.value.code in (1008, 1006)


def test_ws_query_token_rejected() -> None:
    device_id = f"dev-{uuid.uuid4()}"
    with TestClient(app) as client:
        _, token = register_device_sync(client, device_id)
        with pytest.raises(WebSocketDisconnect) as exc:
            with client.websocket_connect(f"/v1/ws/{device_id}?token={token}") as ws:
                ws.receive_text()
        assert exc.value.code in (1008, 1006)


def test_ws_relay_pushes_to_connected_recipient() -> None:
    with TestClient(app) as client:
        device_a, token_a = register_device_sync(client, f"dev-{uuid.uuid4()}")
        device_b, token_b = register_device_sync(client, f"dev-{uuid.uuid4()}")
        session_id = f"res-{uuid.uuid4()}"
        created = client.post(
            "/v1/sessions",
            headers={"Authorization": f"Bearer {token_a}"},
            json={
                "sessionId": session_id,
                "requestId": f"req-{uuid.uuid4()}",
                "requesterDeviceId": device_a,
                "peerDeviceId": device_b,
                "blockedPackage": "com.example.social",
                "resetType": "FRIEND_ACCEPTED",
            },
        )
        assert created.status_code == 201

        envelope = event_envelope(session_id, device_a, device_b)
        with client.websocket_connect(
            f"/v1/ws/{device_b}",
            headers={"Authorization": f"Bearer {token_b}"},
        ) as ws:
            relayed = client.post(
                f"/v1/sessions/{session_id}/events",
                headers={"Authorization": f"Bearer {token_a}"},
                json=envelope,
            )
            assert relayed.status_code == 200
            raw = ws.receive_bytes()
            forwarded = json.loads(raw)
            assert forwarded["eventId"] == envelope["eventId"]
            assert forwarded["signature"] == envelope["signature"]
            assert forwarded["payload"] == envelope["payload"]
