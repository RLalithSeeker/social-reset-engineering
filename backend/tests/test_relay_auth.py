import uuid

from httpx import AsyncClient

from tests.conftest import auth_headers, event_envelope, register_device


async def _make_session(client: AsyncClient) -> tuple[str, str, str, str]:
    device_a, token_a = await register_device(client)
    device_b, _ = await register_device(client)
    session_id = f"res-{uuid.uuid4()}"
    resp = await client.post(
        "/v1/sessions",
        headers=auth_headers(token_a),
        json={
            "sessionId": session_id,
            "requestId": f"req-{uuid.uuid4()}",
            "requesterDeviceId": device_a,
            "peerDeviceId": device_b,
            "blockedPackage": "com.example.social",
            "resetType": "FRIEND_ACCEPTED",
        },
    )
    assert resp.status_code == 201
    return session_id, device_a, device_b, token_a


async def test_relay_event_participant_ok(client: AsyncClient) -> None:
    session_id, device_a, device_b, token_a = await _make_session(client)
    envelope = event_envelope(session_id, device_a, device_b)
    resp = await client.post(
        f"/v1/sessions/{session_id}/events",
        headers=auth_headers(token_a),
        json=envelope,
    )
    assert resp.status_code == 200
    assert resp.json()["eventId"] == envelope["eventId"]


async def test_relay_event_non_participant_sender_403(client: AsyncClient) -> None:
    session_id, device_a, device_b, token_a = await _make_session(client)
    device_intruder, token_intruder = await register_device(client)

    # intruder is not a participant
    envelope = event_envelope(session_id, device_intruder, device_b)
    resp = await client.post(
        f"/v1/sessions/{session_id}/events",
        headers=auth_headers(token_intruder),
        json=envelope,
    )
    assert resp.status_code == 403

    # participant A, but recipient is an outsider
    envelope2 = event_envelope(session_id, device_a, device_intruder)
    resp2 = await client.post(
        f"/v1/sessions/{session_id}/events",
        headers=auth_headers(token_a),
        json=envelope2,
    )
    assert resp2.status_code == 403


async def test_relay_event_oversized_body_413(client: AsyncClient) -> None:
    session_id, device_a, device_b, token_a = await _make_session(client)
    big_payload = {"blob": "x" * 9000}
    envelope = event_envelope(session_id, device_a, device_b, payload=big_payload)
    resp = await client.post(
        f"/v1/sessions/{session_id}/events",
        headers=auth_headers(token_a),
        json=envelope,
    )
    assert resp.status_code == 413


async def test_relay_event_unknown_session_404(client: AsyncClient) -> None:
    session_id, device_a, device_b, token_a = await _make_session(client)
    unknown = f"res-{uuid.uuid4()}"
    envelope = event_envelope(unknown, device_a, device_b)
    resp = await client.post(
        f"/v1/sessions/{unknown}/events",
        headers=auth_headers(token_a),
        json=envelope,
    )
    assert resp.status_code == 404


async def test_relay_event_missing_field_400(client: AsyncClient) -> None:
    session_id, device_a, device_b, token_a = await _make_session(client)
    envelope = event_envelope(session_id, device_a, device_b)
    del envelope["signature"]
    resp = await client.post(
        f"/v1/sessions/{session_id}/events",
        headers=auth_headers(token_a),
        json=envelope,
    )
    assert resp.status_code == 400