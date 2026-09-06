import uuid
from datetime import timedelta

from httpx import AsyncClient

from app.db import get_engine
from app.models import PairingSession
from tests.conftest import auth_headers, register_device
from sqlmodel import Session, select


async def _create_pairing(client: AsyncClient, token: str) -> tuple[str, str]:
    resp = await client.post(
        "/v1/pairing/sessions",
        headers=auth_headers(token),
        json={
            "requestId": f"pair-{uuid.uuid4()}",
            "creatorEphemeralPublicKey": "creator-ephemeral",
        },
    )
    assert resp.status_code == 201
    body = resp.json()
    return body["sessionId"], body["code"]


async def test_create_pairing_returns_code(client: AsyncClient) -> None:
    _, token = await register_device(client)
    resp = await client.post(
        "/v1/pairing/sessions",
        headers=auth_headers(token),
        json={"requestId": f"pair-{uuid.uuid4()}"},
    )
    assert resp.status_code == 201
    body = resp.json()
    assert len(body["code"]) >= 8


async def test_valid_join_returns_200(client: AsyncClient) -> None:
    device_a, token_a = await register_device(client, key="creator-key")
    device_b, token_b = await register_device(client, key="peer-key")
    pairing_id, code = await _create_pairing(client, token_a)

    resp = await client.post(
        f"/v1/pairing/sessions/{pairing_id}/join",
        headers=auth_headers(token_b),
        json={
            "code": code,
            "deviceId": device_b,
            "signingPublicKey": "peer-key",
            "ephemeralPublicKey": "peer-ephemeral",
            "protocolVersion": 1,
            "requestId": f"join-{uuid.uuid4()}",
        },
    )
    assert resp.status_code == 200
    body = resp.json()
    assert body["sessionId"] == pairing_id
    assert body["peerDeviceId"] == device_a
    assert body["peerSigningPublicKey"] == "creator-key"
    assert body["peerEphemeralPublicKey"] == "creator-ephemeral"


async def test_reused_code_returns_409(client: AsyncClient) -> None:
    device_a, token_a = await register_device(client)
    device_b, token_b = await register_device(client)
    device_c, token_c = await register_device(client)
    pairing_id, code = await _create_pairing(client, token_a)

    def join_body(device_id: str) -> dict:
        return {
            "code": code,
            "deviceId": device_id,
            "signingPublicKey": f"key-{device_id}",
            "ephemeralPublicKey": f"eph-{device_id}",
            "protocolVersion": 1,
        }

    first = await client.post(
        f"/v1/pairing/sessions/{pairing_id}/join",
        headers=auth_headers(token_b),
        json=join_body(device_b),
    )
    assert first.status_code == 200

    second = await client.post(
        f"/v1/pairing/sessions/{pairing_id}/join",
        headers=auth_headers(token_c),
        json=join_body(device_c),
    )
    assert second.status_code == 409


async def test_expired_code_returns_410(client: AsyncClient) -> None:
    _, token_a = await register_device(client)
    _, token_b = await register_device(client)
    pairing_id, code = await _create_pairing(client, token_a)

    with Session(get_engine()) as session:
        pairing = session.exec(
            select(PairingSession).where(PairingSession.session_id == pairing_id)
        ).one()
        pairing.expires_at = pairing.created_at - timedelta(seconds=1)
        session.add(pairing)
        session.commit()

    resp = await client.post(
        f"/v1/pairing/sessions/{pairing_id}/join",
        headers=auth_headers(token_b),
        json={
            "code": code,
            "deviceId": "device-b",
            "signingPublicKey": "peer-key",
            "ephemeralPublicKey": "peer-ephemeral",
            "protocolVersion": 1,
        },
    )
    assert resp.status_code == 410


async def test_wrong_code_returns_403(client: AsyncClient) -> None:
    _, token_a = await register_device(client)
    _, token_b = await register_device(client)
    pairing_id, _ = await _create_pairing(client, token_a)

    resp = await client.post(
        f"/v1/pairing/sessions/{pairing_id}/join",
        headers=auth_headers(token_b),
        json={
            "code": "definitely-wrong",
            "deviceId": "device-b",
            "signingPublicKey": "peer-key",
            "ephemeralPublicKey": "peer-ephemeral",
            "protocolVersion": 1,
        },
    )
    assert resp.status_code == 403


async def test_complete_pairing(client: AsyncClient) -> None:
    device_a, token_a = await register_device(client, key="creator-key")
    device_b, token_b = await register_device(client)
    pairing_id, code = await _create_pairing(client, token_a)

    await client.post(
        f"/v1/pairing/sessions/{pairing_id}/join",
        headers=auth_headers(token_b),
        json={
            "code": code,
            "deviceId": device_b,
            "signingPublicKey": "peer-key",
            "ephemeralPublicKey": "peer-ephemeral",
            "protocolVersion": 1,
        },
    )
    resp = await client.post(
        f"/v1/pairing/sessions/{pairing_id}/complete",
        headers=auth_headers(token_a),
        json={"requestId": f"complete-{uuid.uuid4()}"},
    )
    assert resp.status_code == 200
    body = resp.json()
    assert body["status"] == "completed"
    assert body["peerDeviceId"] == device_b
    assert body["peerSigningPublicKey"] == "peer-key"
    assert body["peerEphemeralPublicKey"] == "peer-ephemeral"
