# Nirbhay – SHE-SHIELD Backend

> **Offline-first women's safety ecosystem** — FastAPI + Supabase backend.

All on-device ML models (YAMNet scream detection, accelerometer fall detection) run entirely inside the Android app. The victim may have **no internet at all**. This backend's job is to receive SOS data once it reaches the internet — either directly from the victim's phone, or forwarded by a nearby stranger's phone over Bluetooth mesh (Bridgefy).

---

## Table of Contents

1. [Quick Start](#quick-start)
2. [Architecture Overview](#architecture-overview)
3. [Security Model](#security-model)
4. [Rate Limiting](#rate-limiting)
5. [Endpoints — Explained](#endpoints--explained)
   - [GET `/` — Health Check](#1-get----health-check)
   - [POST `/api/sos/relay` — Offline Mesh SOS](#2-post-apiosossrelay--offline-mesh-sos)
   - [POST `/api/sos/trigger` — Direct Online SOS](#3-post-apisostrigger--direct-online-sos)
   - [POST `/api/sos/location` — Live Tracking Update](#4-post-apisoslocation--live-tracking-update)
   - [POST `/api/sos/resolve` — Resolve SOS](#5-post-apisosresolve--resolve-sos)
6. [Full Emergency Lifecycle](#full-emergency-lifecycle)
7. [Background Tasks](#background-tasks)
8. [Error Reference](#error-reference)
9. [Project Structure](#project-structure)
10. [Database Schema](#database-schema)

---

## Quick Start

### 1. Prerequisites

- Python 3.11+
- A [Supabase](https://supabase.com) project with tables already created
- (Optional) A [Fast2SMS](https://www.fast2sms.com) account for emergency SMS

### 2. Clone & Install

```bash
git clone https://github.com/derangee/Nirbhay.git
cd Nirbhay
python -m venv venv
source venv/bin/activate      # macOS / Linux
pip install -r requirements.txt
```

### 3. Configure Environment

Create a `.env` file in the **project root** (not inside `backend/`):

```env
# Supabase — required
SUPABASE_URL=https://your-project.supabase.co
SUPABASE_SERVICE_ROLE_KEY=your-service-role-key

# Fast2SMS — optional (SMS skipped gracefully if missing)
FAST2SMS_API_KEY=your-fast2sms-api-key

# Tracking page URL embedded in SMS messages
TRACKING_BASE_URL=https://yourdomain.com/track
```

> **Never commit `.env` to version control.** It is already in `.gitignore`.

### 4. Run the Server

```bash
cd backend
uvicorn main:app --reload --host 0.0.0.0 --port 8000
```

Swagger UI: **http://localhost:8000/docs**

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────┐
│                    VICTIM'S PHONE                       │
│  ┌──────────────┐   ┌────────────────┐                  │
│  │ ScreamDetect │   │ FallDetect     │  Edge-AI models  │
│  │ (YAMNet TFLite)│ │ (Accelerometer)│  run on-device   │
│  └──────┬───────┘   └───────┬────────┘                  │
│         └─────────┬─────────┘                           │
│              SOS triggered                              │
│         ┌─────────▼─────────┐                           │
│         │   BridgefyMesh    │  Bluetooth mesh broadcast │
│         └─────────┬─────────┘                           │
└───────────────────┼─────────────────────────────────────┘
                    │
        ┌───────────┴──────────────────────┐
        │                                 │
  HAS NO INTERNET                   HAS INTERNET
        │                                 │
        ▼                                 ▼
 Bluetooth mesh          ┌─────────────────────────────┐
 broadcast picked        │  POST /api/sos/trigger      │
 up by bystander         │  (victim calls directly)    │
        │                └─────────────────────────────┘
        ▼
 Bystander's phone
 (has internet)
        │
        ▼
┌───────────────────────────────────────────────┐
│  POST /api/sos/relay                          │
│  (bystander forwards victim's SOS payload)    │
└───────────────────────┬───────────────────────┘
                        │
                        ▼
             ┌──────────────────┐
             │  FastAPI Backend │
             │  (this service)  │
             └────────┬─────────┘
                      │  verifies victim_id + emergency_token
                      │  inserts sos_events + live_tracking
                      │  fires SMS via Fast2SMS (background)
                      ▼
             ┌──────────────────┐
             │    Supabase      │
             │  (PostgreSQL +   │
             │   Realtime)      │
             └──────────────────┘
```

**Key principle:** The backend trusts **nothing** from the relay device. It re-authenticates the victim's identity from its own database on every request.

---

## Security Model

| What | How |
|------|-----|
| Victim identity | `victim_id` (UUID) + `emergency_token` verified against `profiles` table on every SOS request |
| SOS resolution | `victim_id` + `secure_pin` (separate secret from `emergency_token`) |
| Relay device | IP is logged as `relay_ip` for forensics but is **never trusted** for auth |
| Duplicate SOS protection | Idempotency check: if an active SOS exists for this victim within last 2 minutes, the existing `sos_id` is returned without creating a duplicate |
| Rate limiting | 10 req/min (SOS endpoints) and 30 req/min (location) per IP using `slowapi` |
| Supabase key | Uses **service-role key** (bypasses RLS) — kept server-side only, never in the Android app |

---

## Rate Limiting

Rate limits are enforced per **IP address**. When exceeded the server returns `HTTP 429 Too Many Requests`.

| Endpoint | Limit |
|----------|-------|
| `POST /api/sos/relay` | 10 requests / minute / IP |
| `POST /api/sos/trigger` | 10 requests / minute / IP |
| `POST /api/sos/location` | 30 requests / minute / IP |
| `POST /api/sos/resolve` | 10 requests / minute / IP |

---

## Endpoints — Explained

### Quick Reference

| Method | Path | Called by | When |
|--------|------|-----------|------|
| `GET` | `/` | Deploy probes / uptime monitors | Always |
| `POST` | `/api/sos/relay` | **Bystander's phone** | Victim has no internet; mesh forwarding |
| `POST` | `/api/sos/trigger` | **Victim's phone** | Victim has internet; direct SOS |
| `POST` | `/api/sos/location` | **Victim's phone** | Every ~10 s while SOS is active |
| `POST` | `/api/sos/resolve` | **Victim's phone** | Victim confirms they are safe |

---

### 1. `GET /` — Health Check

#### Why this endpoint exists
Used by Google Cloud Run health probes, uptime monitors (UptimeRobot, etc.), and the CI/CD pipeline to confirm the container is alive and the app has started without crashing.

#### When it is called
- Automatically by Cloud Run on every new container instance startup
- Periodically by any external uptime monitor
- Manually by a developer to verify the deployment went through

#### What it does
Returns a static JSON response. No database query, no authentication, no side effects.

#### Request
```
GET /
```

#### Response `200 OK`
```json
{
  "status": "ok",
  "service": "SHE-SHIELD"
}
```

---

### 2. `POST /api/sos/relay` — Offline Mesh SOS

#### Why this endpoint exists
The **core offline-first feature** of SHE-SHIELD. When a victim is in a location with no mobile data (basement, remote area, network outage), her phone broadcasts the SOS payload over **Bluetooth mesh (Bridgefy)**. Any nearby phone running the app — even a stranger's — receives this broadcast and, if it has internet, forwards it here. This endpoint processes that forwarded payload.

#### When it is called
- Called by a **bystander's phone** (or any mesh relay node) that received the victim's Bluetooth broadcast
- The bystander's app calls this endpoint automatically — no manual action from the bystander
- Triggered within seconds of the victim's SOS (button press, scream detected, fall detected)
- The victim herself may also reach this endpoint if she regains internet but the mesh relay fires first (idempotency handles de-duplication)

#### What happens internally (step by step)
1. **Rate limit** checked (10/min per relay IP) to prevent botnet abuse
2. `victim_id` + `emergency_token` verified against `profiles` table — the relay device's identity is completely ignored for auth purposes
3. **Idempotency check**: if an active SOS for this victim was created in the last 2 minutes, return the existing `sos_id` without creating a duplicate
4. New row inserted into `sos_events` with `status = "active"`, GPS coordinates, `trigger_method`, `relay_ip`
5. Initial GPS coordinates inserted into `live_tracking`
6. **Background task** fires: SMS sent to all trusted contacts whose `notify_via_sms = true`

#### Request Body
```json
{
  "victim_id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "emergency_token": "tok_abc123xyz",
  "lat": 28.6139,
  "lng": 77.2090,
  "trigger_method": "scream_detected",
  "risk_score": 87,
  "battery_level": 42
}
```

| Field | Type | Required | Constraints |
|-------|------|:--------:|-------------|
| `victim_id` | UUID string | ✅ | Must match a row in `profiles` |
| `emergency_token` | string | ✅ | Min 8 chars; must match `profiles.emergency_token` |
| `lat` | float | ✅ | -90.0 to 90.0 |
| `lng` | float | ✅ | -180.0 to 180.0 |
| `trigger_method` | string | ✅ | Min 1 char (e.g. `"button"`, `"shake"`, `"scream_detected"`, `"fall_detected"`) |
| `risk_score` | int | ❌ | 0–100; AI-assessed danger score from the Edge-AI models |
| `battery_level` | int | ❌ | 0–100; victim's phone battery at SOS time |

#### Response `201 Created`
```json
{
  "success": true,
  "message": "SOS event created and contacts are being notified.",
  "sos_id": "sos-uuid-here",
  "is_new": true
}
```

If idempotency triggers (duplicate SOS within 2 min):
```json
{
  "success": true,
  "message": "Active SOS already exists (idempotent).",
  "sos_id": "existing-sos-uuid",
  "is_new": false
}
```

---

### 3. `POST /api/sos/trigger` — Direct Online SOS

#### Why this endpoint exists
When the victim's phone has mobile data or Wi-Fi, she can skip the mesh entirely and reach the backend directly. This is the fastest path — zero relay latency. Functionally identical to `/relay` under the hood; the only difference is `relay_ip` becomes the victim's own IP instead of a bystander's.

#### When it is called
- Called **by the victim's phone directly** when internet is available
- Called when:
  - The victim taps the SOS button while connected
  - `ScreamDetectionService` detects a scream and internet is available
  - `FallDetectionService` detects a fall and internet is available
- `MeshSosSender` on the Android side decides which path to use based on connectivity

#### What happens internally (step by step)
Identical to `/api/sos/relay`:
1. Rate limit checked
2. `victim_id` + `emergency_token` verified
3. Idempotency check (active SOS within 2 min)
4. Insert into `sos_events` with `relay_ip` = victim's own IP
5. Insert initial `live_tracking` row
6. Background SMS fired to trusted contacts

#### Request Body
Same schema as `/api/sos/relay` — all the same fields and constraints.

#### Response `201 Created`
Same structure as `/api/sos/relay`.

> **Tip:** If both `/relay` and `/trigger` fire within 2 minutes for the same victim (e.g. mesh relay arrives 30 seconds after a direct trigger), the second call returns `is_new: false` and the same `sos_id`. No duplicate events are created.

---

### 4. `POST /api/sos/location` — Live Tracking Update

#### Why this endpoint exists
Once an SOS is active, trusted contacts need to see **where the victim is right now**, not just where she was when she triggered the SOS. This endpoint receives periodic GPS telemetry from the victim's phone and upserts it into `live_tracking`. Since Supabase Realtime is enabled on `live_tracking`, contacts' browsers/apps receive position updates in real time.

#### When it is called
- Called **by the victim's phone** automatically while an SOS is active
- Typically called every **10–30 seconds** by the Android app's location update loop
- Only called while the SOS `status = "active"` — the app stops sending after resolve
- Also sends optional sensor data (speed, heading, accuracy, battery) for richer tracking

#### What happens internally (step by step)
1. Rate limit checked (30/min — intentionally higher than SOS endpoints to allow frequent telemetry)
2. `victim_id` + `emergency_token` verified
3. SOS existence check: confirms the given `sos_id` exists, belongs to this victim, and is still `active`
4. **UPSERT** into `live_tracking` keyed on `sos_id` — only the latest position is stored (no history table)
5. Supabase Realtime pushes the updated row to all subscribed contacts instantly

#### Request Body
```json
{
  "victim_id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "emergency_token": "tok_abc123xyz",
  "sos_id": "sos-uuid-here",
  "lat": 28.6145,
  "lng": 77.2095,
  "accuracy": 5.0,
  "speed": 1.2,
  "heading": 270.0,
  "battery_level": 39
}
```

| Field | Type | Required | Constraints |
|-------|------|:--------:|-------------|
| `victim_id` | UUID string | ✅ | Must match authenticated profile |
| `emergency_token` | string | ✅ | Min 8 chars |
| `sos_id` | UUID string | ✅ | Must reference an active SOS owned by this victim |
| `lat` | float | ✅ | -90.0 to 90.0 |
| `lng` | float | ✅ | -180.0 to 180.0 |
| `accuracy` | float | ❌ | ≥ 0; GPS accuracy in metres |
| `speed` | float | ❌ | ≥ 0; speed in m/s |
| `heading` | float | ❌ | 0.0 to 360.0; compass bearing |
| `battery_level` | int | ❌ | 0–100 |

#### Response `200 OK`
```json
{
  "success": true,
  "message": "Location updated.",
  "sos_id": "sos-uuid-here"
}
```

#### Important: What `404` means here
If this endpoint returns `404`, it means the SOS with that `sos_id` is **not active** (either never existed, was already resolved, or belongs to a different victim). The Android app should **stop sending location updates** when it receives a `404`.

---

### 5. `POST /api/sos/resolve` — Resolve SOS

#### Why this endpoint exists
When the victim is safe, she needs to explicitly close the emergency so trusted contacts stop worrying and receiving alerts. This endpoint marks the SOS as `resolved`, records the resolution timestamp, and sends a "she is safe" SMS to all contacts. It uses a **separate secret PIN** (not the `emergency_token`) so that even if the `emergency_token` was intercepted by a relay device, a bad actor cannot falsely mark the victim as safe.

#### When it is called
- Called **by the victim's phone only** — never by a relay device
- Called when the victim taps the "I am Safe" / resolve button in the app
- Called after the victim is out of danger and wants to close the active SOS
- Can only resolve an SOS that is currently `status = "active"` — calling it twice returns `404`

#### What happens internally (step by step)
1. Rate limit checked
2. `victim_id` + `secure_pin` verified against `profiles` — note: uses `secure_pin`, **not** `emergency_token`
3. `sos_events` row updated: `status → "resolved"`, `resolved_at → now(UTC)` — but only if the row is still `active` (prevents double-resolve)
4. If no matching active SOS found → `404`
5. **Background task** fires: "User is Safe" SMS sent to trusted contacts

#### Request Body
```json
{
  "victim_id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "secure_pin": "8472",
  "sos_id": "sos-uuid-here"
}
```

| Field | Type | Required | Constraints |
|-------|------|:--------:|-------------|
| `victim_id` | UUID string | ✅ | Must match a profile |
| `secure_pin` | string | ✅ | Min 4 chars; verified against `profiles.secure_pin` |
| `sos_id` | UUID string | ✅ | Must reference an active SOS owned by this victim |

#### Response `200 OK`
```json
{
  "success": true,
  "message": "SOS resolved. Contacts are being notified you are safe.",
  "sos_id": "sos-uuid-here",
  "resolved_at": "2026-02-25T14:32:10.123456+00:00"
}
```

---

## Full Emergency Lifecycle

The diagram below shows the **exact sequence of endpoint calls** during a complete emergency event.

```
Time →

[SOS Triggered by victim — no internet]
        │
        ▼
BridgefyMesh broadcasts over Bluetooth
        │
        ▼
Bystander's phone picks up broadcast
        │
        ▼
POST /api/sos/relay  ──────────────────────► sos_events inserted (status=active)
        │                                    live_tracking inserted
        │                                    SMS fired to contacts (background)
        │
        │    [victim regains internet — mesh fires again]
        ▼
POST /api/sos/relay (2nd time)  ──────────► idempotency hit → same sos_id returned
        │                                   no duplicate created
        │
        │    [victim's phone now has internet]
        ▼
POST /api/sos/location  ──────────────────► live_tracking UPSERTED
        │                                   Supabase Realtime →  contacts see new pin
        │          (repeats every ~10-30 s)
        ▼
POST /api/sos/location  ──────────────────► live_tracking UPSERTED
        │
        ...
        │
        │    [victim is safe]
        ▼
POST /api/sos/resolve  ───────────────────► sos_events.status = "resolved"
                                            resolved_at = now()
                                            "You are safe" SMS fired (background)


  ────────────────────────────────────────────────────────────
  ALTERNATE PATH: Victim has internet from the start

  SOS triggered on victim's phone
        │
        ▼
  POST /api/sos/trigger  ─────────────────► same as relay, relay_ip = victim's IP
        │
        ▼
  POST /api/sos/location  ─ (repeats) ───► live tracking
        │
        ▼
  POST /api/sos/resolve  ─────────────────► resolved
```

---

## Background Tasks

Both SOS creation endpoints (`/relay` and `/trigger`) and the resolve endpoint fire background SMS tasks via **Fast2SMS**. These run **after** the HTTP response is sent, so the endpoint always returns in milliseconds. If `FAST2SMS_API_KEY` is not set in `.env`, the SMS step is silently skipped — the SOS is still fully created in the database.

| Event | SMS content |
|-------|-------------|
| SOS created | `"URGENT SOS: <full_name> triggered an emergency. Track live: <TRACKING_BASE_URL>/<sos_id>"` |
| SOS resolved | `"SAFE UPDATE: <full_name> has marked themselves safe. The emergency (SOS ID: <sos_id>) has been resolved."` |

SMS is sent only to contacts whose `notify_via_sms = true` in `trusted_contacts`.

---

## Error Reference

| HTTP Code | Meaning | When you see it |
|-----------|---------|-----------------|
| `200` | Success | Location update, resolve, health check |
| `201` | Created | New SOS event created (relay / trigger) |
| `401` | Unauthorized | Wrong `victim_id`, `emergency_token`, or `secure_pin` |
| `404` | Not Found | SOS not active, already resolved, or wrong victim |
| `405` | Method Not Allowed | Wrong HTTP verb (e.g. GET on a POST endpoint) |
| `422` | Unprocessable Entity | Request body fails validation (missing field, wrong type, out-of-range value) |
| `429` | Too Many Requests | Rate limit exceeded (10/min on SOS endpoints) |
| `500` | Internal Server Error | Supabase insert/update returned empty data |

---

## Project Structure

```
backend/
├── .env                  # Secrets — never commit
├── .gitignore
├── Dockerfile            # Multi-stage image for Cloud Run
├── requirements.txt      # Python dependencies
├── models.py             # Pydantic request/response schemas
├── main.py               # FastAPI application + all endpoints
├── API_DOCS.md           # Detailed field-level API reference
├── README_Backend.md     # This file
└── tests/
    ├── conftest.py       # Shared fixtures, Supabase mocking
    ├── test_endpoints.py # 63 unit tests (per-endpoint)
    └── test_e2e_flow.py  # 32 end-to-end lifecycle tests
```

---

## Database Schema

### `profiles`
Stores victim identity and secrets.

| Column | Type | Notes |
|--------|------|-------|
| `id` | UUID PK | Matches Android app's user ID |
| `full_name` | text | Used in SMS messages |
| `phone_number` | text | Victim's phone number |
| `emergency_token` | text | Secret used to authenticate SOS relay/trigger requests |
| `secure_pin` | text | Separate secret used only for SOS resolution |

### `trusted_contacts`
Per-victim list of people to notify.

| Column | Type | Notes |
|--------|------|-------|
| `user_id` | UUID FK → profiles.id | |
| `contact_name` | text | |
| `contact_phone` | text | Phone number to SMS |
| `notify_via_sms` | boolean | Only contacts with `true` receive SMS |

### `sos_events`
Every SOS triggered (active or resolved).

| Column | Type | Notes |
|--------|------|-------|
| `id` | UUID PK | Returned as `sos_id` |
| `victim_id` | UUID FK → profiles.id | |
| `trigger_method` | text | `"button"`, `"scream_detected"`, `"fall_detected"`, etc. |
| `risk_score` | int | 0–100, from Edge-AI model |
| `initial_lat` / `initial_lng` | float | GPS at SOS trigger time |
| `status` | text | `"active"` or `"resolved"` |
| `relay_ip` | text | IP of the device that hit the backend |
| `created_at` | timestamptz | |
| `resolved_at` | timestamptz | Set when `/resolve` is called |

### `live_tracking`
Real-time GPS telemetry (one row per active SOS, upserted on each `/location` call).

| Column | Type | Notes |
|--------|------|-------|
| `sos_id` | UUID PK FK → sos_events.id | One row per SOS — always the latest position |
| `victim_id` | UUID | |
| `lat` / `lng` | float | Current position |
| `accuracy` | float | GPS accuracy in metres |
| `speed` | float | m/s |
| `heading` | float | 0–360° compass bearing |
| `battery_level` | int | Latest battery reading |
| `updated_at` | timestamptz | |

> Both `sos_events` and `live_tracking` are added to **Supabase Realtime** — contacts' tracking pages receive live updates via WebSocket without polling.
