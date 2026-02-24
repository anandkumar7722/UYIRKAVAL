"""
============================================================
SHE-SHIELD Backend – Comprehensive Endpoint Tests
============================================================
Covers every endpoint with positive, negative, and edge-case
scenarios.  The Supabase client is fully mocked (see conftest).
============================================================
"""

import uuid
from datetime import datetime, timezone
from unittest.mock import MagicMock, patch

import pytest

# ── Helpers ────────────────────────────────────────────────

VALID_UUID = str(uuid.uuid4())
VALID_UUID_2 = str(uuid.uuid4())
SOS_UUID = str(uuid.uuid4())

VALID_PROFILE = {
    "id": VALID_UUID,
    "full_name": "Test User",
    "phone_number": "+919876543210",
}

VALID_SOS_ROW = {
    "id": SOS_UUID,
    "victim_id": VALID_UUID,
    "trigger_method": "button",
    "risk_score": 75,
    "initial_lat": 12.97,
    "initial_lng": 77.59,
    "status": "active",
    "relay_ip": "127.0.0.1",
    "created_at": datetime.now(timezone.utc).isoformat(),
}


def _chain_mock(final_data):
    """
    Build a MagicMock whose arbitrary attribute / method chains
    always end with .execute() returning MagicMock(data=final_data).
    """
    sentinel = MagicMock()
    sentinel.execute.return_value = MagicMock(data=final_data)
    # Every chained call returns the same sentinel so that
    # .select().eq().eq()...execute() all resolve correctly.
    sentinel.select.return_value = sentinel
    sentinel.eq.return_value = sentinel
    sentinel.gte.return_value = sentinel
    sentinel.order.return_value = sentinel
    sentinel.limit.return_value = sentinel
    sentinel.insert.return_value = sentinel
    sentinel.upsert.return_value = sentinel
    sentinel.update.return_value = sentinel
    return sentinel


def _setup_auth_success(mock_sb, profile=None):
    """Configure mock so _verify_emergency_token returns a profile."""
    profile = profile or VALID_PROFILE
    mock_sb.table.return_value = _chain_mock([profile])


def _setup_auth_failure(mock_sb):
    """Configure mock so _verify_emergency_token returns None."""
    mock_sb.table.return_value = _chain_mock([])


# ── Payloads ───────────────────────────────────────────────

def _relay_payload(**overrides):
    base = {
        "victim_id": VALID_UUID,
        "emergency_token": "secure-token-12345",
        "lat": 12.9716,
        "lng": 77.5946,
        "trigger_method": "button",
        "risk_score": 75,
        "battery_level": 80,
    }
    base.update(overrides)
    return base


def _trigger_payload(**overrides):
    return _relay_payload(**overrides)


def _location_payload(**overrides):
    base = {
        "sos_id": SOS_UUID,
        "victim_id": VALID_UUID,
        "emergency_token": "secure-token-12345",
        "lat": 12.9717,
        "lng": 77.5947,
        "accuracy": 5.0,
        "speed": 1.2,
        "heading": 90.0,
        "battery_level": 75,
    }
    base.update(overrides)
    return base


def _resolve_payload(**overrides):
    base = {
        "sos_id": SOS_UUID,
        "victim_id": VALID_UUID,
        "secure_pin": "1234",
    }
    base.update(overrides)
    return base


# ============================================================
# 1. HEALTH CHECK  –  GET /
# ============================================================

class TestHealthCheck:

    def test_returns_ok(self, client):
        resp = client.get("/")
        assert resp.status_code == 200
        body = resp.json()
        assert body["status"] == "ok"
        assert body["service"] == "SHE-SHIELD"


# ============================================================
# 2. SOS RELAY  –  POST /api/sos/relay
# ============================================================

class TestSOSRelay:

    def _setup_new_sos(self, mock_supabase):
        """
        Configure mocks for a successful NEW SOS creation via relay.
        This needs to handle multiple table() calls in sequence:
        1. profiles table (auth) → returns profile
        2. sos_events table (idempotency check) → returns empty
        3. sos_events table (insert) → returns new row
        4. live_tracking table (insert) → success
        5. trusted_contacts table (background sms) → contacts
        """
        call_count = {"n": 0}
        profile_mock = _chain_mock([VALID_PROFILE])
        empty_mock = _chain_mock([])
        sos_insert_mock = _chain_mock([VALID_SOS_ROW])
        tracking_mock = _chain_mock([{"sos_id": SOS_UUID}])

        def table_router(table_name):
            call_count["n"] += 1
            if table_name == "profiles":
                return profile_mock
            elif table_name == "sos_events":
                # First sos_events call = idempotency check (empty)
                # Second sos_events call = insert (returns row)
                if call_count["n"] <= 2:
                    return empty_mock
                return sos_insert_mock
            elif table_name == "live_tracking":
                return tracking_mock
            elif table_name == "trusted_contacts":
                return _chain_mock([])
            return MagicMock()

        mock_supabase.table.side_effect = table_router

    def test_valid_relay_creates_sos(self, client, mock_supabase):
        self._setup_new_sos(mock_supabase)
        resp = client.post("/api/sos/relay", json=_relay_payload())
        assert resp.status_code == 201
        body = resp.json()
        assert body["success"] is True
        assert body["is_new"] is True
        assert body["sos_id"] == SOS_UUID

    def test_invalid_auth_returns_401(self, client, mock_supabase):
        _setup_auth_failure(mock_supabase)
        resp = client.post("/api/sos/relay", json=_relay_payload())
        assert resp.status_code == 401
        assert "Invalid victim_id or emergency_token" in resp.json()["detail"]

    def test_missing_victim_id_returns_422(self, client):
        payload = _relay_payload()
        del payload["victim_id"]
        resp = client.post("/api/sos/relay", json=payload)
        assert resp.status_code == 422

    def test_missing_emergency_token_returns_422(self, client):
        payload = _relay_payload()
        del payload["emergency_token"]
        resp = client.post("/api/sos/relay", json=payload)
        assert resp.status_code == 422

    def test_missing_lat_returns_422(self, client):
        payload = _relay_payload()
        del payload["lat"]
        resp = client.post("/api/sos/relay", json=payload)
        assert resp.status_code == 422

    def test_missing_lng_returns_422(self, client):
        payload = _relay_payload()
        del payload["lng"]
        resp = client.post("/api/sos/relay", json=payload)
        assert resp.status_code == 422

    def test_missing_trigger_method_returns_422(self, client):
        payload = _relay_payload()
        del payload["trigger_method"]
        resp = client.post("/api/sos/relay", json=payload)
        assert resp.status_code == 422

    def test_invalid_uuid_returns_422(self, client):
        resp = client.post(
            "/api/sos/relay",
            json=_relay_payload(victim_id="not-a-uuid"),
        )
        assert resp.status_code == 422

    def test_lat_out_of_range_returns_422(self, client):
        resp = client.post(
            "/api/sos/relay",
            json=_relay_payload(lat=91.0),
        )
        assert resp.status_code == 422

    def test_lat_negative_out_of_range_returns_422(self, client):
        resp = client.post(
            "/api/sos/relay",
            json=_relay_payload(lat=-91.0),
        )
        assert resp.status_code == 422

    def test_lng_out_of_range_returns_422(self, client):
        resp = client.post(
            "/api/sos/relay",
            json=_relay_payload(lng=181.0),
        )
        assert resp.status_code == 422

    def test_lng_negative_out_of_range_returns_422(self, client):
        resp = client.post(
            "/api/sos/relay",
            json=_relay_payload(lng=-181.0),
        )
        assert resp.status_code == 422

    def test_risk_score_above_100_returns_422(self, client):
        resp = client.post(
            "/api/sos/relay",
            json=_relay_payload(risk_score=101),
        )
        assert resp.status_code == 422

    def test_risk_score_negative_returns_422(self, client):
        resp = client.post(
            "/api/sos/relay",
            json=_relay_payload(risk_score=-1),
        )
        assert resp.status_code == 422

    def test_battery_above_100_returns_422(self, client):
        resp = client.post(
            "/api/sos/relay",
            json=_relay_payload(battery_level=101),
        )
        assert resp.status_code == 422

    def test_battery_negative_returns_422(self, client):
        resp = client.post(
            "/api/sos/relay",
            json=_relay_payload(battery_level=-1),
        )
        assert resp.status_code == 422

    def test_empty_trigger_method_returns_422(self, client):
        resp = client.post(
            "/api/sos/relay",
            json=_relay_payload(trigger_method=""),
        )
        assert resp.status_code == 422

    def test_short_emergency_token_returns_422(self, client):
        resp = client.post(
            "/api/sos/relay",
            json=_relay_payload(emergency_token="short"),
        )
        assert resp.status_code == 422

    def test_optional_fields_can_be_null(self, client, mock_supabase):
        self._setup_new_sos(mock_supabase)
        resp = client.post(
            "/api/sos/relay",
            json=_relay_payload(risk_score=None, battery_level=None),
        )
        assert resp.status_code == 201

    def test_idempotent_sos_returns_existing(self, client, mock_supabase):
        """If an active SOS exists within 2 min, return it (idempotent)."""
        existing = {"id": SOS_UUID, "created_at": datetime.now(timezone.utc).isoformat()}
        call_count = {"n": 0}

        profile_mock = _chain_mock([VALID_PROFILE])
        existing_mock = _chain_mock([existing])

        def table_router(table_name):
            call_count["n"] += 1
            if table_name == "profiles":
                return profile_mock
            elif table_name == "sos_events":
                return existing_mock
            return MagicMock()

        mock_supabase.table.side_effect = table_router

        resp = client.post("/api/sos/relay", json=_relay_payload())
        assert resp.status_code == 201
        body = resp.json()
        assert body["is_new"] is False
        assert body["sos_id"] == SOS_UUID

    def test_db_insert_failure_returns_500(self, client, mock_supabase):
        """If sos_events insert returns empty data, endpoint returns 500."""
        call_count = {"n": 0}
        profile_mock = _chain_mock([VALID_PROFILE])
        empty_mock = _chain_mock([])

        def table_router(table_name):
            call_count["n"] += 1
            if table_name == "profiles":
                return profile_mock
            # All sos_events calls return empty (idempotency empty + insert empty)
            return empty_mock

        mock_supabase.table.side_effect = table_router

        resp = client.post("/api/sos/relay", json=_relay_payload())
        assert resp.status_code == 500

    def test_empty_body_returns_422(self, client):
        resp = client.post("/api/sos/relay", json={})
        assert resp.status_code == 422

    def test_boundary_lat_values_accepted(self, client, mock_supabase):
        self._setup_new_sos(mock_supabase)
        # Exactly -90 and 90 should be valid
        for lat_val in [-90.0, 90.0]:
            resp = client.post(
                "/api/sos/relay",
                json=_relay_payload(lat=lat_val),
            )
            assert resp.status_code == 201

    def test_boundary_lng_values_accepted(self, client, mock_supabase):
        self._setup_new_sos(mock_supabase)
        for lng_val in [-180.0, 180.0]:
            resp = client.post(
                "/api/sos/relay",
                json=_relay_payload(lng=lng_val),
            )
            assert resp.status_code == 201


# ============================================================
# 3. SOS TRIGGER  –  POST /api/sos/trigger
# ============================================================

class TestSOSTrigger:

    def _setup_new_sos(self, mock_supabase):
        call_count = {"n": 0}
        profile_mock = _chain_mock([VALID_PROFILE])
        empty_mock = _chain_mock([])
        sos_insert_mock = _chain_mock([VALID_SOS_ROW])
        tracking_mock = _chain_mock([{"sos_id": SOS_UUID}])

        def table_router(table_name):
            call_count["n"] += 1
            if table_name == "profiles":
                return profile_mock
            elif table_name == "sos_events":
                if call_count["n"] <= 2:
                    return empty_mock
                return sos_insert_mock
            elif table_name == "live_tracking":
                return tracking_mock
            elif table_name == "trusted_contacts":
                return _chain_mock([])
            return MagicMock()

        mock_supabase.table.side_effect = table_router

    def test_valid_trigger_creates_sos(self, client, mock_supabase):
        self._setup_new_sos(mock_supabase)
        resp = client.post("/api/sos/trigger", json=_trigger_payload())
        assert resp.status_code == 201
        body = resp.json()
        assert body["success"] is True
        assert body["is_new"] is True
        assert body["sos_id"] == SOS_UUID

    def test_invalid_auth_returns_401(self, client, mock_supabase):
        _setup_auth_failure(mock_supabase)
        resp = client.post("/api/sos/trigger", json=_trigger_payload())
        assert resp.status_code == 401

    def test_missing_fields_returns_422(self, client):
        resp = client.post("/api/sos/trigger", json={})
        assert resp.status_code == 422

    def test_invalid_uuid_returns_422(self, client):
        resp = client.post(
            "/api/sos/trigger",
            json=_trigger_payload(victim_id="bad-uuid"),
        )
        assert resp.status_code == 422

    def test_lat_lng_range_validation(self, client):
        resp = client.post(
            "/api/sos/trigger",
            json=_trigger_payload(lat=100, lng=-200),
        )
        assert resp.status_code == 422

    def test_identical_to_relay_fields(self, client, mock_supabase):
        """Trigger endpoint accepts the same payload shape as relay."""
        self._setup_new_sos(mock_supabase)
        payload = _trigger_payload(
            trigger_method="shake",
            risk_score=50,
            battery_level=30,
        )
        resp = client.post("/api/sos/trigger", json=payload)
        assert resp.status_code == 201


# ============================================================
# 4. LOCATION UPDATE  –  POST /api/sos/location
# ============================================================

class TestLocationUpdate:

    def _setup_location_success(self, mock_supabase):
        """Mock for auth success + active SOS + upsert success."""
        call_count = {"n": 0}
        profile_mock = _chain_mock([VALID_PROFILE])
        active_sos_mock = _chain_mock([{"id": SOS_UUID}])
        upsert_mock = _chain_mock([{"sos_id": SOS_UUID}])

        def table_router(table_name):
            call_count["n"] += 1
            if table_name == "profiles":
                return profile_mock
            elif table_name == "sos_events":
                return active_sos_mock
            elif table_name == "live_tracking":
                return upsert_mock
            return MagicMock()

        mock_supabase.table.side_effect = table_router

    def test_valid_location_update(self, client, mock_supabase):
        self._setup_location_success(mock_supabase)
        resp = client.post("/api/sos/location", json=_location_payload())
        assert resp.status_code == 200
        body = resp.json()
        assert body["success"] is True
        assert body["sos_id"] == SOS_UUID

    def test_invalid_auth_returns_401(self, client, mock_supabase):
        _setup_auth_failure(mock_supabase)
        resp = client.post("/api/sos/location", json=_location_payload())
        assert resp.status_code == 401

    def test_no_active_sos_returns_404(self, client, mock_supabase):
        """After auth succeeds, SOS ownership check fails → 404."""
        call_count = {"n": 0}
        profile_mock = _chain_mock([VALID_PROFILE])
        empty_sos_mock = _chain_mock([])

        def table_router(table_name):
            call_count["n"] += 1
            if table_name == "profiles":
                return profile_mock
            elif table_name == "sos_events":
                return empty_sos_mock
            return MagicMock()

        mock_supabase.table.side_effect = table_router

        resp = client.post("/api/sos/location", json=_location_payload())
        assert resp.status_code == 404
        assert "No active SOS" in resp.json()["detail"]

    def test_missing_fields_returns_422(self, client):
        resp = client.post("/api/sos/location", json={})
        assert resp.status_code == 422

    def test_invalid_sos_uuid_returns_422(self, client):
        resp = client.post(
            "/api/sos/location",
            json=_location_payload(sos_id="not-uuid"),
        )
        assert resp.status_code == 422

    def test_invalid_victim_uuid_returns_422(self, client):
        resp = client.post(
            "/api/sos/location",
            json=_location_payload(victim_id="not-uuid"),
        )
        assert resp.status_code == 422

    def test_lat_out_of_range(self, client):
        resp = client.post(
            "/api/sos/location",
            json=_location_payload(lat=95.0),
        )
        assert resp.status_code == 422

    def test_lng_out_of_range(self, client):
        resp = client.post(
            "/api/sos/location",
            json=_location_payload(lng=-181.0),
        )
        assert resp.status_code == 422

    def test_accuracy_negative_returns_422(self, client):
        resp = client.post(
            "/api/sos/location",
            json=_location_payload(accuracy=-1.0),
        )
        assert resp.status_code == 422

    def test_speed_negative_returns_422(self, client):
        resp = client.post(
            "/api/sos/location",
            json=_location_payload(speed=-5.0),
        )
        assert resp.status_code == 422

    def test_heading_over_360_returns_422(self, client):
        resp = client.post(
            "/api/sos/location",
            json=_location_payload(heading=361.0),
        )
        assert resp.status_code == 422

    def test_battery_over_100_returns_422(self, client):
        resp = client.post(
            "/api/sos/location",
            json=_location_payload(battery_level=101),
        )
        assert resp.status_code == 422

    def test_optional_fields_can_be_null(self, client, mock_supabase):
        self._setup_location_success(mock_supabase)
        resp = client.post(
            "/api/sos/location",
            json=_location_payload(
                accuracy=None, speed=None, heading=None, battery_level=None
            ),
        )
        assert resp.status_code == 200

    def test_upsert_failure_returns_500(self, client, mock_supabase):
        """When the live_tracking upsert returns empty data → 500."""
        call_count = {"n": 0}
        profile_mock = _chain_mock([VALID_PROFILE])
        active_sos_mock = _chain_mock([{"id": SOS_UUID}])
        empty_upsert_mock = _chain_mock([])

        def table_router(table_name):
            call_count["n"] += 1
            if table_name == "profiles":
                return profile_mock
            elif table_name == "sos_events":
                return active_sos_mock
            elif table_name == "live_tracking":
                return empty_upsert_mock
            return MagicMock()

        mock_supabase.table.side_effect = table_router

        resp = client.post("/api/sos/location", json=_location_payload())
        assert resp.status_code == 500

    def test_short_emergency_token_returns_422(self, client):
        resp = client.post(
            "/api/sos/location",
            json=_location_payload(emergency_token="abc"),
        )
        assert resp.status_code == 422

    def test_boundary_heading_values_accepted(self, client, mock_supabase):
        self._setup_location_success(mock_supabase)
        for h in [0.0, 360.0]:
            resp = client.post(
                "/api/sos/location",
                json=_location_payload(heading=h),
            )
            assert resp.status_code == 200


# ============================================================
# 5. SOS RESOLVE  –  POST /api/sos/resolve
# ============================================================

class TestSOSResolve:

    def _setup_resolve_success(self, mock_supabase):
        """Mock for pin auth success + SOS update success."""
        call_count = {"n": 0}
        profile_mock = _chain_mock([VALID_PROFILE])
        update_mock = _chain_mock([{**VALID_SOS_ROW, "status": "resolved"}])

        def table_router(table_name):
            call_count["n"] += 1
            if table_name == "profiles":
                return profile_mock
            elif table_name == "sos_events":
                return update_mock
            elif table_name == "trusted_contacts":
                return _chain_mock([])
            return MagicMock()

        mock_supabase.table.side_effect = table_router

    def test_valid_resolve(self, client, mock_supabase):
        self._setup_resolve_success(mock_supabase)
        resp = client.post("/api/sos/resolve", json=_resolve_payload())
        assert resp.status_code == 200
        body = resp.json()
        assert body["success"] is True
        assert body["sos_id"] == SOS_UUID
        assert "resolved_at" in body

    def test_invalid_pin_returns_401(self, client, mock_supabase):
        _setup_auth_failure(mock_supabase)
        resp = client.post("/api/sos/resolve", json=_resolve_payload())
        assert resp.status_code == 401
        assert "secure_pin" in resp.json()["detail"]

    def test_no_active_sos_returns_404(self, client, mock_supabase):
        """Pin valid but no matching active SOS → 404."""
        call_count = {"n": 0}
        profile_mock = _chain_mock([VALID_PROFILE])
        empty_update_mock = _chain_mock([])

        def table_router(table_name):
            call_count["n"] += 1
            if table_name == "profiles":
                return profile_mock
            elif table_name == "sos_events":
                return empty_update_mock
            return MagicMock()

        mock_supabase.table.side_effect = table_router

        resp = client.post("/api/sos/resolve", json=_resolve_payload())
        assert resp.status_code == 404
        assert "No active SOS" in resp.json()["detail"]

    def test_missing_fields_returns_422(self, client):
        resp = client.post("/api/sos/resolve", json={})
        assert resp.status_code == 422

    def test_invalid_sos_uuid_returns_422(self, client):
        resp = client.post(
            "/api/sos/resolve",
            json=_resolve_payload(sos_id="bad"),
        )
        assert resp.status_code == 422

    def test_invalid_victim_uuid_returns_422(self, client):
        resp = client.post(
            "/api/sos/resolve",
            json=_resolve_payload(victim_id="bad"),
        )
        assert resp.status_code == 422

    def test_short_pin_returns_422(self, client):
        resp = client.post(
            "/api/sos/resolve",
            json=_resolve_payload(secure_pin="12"),
        )
        assert resp.status_code == 422

    def test_empty_body_returns_422(self, client):
        resp = client.post("/api/sos/resolve", json={})
        assert resp.status_code == 422


# ============================================================
# 6. EDGE CASES / MISC
# ============================================================

class TestEdgeCases:

    def test_wrong_http_method_on_relay(self, client):
        resp = client.get("/api/sos/relay")
        assert resp.status_code == 405

    def test_wrong_http_method_on_trigger(self, client):
        resp = client.get("/api/sos/trigger")
        assert resp.status_code == 405

    def test_wrong_http_method_on_location(self, client):
        resp = client.get("/api/sos/location")
        assert resp.status_code == 405

    def test_wrong_http_method_on_resolve(self, client):
        resp = client.get("/api/sos/resolve")
        assert resp.status_code == 405

    def test_nonexistent_route_returns_404(self, client):
        resp = client.get("/api/does-not-exist")
        assert resp.status_code == 404

    def test_content_type_not_json_returns_422(self, client):
        resp = client.post(
            "/api/sos/relay",
            data="not json at all",
            headers={"Content-Type": "text/plain"},
        )
        assert resp.status_code == 422

    def test_docs_endpoint_available(self, client):
        """FastAPI auto-generates /docs (Swagger UI)."""
        resp = client.get("/docs")
        assert resp.status_code == 200

    def test_openapi_json_available(self, client):
        """FastAPI auto-generates /openapi.json."""
        resp = client.get("/openapi.json")
        assert resp.status_code == 200
        schema = resp.json()
        assert "paths" in schema
        assert "/api/sos/relay" in schema["paths"]
        assert "/api/sos/trigger" in schema["paths"]
        assert "/api/sos/location" in schema["paths"]
        assert "/api/sos/resolve" in schema["paths"]
