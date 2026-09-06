import uuid

from httpx import AsyncClient

from tests.conftest import register_device


async def test_register_new_device_returns_token(client: AsyncClient) -> None:
    device_id, token = await register_device(client)
    assert token
    assert len(token) > 20


async def test_register_same_device_same_key_is_ok(client: AsyncClient) -> None:
    device_id = f"dev-{uuid.uuid4()}"
    key = "same-key"
    _, token1 = await register_device(client, device_id, key)
    _, token2 = await register_device(client, device_id, key)
    assert token1
    assert token2


async def test_register_same_device_different_key_conflicts(client: AsyncClient) -> None:
    device_id = f"dev-{uuid.uuid4()}"
    await register_device(client, device_id, "key-A")
    resp = await client.post(
        "/v1/devices/register",
        json={
            "deviceId": device_id,
            "protocolVersion": 1,
            "displayName": "test-device",
            "signingPublicKey": "key-B",
        },
    )
    assert resp.status_code == 409