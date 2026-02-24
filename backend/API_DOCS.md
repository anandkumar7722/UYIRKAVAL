# SHE-SHIELD Backend – API Documentation

> **Base URL:** `https://your-deployed-domain.com` (local: `http://localhost:8000`)
>
> **Content-Type:** All requests must send `application/json` body.
>
> **Swagger UI:** Available at `/docs` when the server is running.

---

## Table of Contents

1. [Authentication Model](#authentication-model)
2. [Rate Limiting](#rate-limiting)
3. [Endpoints Overview](#endpoints-overview)
4. [GET / – Health Check](#1-get----health-check)
5. [POST /api/sos/relay – Offline Mesh SOS](#2-post-apisos relay--offline-mesh-sos)
6. [POST /api/sos/trigger – Direct Online SOS](#3-post-apisostrigger--direct-online-sos)
7. [POST /api/sos/location – Live Tracking Update](#4-post-apisoslocation--live-tracking-update)
8. [POST /api/sos/resolve – Resolve SOS](#5-post-apisosresolve--resolve-sos)
9. [Error Responses](#error-responses)
10. [Data Type Reference](#data-type-reference)

---

## Authentication Model

There are **no Bearer tokens or Authorization headers**. Authentication is embedded in the JSON body:

| Endpoint | Auth Fields in Body | Verified Against |
|----------|-------------------|------------------|
| `/api/sos/relay` | `victim_id` + `emergency_token` | `profiles` table |
| `/api/sos/trigger` | `victim_id` + `emergency_token` | `profiles` table |
| `/api/sos/location` | `victim_id` + `emergency_token` | `profiles` table |
| `/api/sos/resolve` | `victim_id` + `secure_pin` | `profiles` table |

The backend **never trusts** the device forwarding the request. It always verifies credentials against the database.

---

## Rate Limiting

Rate limits are enforced **per IP address** using `slowapi`.

| Endpoint | Limit |
|----------|-------|
| `/api/sos/relay` | 10 requests / minute / IP |
| `/api/sos/trigger` | 10 requests / minute / IP |
| `/api/sos/location` | 30 requests / minute / IP |
| `/api/sos/resolve` | 10 requests / minute / IP |

When exceeded, the server returns **HTTP 429 Too Many Requests**.

---

## Endpoints Overview

| Method | Path | Purpose | Success Code |
|--------|------|---------|:------------:|
| `GET` | `/` | Health check | `200` |
| `POST` | `/api/sos/relay` | SOS from Bluetooth mesh relay | `201` |
| `POST` | `/api/sos/trigger` | SOS from device with internet | `201` |
| `POST` | `/api/sos/location` | Live GPS telemetry update | `200` |
| `POST` | `/api/sos/resolve` | Victim marks themselves safe | `200` |

---

## 1. GET `/` – Health Check

Simple liveness probe. No body, no auth.

### Request

```
GET /
```

### Response (200 OK)

```json
{
  "status": "ok",
  "service": "SHE-SHIELD"
}
```

---

## 2. POST `/api/sos/relay` – Offline Mesh SOS

Used when a victim has **no internet**. Their phone broadcasts an SOS over Bluetooth mesh (Bridgefy). A nearby device with internet catches the payload and forwards it here.

### Request Headers

```
Content-Type: application/json
```

### Request Body

```json
{
  "victim_id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "emergency_token": "xK9mP2vL8qR4wN7j",
  "lat": 17.385044,
  "lng": 78.486671,
  "trigger_method": "button",
  "risk_score": 85,
  "battery_level": 42
}
```

### Field Reference

| Field | Type | Required | Constraints | Description |
|-------|------|:--------:|-------------|-------------|
| `victim_id` | `string` (UUID) | **Yes** | Must be a valid UUID | The victim's user ID from `profiles.id` |
| `emergency_token` | `string` | **Yes** | Min 8 characters | Secret token generated during registration |
| `lat` | `float` | **Yes** | -90.0 to 90.0 | Latitude at SOS trigger time |
| `lng` | `float` | **Yes** | -180.0 to 180.0 | Longitude at SOS trigger time |
| `trigger_method` | `string` | **Yes** | Min 1 character | How SOS was triggered: `"button"`, `"shake"`, `"voice"`, etc. |
| `risk_score` | `integer` or `null` | No | 0 to 100 | AI-assessed risk score. Omit or send `null` if unavailable. |
| `battery_level` | `integer` or `null` | No | 0 to 100 | Phone battery percentage. Omit or send `null` if unavailable. |

### Minimal Request (only required fields)

```json
{
  "victim_id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "emergency_token": "xK9mP2vL8qR4wN7j",
  "lat": 17.385044,
  "lng": 78.486671,
  "trigger_method": "button"
}
```

### Success Response (201 Created) – New SOS

```json
{
  "success": true,
  "message": "SOS event created and contacts are being notified.",
  "sos_id": "f7e6d5c4-b3a2-1098-fedc-ba9876543210",
  "is_new": true
}
```

### Success Response (201 Created) – Idempotent (duplicate within 2 min)

```json
{
  "success": true,
  "message": "Active SOS already exists (idempotent).",
  "sos_id": "f7e6d5c4-b3a2-1098-fedc-ba9876543210",
  "is_new": false
}
```

### Error Responses

| Code | Condition | Body |
|:----:|-----------|------|
| `401` | `victim_id` + `emergency_token` don't match | `{"detail": "Invalid victim_id or emergency_token."}` |
| `422` | Missing/invalid fields (Pydantic validation) | `{"detail": [{"loc": [...], "msg": "...", "type": "..."}]}` |
| `429` | Rate limit exceeded (>10 req/min from this IP) | `{"error": "Rate limit exceeded: 10 per 1 minute"}` |
| `500` | Database insert failed | `{"detail": "Failed to create SOS event."}` |

### What Happens Server-Side

1. Verifies `victim_id` + `emergency_token` against `profiles` table
2. Checks if an active SOS exists for this victim from the last 2 minutes (idempotency)
3. Inserts a new row into `sos_events` with `status = "active"` and the relay device's IP
4. Inserts initial coordinates into `live_tracking`
5. **Background task**: Fetches trusted contacts → sends URGENT SMS via Fast2SMS

---

## 3. POST `/api/sos/trigger` – Direct Online SOS

Used when the victim's phone **has internet** and can reach the backend directly. Functionally identical to `/api/sos/relay`.

### Request Headers

```
Content-Type: application/json
```

### Request Body

```json
{
  "victim_id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "emergency_token": "xK9mP2vL8qR4wN7j",
  "lat": 17.385044,
  "lng": 78.486671,
  "trigger_method": "shake",
  "risk_score": 72,
  "battery_level": 65
}
```

### Field Reference

| Field | Type | Required | Constraints | Description |
|-------|------|:--------:|-------------|-------------|
| `victim_id` | `string` (UUID) | **Yes** | Must be a valid UUID | The victim's user ID from `profiles.id` |
| `emergency_token` | `string` | **Yes** | Min 8 characters | Secret token generated during registration |
| `lat` | `float` | **Yes** | -90.0 to 90.0 | Latitude at SOS trigger time |
| `lng` | `float` | **Yes** | -180.0 to 180.0 | Longitude at SOS trigger time |
| `trigger_method` | `string` | **Yes** | Min 1 character | How SOS was triggered: `"button"`, `"shake"`, `"voice"`, etc. |
| `risk_score` | `integer` or `null` | No | 0 to 100 | AI-assessed risk score |
| `battery_level` | `integer` or `null` | No | 0 to 100 | Phone battery percentage |

### Success Response (201 Created)

Same as `/api/sos/relay`:

```json
{
  "success": true,
  "message": "SOS event created and contacts are being notified.",
  "sos_id": "f7e6d5c4-b3a2-1098-fedc-ba9876543210",
  "is_new": true
}
```

### Error Responses

Same as `/api/sos/relay` – see table above.

---

## 4. POST `/api/sos/location` – Live Tracking Update

Called **periodically** by the victim's device while an SOS is active. Updates the victim's latest GPS position. Uses database **UPSERT** on `sos_id`, so only the most recent location is stored per SOS.

### Request Headers

```
Content-Type: application/json
```

### Request Body

```json
{
  "sos_id": "f7e6d5c4-b3a2-1098-fedc-ba9876543210",
  "victim_id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "emergency_token": "xK9mP2vL8qR4wN7j",
  "lat": 17.386100,
  "lng": 78.487200,
  "accuracy": 8.5,
  "speed": 1.2,
  "heading": 270.0,
  "battery_level": 38
}
```

### Field Reference

| Field | Type | Required | Constraints | Description |
|-------|------|:--------:|-------------|-------------|
| `sos_id` | `string` (UUID) | **Yes** | Must be a valid UUID | The active SOS event's ID (returned when SOS was created) |
| `victim_id` | `string` (UUID) | **Yes** | Must be a valid UUID | The victim's user ID from `profiles.id` |
| `emergency_token` | `string` | **Yes** | Min 8 characters | Secret token for authentication |
| `lat` | `float` | **Yes** | -90.0 to 90.0 | Current latitude |
| `lng` | `float` | **Yes** | -180.0 to 180.0 | Current longitude |
| `accuracy` | `float` or `null` | No | >= 0.0 | GPS accuracy in metres |
| `speed` | `float` or `null` | No | >= 0.0 | Speed in metres per second |
| `heading` | `float` or `null` | No | 0.0 to 360.0 | Compass heading in degrees (0 = North, 90 = East) |
| `battery_level` | `integer` or `null` | No | 0 to 100 | Current battery percentage |

### Minimal Request (only required fields)

```json
{
  "sos_id": "f7e6d5c4-b3a2-1098-fedc-ba9876543210",
  "victim_id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "emergency_token": "xK9mP2vL8qR4wN7j",
  "lat": 17.386100,
  "lng": 78.487200
}
```

### Success Response (200 OK)

```json
{
  "success": true,
  "message": "Location updated.",
  "sos_id": "f7e6d5c4-b3a2-1098-fedc-ba9876543210"
}
```

### Error Responses

| Code | Condition | Body |
|:----:|-----------|------|
| `401` | `victim_id` + `emergency_token` don't match | `{"detail": "Invalid victim_id or emergency_token."}` |
| `422` | Missing/invalid fields | `{"detail": [{"loc": [...], "msg": "...", "type": "..."}]}` |
| `429` | Rate limit exceeded (>30 req/min from this IP) | `{"error": "Rate limit exceeded: 30 per 1 minute"}` |
| `500` | Database upsert failed | `{"detail": "Failed to upsert tracking data."}` |

---

## 5. POST `/api/sos/resolve` – Resolve SOS

Called when the victim wants to **mark themselves safe** and end the active SOS. Authenticated with `secure_pin` (NOT `emergency_token`) to ensure only the real victim can resolve it.

### Request Headers

```
Content-Type: application/json
```

### Request Body

```json
{
  "sos_id": "f7e6d5c4-b3a2-1098-fedc-ba9876543210",
  "victim_id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "secure_pin": "8432"
}
```

### Field Reference

| Field | Type | Required | Constraints | Description |
|-------|------|:--------:|-------------|-------------|
| `sos_id` | `string` (UUID) | **Yes** | Must be a valid UUID | The SOS event to resolve |
| `victim_id` | `string` (UUID) | **Yes** | Must be a valid UUID | The victim's user ID from `profiles.id` |
| `secure_pin` | `string` | **Yes** | Min 4 characters | The victim's secure PIN set during registration |

### Success Response (200 OK)

```json
{
  "success": true,
  "message": "SOS resolved. Contacts are being notified you are safe.",
  "sos_id": "f7e6d5c4-b3a2-1098-fedc-ba9876543210",
  "resolved_at": "2026-02-25T14:30:00.123456+00:00"
}
```

### Error Responses

| Code | Condition | Body |
|:----:|-----------|------|
| `401` | `victim_id` + `secure_pin` don't match | `{"detail": "Invalid victim_id or secure_pin."}` |
| `404` | No **active** SOS found with that `sos_id` for this victim | `{"detail": "No active SOS found with the given sos_id for this victim."}` |
| `422` | Missing/invalid fields | `{"detail": [{"loc": [...], "msg": "...", "type": "..."}]}` |
| `429` | Rate limit exceeded (>10 req/min) | `{"error": "Rate limit exceeded: 10 per 1 minute"}` |

### What Happens Server-Side

1. Verifies `victim_id` + `secure_pin` against `profiles` table
2. Updates `sos_events` row: sets `status = "resolved"` and `resolved_at = now()`
3. Only updates if the SOS is currently `"active"` (guard against double-resolve)
4. **Background task**: Sends "User is Safe" SMS to all trusted contacts

---

## Error Responses

All error responses follow this format:

### Validation Error (422)

Returned when the JSON body is missing required fields or has invalid values.

```json
{
  "detail": [
    {
      "type": "missing",
      "loc": ["body", "victim_id"],
      "msg": "Field required",
      "input": { ... }
    }
  ]
}
```

### Authentication Error (401)

```json
{
  "detail": "Invalid victim_id or emergency_token."
}
```

### Not Found (404)

```json
{
  "detail": "No active SOS found with the given sos_id for this victim."
}
```

### Rate Limited (429)

```json
{
  "error": "Rate limit exceeded: 10 per 1 minute"
}
```

### Server Error (500)

```json
{
  "detail": "Failed to create SOS event."
}
```

---

## Data Type Reference

### UUID Format

All UUIDs must be sent as **hyphenated lowercase strings**:

```
"a1b2c3d4-e5f6-7890-abcd-ef1234567890"
```

The `victim_id` comes from `profiles.id` in Supabase (which references `auth.users.id`).
The `sos_id` is returned in the response when an SOS is created.

### Timestamp Format

All timestamps in responses are ISO 8601 with timezone:

```
"2026-02-25T14:30:00.123456+00:00"
```

### `trigger_method` Values

The backend accepts any non-empty string. Suggested values used by the app:

| Value | Meaning |
|-------|---------|
| `"button"` | SOS button pressed |
| `"shake"` | Phone shake detected |
| `"voice"` | Voice command activated |
| `"auto"` | AI auto-detected danger |
| `"widget"` | Home screen widget tap |

---

## Complete Flow: Frontend Integration Guide

### Flow 1: Victim Has Internet

```
App                                    Backend
 │                                       │
 ├── POST /api/sos/trigger ────────────► │ ── verify token
 │   {victim_id, emergency_token,        │ ── insert sos_events
 │    lat, lng, trigger_method, ...}     │ ── insert live_tracking
 │                                       │ ── background: SMS contacts
 │ ◄──────────────── 201 {sos_id} ──────┤
 │                                       │
 ├── POST /api/sos/location ───────────► │ ── verify token
 │   {sos_id, victim_id,                 │ ── upsert live_tracking
 │    emergency_token, lat, lng, ...}    │
 │ ◄──────────────── 200 OK ────────────┤
 │   (repeat every 5-10 seconds)         │
 │                                       │
 ├── POST /api/sos/resolve ────────────► │ ── verify secure_pin
 │   {sos_id, victim_id, secure_pin}     │ ── update sos_events
 │ ◄──────────────── 200 OK ────────────┤ ── background: "safe" SMS
```

### Flow 2: Victim Has NO Internet (Bluetooth Mesh)

```
Victim Phone          Relay Phone              Backend
 │                      │                        │
 │ ── BT broadcast ──► │                        │
 │   {victim_id,        │                        │
 │    emergency_token,  │── POST /api/sos/relay ►│ ── verify token
 │    lat, lng, ...}    │   (forwards payload)   │ ── insert sos_events
 │                      │                        │ ── insert live_tracking
 │                      │ ◄───── 201 {sos_id} ──┤ ── background: SMS
 │                      │                        │
```

The relay device simply wraps the victim's broadcast payload into a JSON POST. The backend ignores who the relay device is — it only trusts the `victim_id` + `emergency_token` pair.

### What the App Must Store Locally

| Value | When Stored | Used For |
|-------|-------------|----------|
| `victim_id` (UUID) | After Supabase auth sign-up | Every API call |
| `emergency_token` | Generated during registration, saved in `profiles` | SOS trigger & location updates |
| `secure_pin` | Set by user during registration | Resolving an SOS only |
| `sos_id` (UUID) | Received in response from `/relay` or `/trigger` | Location updates & resolve |

---

## cURL Examples

### Trigger an SOS (relay)

```bash
curl -X POST http://localhost:8000/api/sos/relay \
  -H "Content-Type: application/json" \
  -d '{
    "victim_id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "emergency_token": "xK9mP2vL8qR4wN7j",
    "lat": 17.385044,
    "lng": 78.486671,
    "trigger_method": "button",
    "risk_score": 85,
    "battery_level": 42
  }'
```

### Update location

```bash
curl -X POST http://localhost:8000/api/sos/location \
  -H "Content-Type: application/json" \
  -d '{
    "sos_id": "f7e6d5c4-b3a2-1098-fedc-ba9876543210",
    "victim_id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "emergency_token": "xK9mP2vL8qR4wN7j",
    "lat": 17.386100,
    "lng": 78.487200,
    "accuracy": 8.5,
    "speed": 1.2,
    "heading": 270.0,
    "battery_level": 38
  }'
```

### Resolve SOS

```bash
curl -X POST http://localhost:8000/api/sos/resolve \
  -H "Content-Type: application/json" \
  -d '{
    "sos_id": "f7e6d5c4-b3a2-1098-fedc-ba9876543210",
    "victim_id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "secure_pin": "8432"
  }'
```
