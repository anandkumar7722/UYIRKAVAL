"""
============================================================
SHE-SHIELD Backend – main.py
============================================================
Production-ready FastAPI backend for an offline-first women's
safety ecosystem.  Designed to receive SOS payloads relayed
over a Bluetooth mesh (Bridgefy) by untrusted intermediary
devices.

SECURITY MODEL
--------------
* The relay device is NEVER trusted.
* Every request is authenticated by verifying `victim_id` +
  `emergency_token` (or `secure_pin`) against the Supabase
  `profiles` table.
* Rate-limiting (slowapi) is applied per IP to mitigate relay
  botnet spam.
* All network-bound work (SMS, external HTTP) runs inside
  FastAPI BackgroundTasks so the endpoint returns within
  milliseconds.

Run locally:
    uvicorn main:app --reload --host 0.0.0.0 --port 8000
============================================================
"""

import logging
import os
from datetime import datetime, timedelta, timezone
from typing import Any, Dict, List, Optional

import httpx
from dotenv import load_dotenv
from fastapi import (
    BackgroundTasks,
    FastAPI,
    HTTPException,
    Request,
    status,
)
from fastapi.middleware.cors import CORSMiddleware
from slowapi import Limiter, _rate_limit_exceeded_handler
from slowapi.errors import RateLimitExceeded
from slowapi.middleware import SlowAPIMiddleware
from slowapi.util import get_remote_address
from supabase import Client, create_client

from models import (
    LocationResponse,
    LocationUpdateRequest,
    ResolveResponse,
    SOSRelayRequest,
    SOSResolveRequest,
    SOSResponse,
    SOSTriggerRequest,
)

# ============================================================
# 1. CONFIGURATION & INITIALISATION
# ============================================================

# Load .env file (must be in the project root)
load_dotenv()

# --- Logging ---
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s | %(levelname)-8s | %(name)s | %(message)s",
)
logger = logging.getLogger("she-shield")

# --- Supabase ---
SUPABASE_URL: str = os.environ["SUPABASE_URL"]
SUPABASE_SERVICE_ROLE_KEY: str = os.environ["SUPABASE_SERVICE_ROLE_KEY"]

# We use the *service-role* key so the backend can bypass RLS
# and perform unrestricted reads/writes on behalf of victims.
supabase: Client = create_client(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY)

# --- Fast2SMS ---
FAST2SMS_API_KEY: str = os.getenv("FAST2SMS_API_KEY", "")
FAST2SMS_ENDPOINT: str = "https://www.fast2sms.com/dev/bulkV2"

# --- Tracking page base URL (shared in SMS messages) ---
TRACKING_BASE_URL: str = os.getenv(
    "TRACKING_BASE_URL", "https://yourdomain.com/track"
)

# --- Rate limiter (keyed on caller's IP) ---
limiter = Limiter(key_func=get_remote_address)

# --- FastAPI application ---
app = FastAPI(
    title="SHE-SHIELD API",
    description="Offline-first women's safety backend.",
    version="1.0.0",
)

# Attach the limiter's state and exception handler
app.state.limiter = limiter
app.add_exception_handler(RateLimitExceeded, _rate_limit_exceeded_handler)

# SlowAPI middleware for proper rate-limiting integration
app.add_middleware(SlowAPIMiddleware)

# --- CORS (allow all for mobile clients; tighten in production) ---
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


# ============================================================
# 2. HELPER / UTILITY FUNCTIONS
# ============================================================


def _verify_emergency_token(
    victim_id: str, emergency_token: str
) -> Optional[Dict[str, Any]]:
    """
    Query the `profiles` table to verify that the given
    `victim_id` owns the supplied `emergency_token`.

    Returns the profile row dict on success, or None on failure.
    """
    result = (
        supabase.table("profiles")
        .select("id, full_name, phone_number")
        .eq("id", victim_id)
        .eq("emergency_token", emergency_token)
        .execute()
    )
    if result.data and len(result.data) > 0:
        return result.data[0]
    return None


def _verify_secure_pin(
    victim_id: str, secure_pin: str
) -> Optional[Dict[str, Any]]:
    """
    Verify the victim's `secure_pin` for SOS resolution.
    Returns the profile row dict on success, or None on failure.
    """
    result = (
        supabase.table("profiles")
        .select("id, full_name, phone_number")
        .eq("id", victim_id)
        .eq("secure_pin", secure_pin)
        .execute()
    )
    if result.data and len(result.data) > 0:
        return result.data[0]
    return None


def _check_active_sos_within(
    victim_id: str, minutes: int = 2
) -> Optional[Dict[str, Any]]:
    """
    Idempotency guard: look for an *active* SOS event created by
    this victim within the last `minutes` minutes.  If one exists
    we return it instead of creating a duplicate.
    """
    cutoff = (
        datetime.now(timezone.utc) - timedelta(minutes=minutes)
    ).isoformat()

    result = (
        supabase.table("sos_events")
        .select("id, created_at")
        .eq("victim_id", victim_id)
        .eq("status", "active")
        .gte("created_at", cutoff)
        .order("created_at", desc=True)
        .limit(1)
        .execute()
    )
    if result.data and len(result.data) > 0:
        return result.data[0]
    return None


def _get_client_ip(request: Request) -> str:
    """
    Extract the real client IP.  Respects X-Forwarded-For when
    the app runs behind a reverse proxy / load balancer.
    """
    forwarded = request.headers.get("x-forwarded-for")
    if forwarded:
        # Take the first (leftmost) IP – that's the original client
        return forwarded.split(",")[0].strip()
    return request.client.host if request.client else "unknown"


# ============================================================
# 3. BACKGROUND TASKS (SMS via Fast2SMS)
# ============================================================


async def _send_sos_sms(
    victim_id: str, full_name: str, sos_id: str
) -> None:
    """
    Background task: fetch all trusted contacts (with
    `notify_via_sms = true`) and send an URGENT SOS SMS
    through the Fast2SMS bulk API.

    Runs asynchronously so the API response is never blocked.
    """
    try:
        # 1. Fetch contacts to notify
        contacts_result = (
            supabase.table("trusted_contacts")
            .select("contact_phone, contact_name")
            .eq("user_id", victim_id)
            .eq("notify_via_sms", True)
            .execute()
        )

        contacts: List[Dict[str, Any]] = contacts_result.data or []
        if not contacts:
            logger.warning(
                "No SMS contacts found for victim %s", victim_id
            )
            return

        # 2. Build the SMS message
        tracking_url = f"{TRACKING_BASE_URL}/{sos_id}"
        message = (
            f"URGENT SOS: {full_name} triggered an emergency. "
            f"Track live: {tracking_url}"
        )

        # 3. Comma-separated phone numbers (Fast2SMS format)
        phone_numbers = ",".join(
            c["contact_phone"] for c in contacts
        )

        logger.info(
            "Sending SOS SMS for victim %s to %d contact(s)",
            victim_id,
            len(contacts),
        )

        # 4. Fire the Fast2SMS API (non-blocking async HTTP)
        async with httpx.AsyncClient(timeout=15.0) as client:
            resp = await client.post(
                FAST2SMS_ENDPOINT,
                headers={"authorization": FAST2SMS_API_KEY},
                data={
                    "route": "q",          # Quick SMS route
                    "message": message,
                    "language": "english",
                    "flash": "0",
                    "numbers": phone_numbers,
                },
            )
            logger.info(
                "Fast2SMS response [%s]: %s",
                resp.status_code,
                resp.text[:200],
            )

    except Exception:
        # Log but never crash – this is a background task.
        logger.exception(
            "Failed to send SOS SMS for victim %s", victim_id
        )


async def _send_safe_sms(
    victim_id: str, full_name: str, sos_id: str
) -> None:
    """
    Background task: notify trusted contacts that the victim
    has marked themselves safe and the SOS is resolved.
    """
    try:
        contacts_result = (
            supabase.table("trusted_contacts")
            .select("contact_phone, contact_name")
            .eq("user_id", victim_id)
            .eq("notify_via_sms", True)
            .execute()
        )

        contacts: List[Dict[str, Any]] = contacts_result.data or []
        if not contacts:
            return

        message = (
            f"SAFE UPDATE: {full_name} has marked themselves safe. "
            f"The emergency (SOS ID: {sos_id}) has been resolved."
        )

        phone_numbers = ",".join(
            c["contact_phone"] for c in contacts
        )

        async with httpx.AsyncClient(timeout=15.0) as client:
            resp = await client.post(
                FAST2SMS_ENDPOINT,
                headers={"authorization": FAST2SMS_API_KEY},
                data={
                    "route": "q",
                    "message": message,
                    "language": "english",
                    "flash": "0",
                    "numbers": phone_numbers,
                },
            )
            logger.info(
                "Fast2SMS safe-SMS response [%s]: %s",
                resp.status_code,
                resp.text[:200],
            )

    except Exception:
        logger.exception(
            "Failed to send safe SMS for victim %s", victim_id
        )


# ============================================================
# 4. CORE SOS PROCESSING (shared by relay & direct endpoints)
# ============================================================


def _process_sos(
    *,
    victim_id: str,
    emergency_token: str,
    lat: float,
    lng: float,
    trigger_method: str,
    risk_score: Optional[int],
    battery_level: Optional[int],
    relay_ip: str,
    background_tasks: BackgroundTasks,
) -> SOSResponse:
    """
    Shared logic for both the relay and direct-trigger endpoints:

    1. Authenticate the victim via `emergency_token`.
    2. Idempotency check (active SOS within last 2 min).
    3. Insert into `sos_events`.
    4. Insert initial telemetry into `live_tracking`.
    5. Enqueue background SMS to trusted contacts.

    Returns an SOSResponse ready to be sent to the caller.
    """

    # --- STEP 1: Verify identity ---
    profile = _verify_emergency_token(victim_id, emergency_token)
    if profile is None:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid victim_id or emergency_token.",
        )

    full_name: str = profile["full_name"]

    # --- STEP 2: Idempotency check ---
    existing_sos = _check_active_sos_within(victim_id, minutes=2)
    if existing_sos is not None:
        logger.info(
            "Idempotent SOS hit for victim %s → existing SOS %s",
            victim_id,
            existing_sos["id"],
        )
        return SOSResponse(
            success=True,
            message="Active SOS already exists (idempotent).",
            sos_id=existing_sos["id"],
            is_new=False,
        )

    # --- STEP 3: Insert new SOS event ---
    sos_insert = (
        supabase.table("sos_events")
        .insert(
            {
                "victim_id": victim_id,
                "trigger_method": trigger_method,
                "risk_score": risk_score,
                "initial_lat": lat,
                "initial_lng": lng,
                "status": "active",
                "relay_ip": relay_ip,
            }
        )
        .execute()
    )

    if not sos_insert.data or len(sos_insert.data) == 0:
        logger.error("Failed to insert SOS event for victim %s", victim_id)
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="Failed to create SOS event.",
        )

    sos_row = sos_insert.data[0]
    sos_id: str = sos_row["id"]

    logger.info(
        "NEW SOS %s created for victim %s (relay_ip=%s)",
        sos_id,
        victim_id,
        relay_ip,
    )

    # --- STEP 4: Insert initial live-tracking telemetry ---
    try:
        supabase.table("live_tracking").insert(
            {
                "sos_id": sos_id,
                "victim_id": victim_id,
                "lat": lat,
                "lng": lng,
                "battery_level": battery_level,
                "updated_at": datetime.now(timezone.utc).isoformat(),
            }
        ).execute()
    except Exception:
        # Non-fatal: the SOS event was already created.
        logger.exception(
            "Failed to insert initial live_tracking for SOS %s", sos_id
        )

    # --- STEP 5: Enqueue background SMS ---
    background_tasks.add_task(
        _send_sos_sms, victim_id, full_name, sos_id
    )

    return SOSResponse(
        success=True,
        message="SOS event created and contacts are being notified.",
        sos_id=sos_id,
        is_new=True,
    )


# ============================================================
# 5. API ENDPOINTS
# ============================================================

# ----------------------------------------------------------
# Health-check (useful for uptime monitors / deploy probes)
# ----------------------------------------------------------
@app.get("/", tags=["Health"])
async def health_check():
    """Simple liveness probe."""
    return {"status": "ok", "service": "SHE-SHIELD"}


# ----------------------------------------------------------
# 5.1  POST /api/sos/relay  –  Offline Mesh Relay Endpoint
# ----------------------------------------------------------
@app.post(
    "/api/sos/relay",
    response_model=SOSResponse,
    status_code=status.HTTP_201_CREATED,
    tags=["SOS"],
    summary="Receive an SOS relayed over the Bluetooth mesh.",
)
@limiter.limit("10/minute")
async def sos_relay(
    request: Request,
    payload: SOSRelayRequest,
    background_tasks: BackgroundTasks,
):
    """
    **Offline Mesh Endpoint**

    A nearby device with internet relays the victim's broadcast
    payload to this endpoint.  The backend:

    1. Validates `victim_id` + `emergency_token` (does NOT trust
       the relay device).
    2. Performs an idempotency check (active SOS within 2 min).
    3. Creates the SOS event & initial tracking row.
    4. Fires background SMS to all trusted contacts.

    Rate-limited to **10 requests / minute per IP** to mitigate
    botnet / replay abuse by malicious relay nodes.
    """
    relay_ip = _get_client_ip(request)

    return _process_sos(
        victim_id=str(payload.victim_id),
        emergency_token=payload.emergency_token,
        lat=payload.lat,
        lng=payload.lng,
        trigger_method=payload.trigger_method,
        risk_score=payload.risk_score,
        battery_level=payload.battery_level,
        relay_ip=relay_ip,
        background_tasks=background_tasks,
    )


# ----------------------------------------------------------
# 5.2  POST /api/sos/trigger  –  Direct Online Endpoint
# ----------------------------------------------------------
@app.post(
    "/api/sos/trigger",
    response_model=SOSResponse,
    status_code=status.HTTP_201_CREATED,
    tags=["SOS"],
    summary="Trigger an SOS directly when the victim has internet.",
)
@limiter.limit("10/minute")
async def sos_trigger(
    request: Request,
    payload: SOSTriggerRequest,
    background_tasks: BackgroundTasks,
):
    """
    **Direct Online Endpoint**

    Used when the victim's device has internet connectivity and
    can reach the backend without relay.  Performs the same
    authentication, idempotency, DB inserts, and SMS background
    tasks as the relay endpoint.  The `relay_ip` is simply the
    victim's own IP address.
    """
    caller_ip = _get_client_ip(request)

    return _process_sos(
        victim_id=str(payload.victim_id),
        emergency_token=payload.emergency_token,
        lat=payload.lat,
        lng=payload.lng,
        trigger_method=payload.trigger_method,
        risk_score=payload.risk_score,
        battery_level=payload.battery_level,
        relay_ip=caller_ip,
        background_tasks=background_tasks,
    )


# ----------------------------------------------------------
# 5.3  POST /api/sos/location  –  Live Tracking Update
# ----------------------------------------------------------
@app.post(
    "/api/sos/location",
    response_model=LocationResponse,
    tags=["Tracking"],
    summary="Push a live-tracking telemetry update.",
)
@limiter.limit("30/minute")
async def sos_location_update(
    request: Request,
    payload: LocationUpdateRequest,
):
    """
    **Live Tracking Update**

    Called periodically by the victim's device while an SOS is
    active.  Performs an **UPSERT** on `live_tracking` keyed by
    `sos_id` so only the latest coordinates are stored.

    Authentication is via `victim_id` + `emergency_token`.
    """

    # --- Authenticate ---
    profile = _verify_emergency_token(
        str(payload.victim_id), payload.emergency_token
    )
    if profile is None:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid victim_id or emergency_token.",
        )

    # --- Build the upsert row ---
    tracking_row: Dict[str, Any] = {
        "sos_id": str(payload.sos_id),
        "victim_id": str(payload.victim_id),
        "lat": payload.lat,
        "lng": payload.lng,
        "updated_at": datetime.now(timezone.utc).isoformat(),
    }

    # Only include optional fields if the client sent them
    if payload.accuracy is not None:
        tracking_row["accuracy"] = payload.accuracy
    if payload.speed is not None:
        tracking_row["speed"] = payload.speed
    if payload.heading is not None:
        tracking_row["heading"] = payload.heading
    if payload.battery_level is not None:
        tracking_row["battery_level"] = payload.battery_level

    # --- Upsert (on conflict with PK `sos_id`) ---
    result = (
        supabase.table("live_tracking")
        .upsert(tracking_row, on_conflict="sos_id")
        .execute()
    )

    if not result.data or len(result.data) == 0:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="Failed to upsert tracking data.",
        )

    logger.info(
        "Live tracking updated for SOS %s (lat=%s, lng=%s)",
        payload.sos_id,
        payload.lat,
        payload.lng,
    )

    return LocationResponse(
        success=True,
        message="Location updated.",
        sos_id=payload.sos_id,
    )


# ----------------------------------------------------------
# 5.4  POST /api/sos/resolve  –  Mark SOS Resolved
# ----------------------------------------------------------
@app.post(
    "/api/sos/resolve",
    response_model=ResolveResponse,
    tags=["SOS"],
    summary="Resolve an active SOS (victim confirms they are safe).",
)
@limiter.limit("10/minute")
async def sos_resolve(
    request: Request,
    payload: SOSResolveRequest,
    background_tasks: BackgroundTasks,
):
    """
    **SOS Resolution**

    Only the real victim can resolve their SOS – they must provide
    their `secure_pin`.  This:

    1. Verifies `victim_id` + `secure_pin` against `profiles`.
    2. Updates the SOS event's `status` to `resolved` and sets
       `resolved_at` to the current UTC time.
    3. Fires a background SMS notifying contacts the victim is safe.
    """

    # --- Authenticate via secure PIN ---
    profile = _verify_secure_pin(
        str(payload.victim_id), payload.secure_pin
    )
    if profile is None:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid victim_id or secure_pin.",
        )

    full_name: str = profile["full_name"]
    now_utc = datetime.now(timezone.utc)

    # --- Update the SOS event ---
    update_result = (
        supabase.table("sos_events")
        .update(
            {
                "status": "resolved",
                "resolved_at": now_utc.isoformat(),
            }
        )
        .eq("id", str(payload.sos_id))
        .eq("victim_id", str(payload.victim_id))
        .eq("status", "active")            # Guard: only resolve active SOS
        .execute()
    )

    if not update_result.data or len(update_result.data) == 0:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="No active SOS found with the given sos_id for this victim.",
        )

    logger.info(
        "SOS %s resolved by victim %s", payload.sos_id, payload.victim_id
    )

    # --- Background: send "User is Safe" SMS ---
    background_tasks.add_task(
        _send_safe_sms,
        str(payload.victim_id),
        full_name,
        str(payload.sos_id),
    )

    return ResolveResponse(
        success=True,
        message="SOS resolved. Contacts are being notified you are safe.",
        sos_id=payload.sos_id,
        resolved_at=now_utc,
    )


# ============================================================
# 6. ENTRY POINT
# ============================================================

if __name__ == "__main__":
    import uvicorn

    uvicorn.run(
        "main:app",
        host="0.0.0.0",
        port=8000,
        reload=True,
        log_level="info",
    )
