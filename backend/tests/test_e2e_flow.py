"""
============================================================
SHE-SHIELD Backend – End-to-End Pipeline Test
============================================================
Comprehensive E2E flow test that exercises the FULL SOS
lifecycle:

    1. Health check
    2. Trigger SOS (direct online)
    3. Relay SOS (Bluetooth mesh)
    4. Live-tracking location update
    5. Resolve SOS (victim marks safe)
    6. Edge cases & negative paths

Two modes:
  • DEFAULT (pytest):  Uses FastAPI TestClient with mocked
    Supabase.  No network needed.  Run with:
        cd backend && python -m pytest tests/test_e2e_flow.py -v -s

  • LIVE SERVER:  Set env var E2E_BASE_URL to hit a real
    running instance.  Requires real Supabase data.
        E2E_BASE_URL=http://localhost:8000 python -m pytest tests/test_e2e_flow.py -v -s

============================================================
"""

import os
import sys
import uuid
from datetime import datetime, timezone
from typing import Any, Dict, Optional
from unittest.mock import MagicMock

import pytest

# ── Ensure backend/ is on sys.path ────────────────────────
sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

# ── Import from conftest (mocked Supabase already active) ─
# The conftest.py patches create_client before main.py loads.


# ============================================================
# FIXTURES
# ============================================================

# Test identity constants
VICTIM_ID = str(uuid.uuid4())
VICTIM_NAME = "E2E Test Victim"
VICTIM_PHONE = "+919876543210"
EMERGENCY_TOKEN = "e2e-secure-token-9999"
SECURE_PIN = "5678"


def _chain_mock(final_data):
    """
    Build a MagicMock whose .select().eq()...execute() chain
    always returns MagicMock(data=final_data).
    """
    sentinel = MagicMock()
    sentinel.execute.return_value = MagicMock(data=final_data)
    sentinel.select.return_value = sentinel
    sentinel.eq.return_value = sentinel
    sentinel.gte.return_value = sentinel
    sentinel.order.return_value = sentinel
    sentinel.limit.return_value = sentinel
    sentinel.insert.return_value = sentinel
    sentinel.upsert.return_value = sentinel
    sentinel.update.return_value = sentinel
    return sentinel


PROFILE_ROW = {
    "id": VICTIM_ID,
    "full_name": VICTIM_NAME,
    "phone_number": VICTIM_PHONE,
}

# Will be populated by the trigger test so subsequent tests can use it
_shared_state: Dict[str, Any] = {}


# ============================================================
# 1. HEALTH CHECK
# ============================================================

class TestHealthCheck:
    """GET / should always return 200 with status ok."""

    def test_health_returns_ok(self, client):
        print("\n" + "=" * 70)
        print("TEST: Health Check – GET /")
        print("=" * 70)

        resp = client.get("/")
        print(f"  → Status Code : {resp.status_code}")
        print(f"  → Response    : {resp.json()}")

        assert resp.status_code == 200, f"Expected 200, got {resp.status_code}"
        body = resp.json()
        assert body["status"] == "ok"
        assert body["service"] == "SHE-SHIELD"
        print("  ✅ Health check PASSED")


# ============================================================
# 2. TRIGGER SOS – POST /api/sos/trigger
# ============================================================

class TestSOSTrigger:
    """Full trigger flow: authenticate → create SOS → return 201."""

    def test_trigger_sos_success(self, client, mock_supabase):
        print("\n" + "=" * 70)
        print("TEST: Trigger SOS – POST /api/sos/trigger")
        print("=" * 70)

        sos_id = str(uuid.uuid4())
        sos_row = {
            "id": sos_id,
            "victim_id": VICTIM_ID,
            "trigger_method": "scream_ai",
            "risk_score": 85,
            "initial_lat": 12.9716,
            "initial_lng": 77.5946,
            "status": "active",
            "relay_ip": "127.0.0.1",
            "created_at": datetime.now(timezone.utc).isoformat(),
        }

        # Mock: profile lookup succeeds, no active SOS, insert succeeds
        call_count = {"n": 0}
        def table_router(table_name):
            call_count["n"] += 1
            if table_name == "profiles":
                return _chain_mock([PROFILE_ROW])
            elif table_name == "sos_events":
                # First call = idempotency check (empty), second = insert
                if call_count["n"] <= 2:
                    return _chain_mock([])      # no existing SOS
                return _chain_mock([sos_row])   # insert returns row
            elif table_name == "live_tracking":
                return _chain_mock([{"sos_id": sos_id}])
            elif table_name == "trusted_contacts":
                return _chain_mock([])
            return _chain_mock([])

        mock_supabase.table.side_effect = table_router

        payload = {
            "victim_id": VICTIM_ID,
            "emergency_token": EMERGENCY_TOKEN,
            "lat": 12.9716,
            "lng": 77.5946,
            "trigger_method": "scream_ai",
            "risk_score": 85,
            "battery_level": 72,
        }

        print(f"  → Payload     : {payload}")
        resp = client.post("/api/sos/trigger", json=payload)
        print(f"  → Status Code : {resp.status_code}")
        print(f"  → Response    : {resp.json()}")

        assert resp.status_code == 201, f"Expected 201, got {resp.status_code}"
        body = resp.json()
        assert body["success"] is True
        assert body["is_new"] is True
        assert body["sos_id"] == sos_id
        print(f"  ✅ SOS created with ID: {sos_id}")

        # Save for later tests
        _shared_state["sos_id"] = sos_id
        _shared_state["victim_id"] = VICTIM_ID

    def test_trigger_sos_invalid_token(self, client, mock_supabase):
        print("\n" + "=" * 70)
        print("TEST: Trigger SOS – Invalid Token (should 401)")
        print("=" * 70)

        mock_supabase.table.return_value = _chain_mock([])  # auth fails

        payload = {
            "victim_id": VICTIM_ID,
            "emergency_token": "wrong-token-lol",
            "lat": 12.97,
            "lng": 77.59,
            "trigger_method": "button",
        }

        resp = client.post("/api/sos/trigger", json=payload)
        print(f"  → Status Code : {resp.status_code}")
        print(f"  → Response    : {resp.json()}")

        assert resp.status_code == 401
        assert "Invalid" in resp.json()["detail"]
        print("  ✅ Correctly rejected with 401 Unauthorized")

    def test_trigger_sos_invalid_body(self, client):
        print("\n" + "=" * 70)
        print("TEST: Trigger SOS – Missing fields (should 422)")
        print("=" * 70)

        resp = client.post("/api/sos/trigger", json={"victim_id": VICTIM_ID})
        print(f"  → Status Code : {resp.status_code}")
        print(f"  → Errors      : {resp.json()['detail'][:200]}...")

        assert resp.status_code == 422
        print("  ✅ Correctly rejected with 422 Validation Error")

    def test_trigger_sos_idempotent(self, client, mock_supabase):
        print("\n" + "=" * 70)
        print("TEST: Trigger SOS – Idempotent duplicate (should reuse)")
        print("=" * 70)

        existing_sos_id = str(uuid.uuid4())
        existing_row = {"id": existing_sos_id, "created_at": datetime.now(timezone.utc).isoformat()}

        call_count = {"n": 0}
        def table_router(name):
            call_count["n"] += 1
            if name == "profiles":
                return _chain_mock([PROFILE_ROW])
            elif name == "sos_events":
                return _chain_mock([existing_row])  # active SOS exists
            return _chain_mock([])

        mock_supabase.table.side_effect = table_router

        payload = {
            "victim_id": VICTIM_ID,
            "emergency_token": EMERGENCY_TOKEN,
            "lat": 12.97,
            "lng": 77.59,
            "trigger_method": "button",
        }

        resp = client.post("/api/sos/trigger", json=payload)
        print(f"  → Status Code : {resp.status_code}")
        print(f"  → Response    : {resp.json()}")

        assert resp.status_code == 201
        body = resp.json()
        assert body["is_new"] is False
        assert body["sos_id"] == existing_sos_id
        print(f"  ✅ Idempotent – returned existing SOS {existing_sos_id}")

    def test_trigger_sos_lat_out_of_range(self, client):
        print("\n" + "=" * 70)
        print("TEST: Trigger SOS – Latitude > 90 (should 422)")
        print("=" * 70)

        payload = {
            "victim_id": VICTIM_ID,
            "emergency_token": EMERGENCY_TOKEN,
            "lat": 91.0,
            "lng": 77.59,
            "trigger_method": "button",
        }

        resp = client.post("/api/sos/trigger", json=payload)
        print(f"  → Status Code : {resp.status_code}")
        assert resp.status_code == 422
        print("  ✅ Correctly rejected lat > 90")

    def test_trigger_sos_lng_out_of_range(self, client):
        print("\n" + "=" * 70)
        print("TEST: Trigger SOS – Longitude < -180 (should 422)")
        print("=" * 70)

        payload = {
            "victim_id": VICTIM_ID,
            "emergency_token": EMERGENCY_TOKEN,
            "lat": 12.97,
            "lng": -181.0,
            "trigger_method": "button",
        }

        resp = client.post("/api/sos/trigger", json=payload)
        print(f"  → Status Code : {resp.status_code}")
        assert resp.status_code == 422
        print("  ✅ Correctly rejected lng < -180")


# ============================================================
# 3. RELAY SOS – POST /api/sos/relay
# ============================================================

class TestSOSRelay:
    """Bluetooth mesh relay endpoint – same auth, different path."""

    def test_relay_sos_success(self, client, mock_supabase):
        print("\n" + "=" * 70)
        print("TEST: Relay SOS – POST /api/sos/relay")
        print("=" * 70)

        sos_id = str(uuid.uuid4())
        sos_row = {
            "id": sos_id,
            "victim_id": VICTIM_ID,
            "trigger_method": "shake",
            "risk_score": 60,
            "initial_lat": 13.0827,
            "initial_lng": 80.2707,
            "status": "active",
            "relay_ip": "10.0.0.42",
            "created_at": datetime.now(timezone.utc).isoformat(),
        }

        call_count = {"n": 0}
        def table_router(name):
            call_count["n"] += 1
            if name == "profiles":
                return _chain_mock([PROFILE_ROW])
            elif name == "sos_events":
                if call_count["n"] <= 2:
                    return _chain_mock([])
                return _chain_mock([sos_row])
            elif name == "live_tracking":
                return _chain_mock([{"sos_id": sos_id}])
            elif name == "trusted_contacts":
                return _chain_mock([])
            return _chain_mock([])

        mock_supabase.table.side_effect = table_router

        payload = {
            "victim_id": VICTIM_ID,
            "emergency_token": EMERGENCY_TOKEN,
            "lat": 13.0827,
            "lng": 80.2707,
            "trigger_method": "shake",
            "risk_score": 60,
            "battery_level": 45,
        }

        print(f"  → Payload     : {payload}")
        resp = client.post("/api/sos/relay", json=payload)
        print(f"  → Status Code : {resp.status_code}")
        print(f"  → Response    : {resp.json()}")

        assert resp.status_code == 201
        body = resp.json()
        assert body["success"] is True
        assert body["is_new"] is True
        print(f"  ✅ Relay SOS created with ID: {sos_id}")

    def test_relay_sos_invalid_token(self, client, mock_supabase):
        print("\n" + "=" * 70)
        print("TEST: Relay SOS – Invalid token (should 401)")
        print("=" * 70)

        mock_supabase.table.return_value = _chain_mock([])

        payload = {
            "victim_id": VICTIM_ID,
            "emergency_token": "bad-token-xxxx",
            "lat": 13.08,
            "lng": 80.27,
            "trigger_method": "button",
        }

        resp = client.post("/api/sos/relay", json=payload)
        print(f"  → Status Code : {resp.status_code}")
        assert resp.status_code == 401
        print("  ✅ Relay correctly rejected with 401")

    def test_relay_missing_trigger_method(self, client):
        print("\n" + "=" * 70)
        print("TEST: Relay SOS – Missing trigger_method (should 422)")
        print("=" * 70)

        payload = {
            "victim_id": VICTIM_ID,
            "emergency_token": EMERGENCY_TOKEN,
            "lat": 13.08,
            "lng": 80.27,
            # trigger_method intentionally omitted
        }

        resp = client.post("/api/sos/relay", json=payload)
        print(f"  → Status Code : {resp.status_code}")
        assert resp.status_code == 422
        print("  ✅ Missing field rejected with 422")


# ============================================================
# 4. LIVE TRACKING – POST /api/sos/location
# ============================================================

class TestLocationUpdate:
    """Live-tracking telemetry upsert endpoint."""

    def test_location_update_success(self, client, mock_supabase):
        print("\n" + "=" * 70)
        print("TEST: Location Update – POST /api/sos/location")
        print("=" * 70)

        sos_id = _shared_state.get("sos_id", str(uuid.uuid4()))

        call_count = {"n": 0}
        def table_router(name):
            call_count["n"] += 1
            if name == "profiles":
                return _chain_mock([PROFILE_ROW])
            elif name == "sos_events":
                # ownership check returns active SOS
                return _chain_mock([{"id": sos_id}])
            elif name == "live_tracking":
                return _chain_mock([{"sos_id": sos_id, "lat": 12.9720, "lng": 77.5950}])
            return _chain_mock([])

        mock_supabase.table.side_effect = table_router

        payload = {
            "sos_id": sos_id,
            "victim_id": VICTIM_ID,
            "emergency_token": EMERGENCY_TOKEN,
            "lat": 12.9720,
            "lng": 77.5950,
            "accuracy": 4.5,
            "speed": 1.8,
            "heading": 120.0,
            "battery_level": 68,
        }

        print(f"  → Payload     : {payload}")
        resp = client.post("/api/sos/location", json=payload)
        print(f"  → Status Code : {resp.status_code}")
        print(f"  → Response    : {resp.json()}")

        assert resp.status_code == 200, f"Expected 200, got {resp.status_code}"
        body = resp.json()
        assert body["success"] is True
        assert body["sos_id"] == sos_id
        print(f"  ✅ Location upserted for SOS {sos_id}")

    def test_location_update_minimal_fields(self, client, mock_supabase):
        print("\n" + "=" * 70)
        print("TEST: Location Update – Only required fields")
        print("=" * 70)

        sos_id = str(uuid.uuid4())

        call_count = {"n": 0}
        def table_router(name):
            call_count["n"] += 1
            if name == "profiles":
                return _chain_mock([PROFILE_ROW])
            elif name == "sos_events":
                return _chain_mock([{"id": sos_id}])
            elif name == "live_tracking":
                return _chain_mock([{"sos_id": sos_id}])
            return _chain_mock([])

        mock_supabase.table.side_effect = table_router

        payload = {
            "sos_id": sos_id,
            "victim_id": VICTIM_ID,
            "emergency_token": EMERGENCY_TOKEN,
            "lat": 12.98,
            "lng": 77.60,
        }

        resp = client.post("/api/sos/location", json=payload)
        print(f"  → Status Code : {resp.status_code}")
        print(f"  → Response    : {resp.json()}")

        assert resp.status_code == 200
        print("  ✅ Minimal location update accepted")

    def test_location_update_invalid_token(self, client, mock_supabase):
        print("\n" + "=" * 70)
        print("TEST: Location Update – Bad token (should 401)")
        print("=" * 70)

        mock_supabase.table.return_value = _chain_mock([])

        payload = {
            "sos_id": str(uuid.uuid4()),
            "victim_id": VICTIM_ID,
            "emergency_token": "nope-wrong",
            "lat": 12.97,
            "lng": 77.59,
        }

        resp = client.post("/api/sos/location", json=payload)
        print(f"  → Status Code : {resp.status_code}")
        assert resp.status_code == 401
        print("  ✅ Bad token rejected with 401")

    def test_location_update_no_active_sos(self, client, mock_supabase):
        print("\n" + "=" * 70)
        print("TEST: Location Update – SOS not found (should 404)")
        print("=" * 70)

        call_count = {"n": 0}
        def table_router(name):
            call_count["n"] += 1
            if name == "profiles":
                return _chain_mock([PROFILE_ROW])
            elif name == "sos_events":
                return _chain_mock([])  # no active SOS
            return _chain_mock([])

        mock_supabase.table.side_effect = table_router

        payload = {
            "sos_id": str(uuid.uuid4()),
            "victim_id": VICTIM_ID,
            "emergency_token": EMERGENCY_TOKEN,
            "lat": 12.97,
            "lng": 77.59,
        }

        resp = client.post("/api/sos/location", json=payload)
        print(f"  → Status Code : {resp.status_code}")
        assert resp.status_code == 404
        print("  ✅ Missing SOS correctly returned 404")

    def test_location_update_invalid_accuracy(self, client):
        print("\n" + "=" * 70)
        print("TEST: Location Update – Negative accuracy (should 422)")
        print("=" * 70)

        payload = {
            "sos_id": str(uuid.uuid4()),
            "victim_id": VICTIM_ID,
            "emergency_token": EMERGENCY_TOKEN,
            "lat": 12.97,
            "lng": 77.59,
            "accuracy": -5.0,
        }

        resp = client.post("/api/sos/location", json=payload)
        print(f"  → Status Code : {resp.status_code}")
        assert resp.status_code == 422
        print("  ✅ Negative accuracy rejected")

    def test_location_update_heading_over_360(self, client):
        print("\n" + "=" * 70)
        print("TEST: Location Update – Heading > 360 (should 422)")
        print("=" * 70)

        payload = {
            "sos_id": str(uuid.uuid4()),
            "victim_id": VICTIM_ID,
            "emergency_token": EMERGENCY_TOKEN,
            "lat": 12.97,
            "lng": 77.59,
            "heading": 361.0,
        }

        resp = client.post("/api/sos/location", json=payload)
        print(f"  → Status Code : {resp.status_code}")
        assert resp.status_code == 422
        print("  ✅ Heading > 360 rejected")

    def test_location_update_multiple_sequential(self, client, mock_supabase):
        """Simulate 5 rapid GPS pings to test upsert stability."""
        print("\n" + "=" * 70)
        print("TEST: Location Update – 5 rapid sequential updates")
        print("=" * 70)

        sos_id = str(uuid.uuid4())

        def table_router(name):
            if name == "profiles":
                return _chain_mock([PROFILE_ROW])
            elif name == "sos_events":
                return _chain_mock([{"id": sos_id}])
            elif name == "live_tracking":
                return _chain_mock([{"sos_id": sos_id}])
            return _chain_mock([])

        mock_supabase.table.side_effect = table_router

        for i in range(5):
            payload = {
                "sos_id": sos_id,
                "victim_id": VICTIM_ID,
                "emergency_token": EMERGENCY_TOKEN,
                "lat": 12.97 + (i * 0.001),
                "lng": 77.59 + (i * 0.001),
                "speed": float(i),
                "battery_level": 70 - i,
            }

            resp = client.post("/api/sos/location", json=payload)
            print(f"  → Ping {i+1}/5 : lat={payload['lat']:.4f} → {resp.status_code}")
            assert resp.status_code == 200

        print("  ✅ All 5 rapid location updates accepted")


# ============================================================
# 5. RESOLVE SOS – POST /api/sos/resolve
# ============================================================

class TestSOSResolve:
    """Victim marks themselves safe with secure_pin."""

    def test_resolve_sos_success(self, client, mock_supabase):
        print("\n" + "=" * 70)
        print("TEST: Resolve SOS – POST /api/sos/resolve")
        print("=" * 70)

        sos_id = _shared_state.get("sos_id", str(uuid.uuid4()))
        now_utc = datetime.now(timezone.utc).isoformat()

        resolved_row = {
            "id": sos_id,
            "victim_id": VICTIM_ID,
            "status": "resolved",
            "resolved_at": now_utc,
        }

        call_count = {"n": 0}
        def table_router(name):
            call_count["n"] += 1
            if name == "profiles":
                return _chain_mock([PROFILE_ROW])
            elif name == "sos_events":
                return _chain_mock([resolved_row])
            elif name == "trusted_contacts":
                return _chain_mock([])
            return _chain_mock([])

        mock_supabase.table.side_effect = table_router

        payload = {
            "sos_id": sos_id,
            "victim_id": VICTIM_ID,
            "secure_pin": SECURE_PIN,
        }

        print(f"  → Payload     : {payload}")
        resp = client.post("/api/sos/resolve", json=payload)
        print(f"  → Status Code : {resp.status_code}")
        print(f"  → Response    : {resp.json()}")

        assert resp.status_code == 200, f"Expected 200, got {resp.status_code}"
        body = resp.json()
        assert body["success"] is True
        assert body["sos_id"] == sos_id
        assert "resolved_at" in body
        print(f"  ✅ SOS {sos_id} resolved successfully")

    def test_resolve_sos_wrong_pin(self, client, mock_supabase):
        print("\n" + "=" * 70)
        print("TEST: Resolve SOS – Wrong PIN (should 401)")
        print("=" * 70)

        mock_supabase.table.return_value = _chain_mock([])

        payload = {
            "sos_id": str(uuid.uuid4()),
            "victim_id": VICTIM_ID,
            "secure_pin": "0000",
        }

        resp = client.post("/api/sos/resolve", json=payload)
        print(f"  → Status Code : {resp.status_code}")
        assert resp.status_code == 401
        print("  ✅ Wrong PIN rejected with 401")

    def test_resolve_sos_not_found(self, client, mock_supabase):
        print("\n" + "=" * 70)
        print("TEST: Resolve SOS – SOS not active (should 404)")
        print("=" * 70)

        call_count = {"n": 0}
        def table_router(name):
            call_count["n"] += 1
            if name == "profiles":
                return _chain_mock([PROFILE_ROW])
            elif name == "sos_events":
                return _chain_mock([])  # update matched nothing
            return _chain_mock([])

        mock_supabase.table.side_effect = table_router

        payload = {
            "sos_id": str(uuid.uuid4()),
            "victim_id": VICTIM_ID,
            "secure_pin": SECURE_PIN,
        }

        resp = client.post("/api/sos/resolve", json=payload)
        print(f"  → Status Code : {resp.status_code}")
        assert resp.status_code == 404
        print("  ✅ Non-existent SOS rejected with 404")

    def test_resolve_short_pin(self, client):
        print("\n" + "=" * 70)
        print("TEST: Resolve SOS – PIN too short (should 422)")
        print("=" * 70)

        payload = {
            "sos_id": str(uuid.uuid4()),
            "victim_id": VICTIM_ID,
            "secure_pin": "12",  # min_length=4
        }

        resp = client.post("/api/sos/resolve", json=payload)
        print(f"  → Status Code : {resp.status_code}")
        assert resp.status_code == 422
        print("  ✅ Short PIN rejected with 422")


# ============================================================
# 6. FULL LIFECYCLE E2E – Trigger → Track → Resolve
# ============================================================

class TestFullLifecycle:
    """
    The crown jewel: exercises the complete pipeline in order.
    Simulates a real-world SOS from scream detection through
    resolution.
    """

    def test_complete_sos_lifecycle(self, client, mock_supabase):
        print("\n" + "=" * 70)
        print("TEST: FULL LIFECYCLE – Trigger → Location × 3 → Resolve")
        print("=" * 70)

        victim_id = str(uuid.uuid4())
        sos_id = str(uuid.uuid4())
        profile = {
            "id": victim_id,
            "full_name": "Lifecycle Victim",
            "phone_number": "+919999999999",
        }

        # ── PHASE 1: Trigger SOS ─────────────────────────────
        print("\n  ── PHASE 1: Triggering SOS ──")

        phase = {"current": "trigger"}

        def table_router(name):
            if name == "profiles":
                return _chain_mock([profile])
            elif name == "sos_events":
                if phase["current"] == "trigger":
                    # First = idempotency check (empty), then insert
                    call = _chain_mock([])
                    insert_sentinel = _chain_mock([{
                        "id": sos_id,
                        "victim_id": victim_id,
                        "trigger_method": "scream_ai",
                        "status": "active",
                        "created_at": datetime.now(timezone.utc).isoformat(),
                    }])
                    call.insert.return_value = insert_sentinel
                    return call
                elif phase["current"] == "location":
                    return _chain_mock([{"id": sos_id}])  # ownership check
                elif phase["current"] == "resolve":
                    now_utc = datetime.now(timezone.utc).isoformat()
                    return _chain_mock([{
                        "id": sos_id,
                        "status": "resolved",
                        "resolved_at": now_utc,
                    }])
            elif name == "live_tracking":
                return _chain_mock([{"sos_id": sos_id}])
            elif name == "trusted_contacts":
                return _chain_mock([{
                    "contact_phone": "+918888888888",
                    "contact_name": "Mom",
                }])
            return _chain_mock([])

        mock_supabase.table.side_effect = table_router

        trigger_payload = {
            "victim_id": victim_id,
            "emergency_token": EMERGENCY_TOKEN,
            "lat": 12.9716,
            "lng": 77.5946,
            "trigger_method": "scream_ai",
            "risk_score": 90,
            "battery_level": 85,
        }

        resp = client.post("/api/sos/trigger", json=trigger_payload)
        print(f"    → Status: {resp.status_code} | SOS ID: {resp.json().get('sos_id', 'N/A')}")
        assert resp.status_code == 201
        returned_sos_id = resp.json()["sos_id"]
        print(f"    ✅ SOS triggered: {returned_sos_id}")

        # ── PHASE 2: Send 3 location updates ─────────────────
        phase["current"] = "location"
        print("\n  ── PHASE 2: Sending 3 location pings ──")

        coords = [
            (12.9720, 77.5950, 1.2),
            (12.9725, 77.5955, 2.5),
            (12.9730, 77.5960, 3.0),
        ]

        for i, (lat, lng, speed) in enumerate(coords, 1):
            loc_payload = {
                "sos_id": returned_sos_id,
                "victim_id": victim_id,
                "emergency_token": EMERGENCY_TOKEN,
                "lat": lat,
                "lng": lng,
                "speed": speed,
                "battery_level": 85 - (i * 3),
            }

            resp = client.post("/api/sos/location", json=loc_payload)
            print(f"    → Ping {i}/3: lat={lat}, lng={lng} → {resp.status_code}")
            assert resp.status_code == 200

        print("    ✅ All 3 location updates accepted")

        # ── PHASE 3: Resolve SOS ──────────────────────────────
        phase["current"] = "resolve"
        print("\n  ── PHASE 3: Resolving SOS ──")

        resolve_payload = {
            "sos_id": returned_sos_id,
            "victim_id": victim_id,
            "secure_pin": SECURE_PIN,
        }

        resp = client.post("/api/sos/resolve", json=resolve_payload)
        print(f"    → Status: {resp.status_code}")
        print(f"    → Body:   {resp.json()}")
        assert resp.status_code == 200
        assert resp.json()["success"] is True
        print(f"    ✅ SOS {returned_sos_id} resolved – victim safe!")

        print("\n  " + "=" * 66)
        print("  🎉  FULL LIFECYCLE PASSED  🎉")
        print("  " + "=" * 66)


# ============================================================
# 7. EDGE CASES & BOUNDARY TESTS
# ============================================================

class TestEdgeCases:
    """Additional boundary and stress scenarios."""

    def test_invalid_uuid_format(self, client):
        print("\n" + "=" * 70)
        print("TEST: Invalid UUID format in victim_id")
        print("=" * 70)

        payload = {
            "victim_id": "not-a-uuid",
            "emergency_token": EMERGENCY_TOKEN,
            "lat": 12.97,
            "lng": 77.59,
            "trigger_method": "button",
        }

        resp = client.post("/api/sos/trigger", json=payload)
        print(f"  → Status Code : {resp.status_code}")
        assert resp.status_code == 422
        print("  ✅ Invalid UUID rejected with 422")

    def test_empty_body(self, client):
        print("\n" + "=" * 70)
        print("TEST: Completely empty body")
        print("=" * 70)

        resp = client.post("/api/sos/trigger", json={})
        print(f"  → Status Code : {resp.status_code}")
        assert resp.status_code == 422
        print("  ✅ Empty body rejected with 422")

    def test_battery_over_100(self, client):
        print("\n" + "=" * 70)
        print("TEST: Battery level > 100 (should 422)")
        print("=" * 70)

        payload = {
            "victim_id": VICTIM_ID,
            "emergency_token": EMERGENCY_TOKEN,
            "lat": 12.97,
            "lng": 77.59,
            "trigger_method": "button",
            "battery_level": 150,
        }

        resp = client.post("/api/sos/trigger", json=payload)
        print(f"  → Status Code : {resp.status_code}")
        assert resp.status_code == 422
        print("  ✅ Battery > 100 rejected")

    def test_risk_score_negative(self, client):
        print("\n" + "=" * 70)
        print("TEST: Risk score < 0 (should 422)")
        print("=" * 70)

        payload = {
            "victim_id": VICTIM_ID,
            "emergency_token": EMERGENCY_TOKEN,
            "lat": 12.97,
            "lng": 77.59,
            "trigger_method": "button",
            "risk_score": -10,
        }

        resp = client.post("/api/sos/trigger", json=payload)
        print(f"  → Status Code : {resp.status_code}")
        assert resp.status_code == 422
        print("  ✅ Negative risk score rejected")

    def test_risk_score_over_100(self, client):
        print("\n" + "=" * 70)
        print("TEST: Risk score > 100 (should 422)")
        print("=" * 70)

        payload = {
            "victim_id": VICTIM_ID,
            "emergency_token": EMERGENCY_TOKEN,
            "lat": 12.97,
            "lng": 77.59,
            "trigger_method": "button",
            "risk_score": 101,
        }

        resp = client.post("/api/sos/trigger", json=payload)
        print(f"  → Status Code : {resp.status_code}")
        assert resp.status_code == 422
        print("  ✅ Risk score > 100 rejected")

    def test_empty_trigger_method(self, client):
        print("\n" + "=" * 70)
        print("TEST: Empty trigger_method (should 422, min_length=1)")
        print("=" * 70)

        payload = {
            "victim_id": VICTIM_ID,
            "emergency_token": EMERGENCY_TOKEN,
            "lat": 12.97,
            "lng": 77.59,
            "trigger_method": "",
        }

        resp = client.post("/api/sos/trigger", json=payload)
        print(f"  → Status Code : {resp.status_code}")
        assert resp.status_code == 422
        print("  ✅ Empty trigger_method rejected")

    def test_short_emergency_token(self, client):
        print("\n" + "=" * 70)
        print("TEST: Emergency token too short (< 8 chars, should 422)")
        print("=" * 70)

        payload = {
            "victim_id": VICTIM_ID,
            "emergency_token": "short",
            "lat": 12.97,
            "lng": 77.59,
            "trigger_method": "button",
        }

        resp = client.post("/api/sos/trigger", json=payload)
        print(f"  → Status Code : {resp.status_code}")
        assert resp.status_code == 422
        print("  ✅ Short token rejected")

    def test_wrong_http_method(self, client):
        print("\n" + "=" * 70)
        print("TEST: GET on POST-only endpoint (should 405)")
        print("=" * 70)

        resp = client.get("/api/sos/trigger")
        print(f"  → Status Code : {resp.status_code}")
        assert resp.status_code == 405
        print("  ✅ Wrong HTTP method rejected with 405")

    def test_nonexistent_endpoint(self, client):
        print("\n" + "=" * 70)
        print("TEST: 404 on non-existent route")
        print("=" * 70)

        resp = client.get("/api/nonexistent")
        print(f"  → Status Code : {resp.status_code}")
        assert resp.status_code == 404
        print("  ✅ Non-existent route returned 404")

    def test_extreme_coordinates(self, client, mock_supabase):
        """Lat/lng at exact boundaries: (-90, -180) and (90, 180)."""
        print("\n" + "=" * 70)
        print("TEST: Extreme boundary coordinates (-90/-180 and 90/180)")
        print("=" * 70)

        sos_id = str(uuid.uuid4())

        call_count = {"n": 0}
        def table_router(name):
            call_count["n"] += 1
            if name == "profiles":
                return _chain_mock([PROFILE_ROW])
            elif name == "sos_events":
                if call_count["n"] <= 2:
                    return _chain_mock([])
                return _chain_mock([{
                    "id": sos_id,
                    "victim_id": VICTIM_ID,
                    "trigger_method": "button",
                    "status": "active",
                    "created_at": datetime.now(timezone.utc).isoformat(),
                }])
            elif name in ("live_tracking", "trusted_contacts"):
                return _chain_mock([])
            return _chain_mock([])

        mock_supabase.table.side_effect = table_router

        # South Pole / Antimeridian
        payload = {
            "victim_id": VICTIM_ID,
            "emergency_token": EMERGENCY_TOKEN,
            "lat": -90.0,
            "lng": -180.0,
            "trigger_method": "button",
        }

        resp = client.post("/api/sos/trigger", json=payload)
        print(f"  → (-90, -180) → Status: {resp.status_code}")
        assert resp.status_code == 201
        print("  ✅ Extreme coordinates accepted")


# ============================================================
# SUMMARY PRINTER
# ============================================================

def pytest_terminal_summary(terminalreporter, exitstatus, config):
    """Custom summary printed at the end of the test run."""
    passed = len(terminalreporter.stats.get("passed", []))
    failed = len(terminalreporter.stats.get("failed", []))
    errors = len(terminalreporter.stats.get("error", []))
    total = passed + failed + errors

    print("\n" + "=" * 70)
    print("  SHE-SHIELD E2E PIPELINE TEST SUMMARY")
    print("=" * 70)
    print(f"  Total  : {total}")
    print(f"  Passed : {passed} ✅")
    print(f"  Failed : {failed} ❌")
    print(f"  Errors : {errors} 💥")
    print("=" * 70)

    if failed == 0 and errors == 0:
        print("  🎉  ALL TESTS PASSED – PIPELINE IS SOLID  🎉")
    else:
        print("  ⚠️   SOME TESTS FAILED – CHECK OUTPUT ABOVE  ⚠️")
    print("=" * 70 + "\n")
