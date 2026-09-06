import uuid

from httpx import AsyncClient

from tests.conftest import auth_headers, register_device


async def test_pairing_create_rate_limited_6th(client: AsyncClient) -> None:
    _, token = await register_device(client)
    for _ in range(5):
        resp = await client.post(
            "/v1/pairing/sessions",
            headers=auth_headers(token),
            json={"requestId": f"pair-{uuid.uuid4()}"},
        )
        assert resp.status_code == 201

    sixth = await client.post(
        "/v1/pairing/sessions",
        headers=auth_headers(token),
        json={"requestId": f"pair-{uuid.uuid4()}"},
    )
    assert sixth.status_code == 429


async def test_register_rate_limited_4th(client: AsyncClient) -> None:
    device_id = f"dev-{uuid.uuid4()}"
    key = f"key-{uuid.uuid4()}"
    for _ in range(3):
        resp = await client.post(
            "/v1/devices/register",
            json={
                "deviceId": device_id,
                "protocolVersion": 1,
                "displayName": "test-device",
                "signingPublicKey": key,
            },
        )
        assert resp.status_code == 200

    fourth = await client.post(
        "/v1/devices/register",
        json={
            "deviceId": device_id,
            "protocolVersion": 1,
            "displayName": "test-device",
            "signingPublicKey": key,
        },
    )
    assert fourth.status_code == 429