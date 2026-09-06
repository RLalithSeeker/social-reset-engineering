import uuid

from httpx import AsyncClient

from tests.conftest import auth_headers, register_device


async def _create_reset_session(client: AsyncClient, token: str, requester: str, peer: str, request_id: str) -> tuple[int, dict]:
    resp = await client.post(
        "/v1/sessions",
        headers=auth_headers(token),
        json={
            "sessionId": f"res-{uuid.uuid4()}",
            "requestId": request_id,
            "requesterDeviceId": requester,
            "peerDeviceId": peer,
            "blockedPackage": "com.example.social",
            "resetType": "FRIEND_ACCEPTED",
        },
    )
    return resp.status_code, resp.json()


async def test_create_reset_session(client: AsyncClient) -> None:
    device_a, token_a = await register_device(client)
    device_b, _ = await register_device(client)
    status, body = await _create_reset_session(client, token_a, device_a, device_b, f"req-{uuid.uuid4()}")
    assert status == 201
    assert body["sessionId"]
    assert body["peerDeviceId"] == device_b


async def test_create_reset_session_idempotent_on_request_id(client: AsyncClient) -> None:
    device_a, token_a = await register_device(client)
    device_b, _ = await register_device(client)
    request_id = f"req-{uuid.uuid4()}"

    status1, body1 = await _create_reset_session(client, token_a, device_a, device_b, request_id)
    assert status1 == 201
    status2, body2 = await _create_reset_session(client, token_a, device_a, device_b, request_id)
    assert status2 == 201
    assert body2["sessionId"] == body1["sessionId"]


async def test_create_reset_session_requires_matching_requester(client: AsyncClient) -> None:
    device_a, _ = await register_device(client)
    device_b, token_b = await register_device(client)
    resp = await client.post(
        "/v1/sessions",
        headers=auth_headers(token_b),
        json={
            "sessionId": f"res-{uuid.uuid4()}",
            "requestId": f"req-{uuid.uuid4()}",
            "requesterDeviceId": device_a,
            "peerDeviceId": device_b,
            "blockedPackage": "com.example.social",
            "resetType": "FRIEND_ACCEPTED",
        },
    )
    assert resp.status_code == 403


async def test_get_session_only_for_participants(client: AsyncClient) -> None:
    device_a, token_a = await register_device(client)
    device_b, _ = await register_device(client)
    device_c, token_c = await register_device(client)
    _, body = await _create_reset_session(client, token_a, device_a, device_b, f"req-{uuid.uuid4()}")

    ok = await client.get(f"/v1/sessions/{body['sessionId']}", headers=auth_headers(token_a))
    assert ok.status_code == 200

    forbidden = await client.get(f"/v1/sessions/{body['sessionId']}", headers=auth_headers(token_c))
    assert forbidden.status_code == 403

    missing = await client.get(f"/v1/sessions/{uuid.uuid4()}", headers=auth_headers(token_a))
    assert missing.status_code == 404