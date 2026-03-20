"""
============================================================
SHE-SHIELD Backend – main.py  (Hackathon / Testing Edition)
============================================================
"""

import logging
import os
import uuid
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
from pydantic import BaseModel
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

load_dotenv()

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s | %(levelname)-8s | %(name)s | %(message)s",
)
logger = logging.getLogger("she-shield")

SUPABASE_URL: str = os.environ["SUPABASE_URL"]
SUPABASE_SERVICE_ROLE_KEY: str = os.environ["SUPABASE_SERVICE_ROLE_KEY"]
supabase: Client = create_client(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY)

FAST2SMS_API_KEY: str = os.getenv("FAST2SMS_API_KEY", "")
FAST2SMS_ENDPOINT: str = "https://www.fast2sms.com/dev/bulkV2"

TRACKING_BASE_URL: str = os.getenv(
    "TRACKING_BASE_URL", "https://yourdomain.com/track"
)

limiter = Limiter(key_func=get_remote_address)

app = FastAPI(
    title="SHE-SHIELD API",
    description="Offline-first women's safety backend.",
    version="1.0.0",
)

app.state.limiter = limiter
app.add_exception_handler(RateLimitExceeded, _rate_limit_exceeded_handler)
app.add_middleware(SlowAPIMiddleware)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


# ============================================================
# 2. PYDANTIC MODELS
# ============================================================

class RegisterRequest(BaseModel):
    email: str
    password: str
    full_name: str
    phone_number: str
    secure_pin: str = "0000"


class LoginRequest(BaseModel):
    email: str
    password: str


class AddGuardianRequest(BaseModel):
    user_id: str
    contact_name: str
    contact_phone: Optional[str] = None
    contact_email: Optional[str] = None
    relation: Optional[str] = None
    notify_via_sms: bool = False
    notify_via_email: bool = True


class UpdateGuardianRequest(BaseModel):
    user_id: str
    contact_name: Optional[str] = None
    contact_phone: Optional[str] = None
    contact_email: Optional[str] = None
    relation: Optional[str] = None
    notify_via_sms: Optional[bool] = None
    notify_via_email: Optional[bool] = None


class DeleteGuardianRequest(BaseModel):
    user_id: str


# ============================================================
# 3. HELPER / UTILITY FUNCTIONS
# ============================================================

def _get_profile(victim_id: str) -> Dict[str, Any]:
    result = (
        supabase.table("profiles")
        .select("id, full_name, phone_number")
        .eq("id", victim_id)
        .execute()
    )
    if result.data and len(result.data) > 0:
        return result.data[0]
    return {"id": victim_id, "full_name": victim_id, "phone_number": ""}


def _verify_secure_pin(
    victim_id: str, secure_pin: str
) -> Optional[Dict[str, Any]]:
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
    forwarded = request.headers.get("x-forwarded-for")
    if forwarded:
        return forwarded.split(",")[0].strip()
    return request.client.host if request.client else "unknown"


# ============================================================
# 4. BACKGROUND TASKS
# ============================================================

async def _send_sos_sms(
    victim_id: str, full_name: str, sos_id: str
) -> None:
    if not FAST2SMS_API_KEY:
        logger.warning("FAST2SMS_API_KEY not configured – skipping SOS SMS")
        return
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
            logger.warning("No SMS contacts found for victim %s", victim_id)
            return

        tracking_url = f"{TRACKING_BASE_URL}/{sos_id}"
        message = (
            f"URGENT SOS: {full_name} triggered an emergency. "
            f"Track live: {tracking_url}"
        )
        phone_numbers = ",".join(c["contact_phone"] for c in contacts)

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
            logger.info("Fast2SMS response [%s]: %s", resp.status_code, resp.text[:200])
    except Exception:
        logger.exception("Failed to send SOS SMS for victim %s", victim_id)


async def _send_safe_sms(
    victim_id: str, full_name: str, sos_id: str
) -> None:
    if not FAST2SMS_API_KEY:
        logger.warning("FAST2SMS_API_KEY not configured – skipping safe SMS")
        return
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
        phone_numbers = ",".join(c["contact_phone"] for c in contacts)

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
            logger.info("Fast2SMS safe-SMS response [%s]: %s", resp.status_code, resp.text[:200])
    except Exception:
        logger.exception("Failed to send safe SMS for victim %s", victim_id)


# ============================================================
# 5. CORE SOS PROCESSING
# ============================================================

def _process_sos(
    *,
    victim_id: str,
    lat: float,
    lng: float,
    trigger_method: str,
    risk_score: Optional[int],
    battery_level: Optional[int],
    relay_ip: str,
    background_tasks: BackgroundTasks,
) -> SOSResponse:
    profile = _get_profile(victim_id)
    full_name: str = profile["full_name"]

    existing_sos = _check_active_sos_within(victim_id, minutes=2)
    if existing_sos is not None:
        return SOSResponse(
            success=True,
            message="Active SOS already exists (idempotent).",
            sos_id=existing_sos["id"],
            is_new=False,
        )

    sos_insert = (
        supabase.table("sos_events")
        .insert({
            "victim_id": victim_id,
            "trigger_method": trigger_method,
            "risk_score": risk_score,
            "initial_lat": lat,
            "initial_lng": lng,
            "status": "active",
            "relay_ip": relay_ip,
        })
        .execute()
    )

    if not sos_insert.data or len(sos_insert.data) == 0:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="Failed to create SOS event.",
        )

    sos_row = sos_insert.data[0]
    sos_id: str = sos_row["id"]

    try:
        supabase.table("live_tracking").insert({
            "sos_id": sos_id,
            "victim_id": victim_id,
            "lat": lat,
            "lng": lng,
            "battery_level": battery_level,
            "updated_at": datetime.now(timezone.utc).isoformat(),
        }).execute()
    except Exception:
        logger.exception("Failed to insert initial live_tracking for SOS %s", sos_id)

    background_tasks.add_task(_send_sos_sms, victim_id, full_name, sos_id)

    return SOSResponse(
        success=True,
        message="SOS event created and contacts are being notified.",
        sos_id=sos_id,
        is_new=True,
    )


# ============================================================
# 6. API ENDPOINTS
# ============================================================

# --- Health ---
@app.get("/", tags=["Health"])
async def health_check():
    """Simple liveness probe."""
    return {"status": "ok", "service": "SHE-SHIELD"}


# --- Auth ---
@app.post("/api/auth/register", status_code=201, tags=["Auth"])
def register(payload: RegisterRequest):
    """Register a new user."""
    existing = (
        supabase.table("profiles")
        .select("id")
        .eq("email", payload.email)
        .execute()
    )
    if existing.data:
        raise HTTPException(status_code=409, detail="Email already registered.")

    user_id = str(uuid.uuid5(uuid.NAMESPACE_DNS, payload.email))

    result = supabase.table("profiles").insert({
        "id": user_id,
        "email": payload.email,
        "full_name": payload.full_name,
        "phone_number": payload.phone_number,
        "password_hash": payload.password,
        "secure_pin": payload.secure_pin,
    }).execute()

    if not result.data:
        raise HTTPException(status_code=500, detail="Failed to create user.")

    return {
        "success": True,
        "message": "User registered successfully.",
        "user_id": user_id,
        "email": payload.email,
        "full_name": payload.full_name,
        "phone_number": payload.phone_number,
    }


@app.post("/api/auth/login", tags=["Auth"])
def login(payload: LoginRequest):
    """Login with email and password."""
    result = (
        supabase.table("profiles")
        .select("id, email, full_name, phone_number")
        .eq("email", payload.email)
        .eq("password_hash", payload.password)
        .execute()
    )

    if not result.data:
        raise HTTPException(status_code=401, detail="Invalid email or password.")

    profile = result.data[0]

    return {
        "success": True,
        "message": "Login successful.",
        "user_id": profile["id"],
        "email": profile["email"],
        "full_name": profile["full_name"],
        "phone_number": profile["phone_number"],
    }


# --- Guardians ---
@app.post("/api/guardians", status_code=201, tags=["Guardians"])
def add_guardian(payload: AddGuardianRequest):
    """Add a new guardian."""
    if not payload.contact_phone and not payload.contact_email:
        raise HTTPException(
            status_code=400,
            detail="At least one of contact_phone or contact_email is required."
        )

    count_result = (
        supabase.table("trusted_contacts")
        .select("id")
        .eq("user_id", payload.user_id)
        .execute()
    )
    if len(count_result.data or []) >= 10:
        raise HTTPException(status_code=400, detail="Maximum 10 guardians allowed.")

    result = supabase.table("trusted_contacts").insert({
        "user_id": payload.user_id,
        "contact_name": payload.contact_name,
        "contact_phone": payload.contact_phone,
        "contact_email": payload.contact_email,
        "relation": payload.relation,
        "notify_via_sms": payload.notify_via_sms,
        "notify_via_email": payload.notify_via_email,
    }).execute()

    if not result.data:
        raise HTTPException(status_code=500, detail="Failed to add guardian.")

    return {
        "success": True,
        "message": "Guardian added successfully.",
        "guardian": result.data[0]
    }


@app.get("/api/guardians/{user_id}", tags=["Guardians"])
def list_guardians(user_id: str):
    """List all guardians for a user."""
    result = (
        supabase.table("trusted_contacts")
        .select("*")
        .eq("user_id", user_id)
        .order("created_at", desc=True)
        .execute()
    )
    return {
        "success": True,
        "message": "Guardians fetched.",
        "guardians": result.data or [],
        "count": len(result.data or [])
    }


@app.put("/api/guardians/{guardian_id}", tags=["Guardians"])
def update_guardian(guardian_id: str, payload: UpdateGuardianRequest):
    """Update an existing guardian."""
    updates = {}
    if payload.contact_name is not None:
        updates["contact_name"] = payload.contact_name
    if payload.contact_phone is not None:
        updates["contact_phone"] = payload.contact_phone
    if payload.contact_email is not None:
        updates["contact_email"] = payload.contact_email
    if payload.relation is not None:
        updates["relation"] = payload.relation
    if payload.notify_via_sms is not None:
        updates["notify_via_sms"] = payload.notify_via_sms
    if payload.notify_via_email is not None:
        updates["notify_via_email"] = payload.notify_via_email

    result = (
        supabase.table("trusted_contacts")
        .update(updates)
        .eq("id", guardian_id)
        .eq("user_id", payload.user_id)
        .execute()
    )

    if not result.data:
        raise HTTPException(status_code=404, detail="Guardian not found.")

    return {
        "success": True,
        "message": "Guardian updated successfully.",
        "guardian": result.data[0]
    }


@app.delete("/api/guardians/{guardian_id}", tags=["Guardians"])
def delete_guardian(guardian_id: str, payload: DeleteGuardianRequest):
    """Delete a guardian."""
    supabase.table("trusted_contacts").delete().eq("id", guardian_id).eq("user_id", payload.user_id).execute()
    return {"success": True, "message": "Guardian deleted successfully."}


# --- SOS ---
@app.post("/api/sos/relay", response_model=SOSResponse, status_code=201, tags=["SOS"])
@limiter.limit("10/minute")
def sos_relay(request: Request, payload: SOSRelayRequest, background_tasks: BackgroundTasks):
    """Receive an SOS relayed over the Bluetooth mesh."""
    return _process_sos(
        victim_id=payload.victim_id,
        lat=payload.lat,
        lng=payload.lng,
        trigger_method=payload.trigger_method,
        risk_score=payload.risk_score,
        battery_level=payload.battery_level,
        relay_ip=_get_client_ip(request),
        background_tasks=background_tasks,
    )


@app.post("/api/sos/trigger", response_model=SOSResponse, status_code=201, tags=["SOS"])
@limiter.limit("10/minute")
def sos_trigger(request: Request, payload: SOSTriggerRequest, background_tasks: BackgroundTasks):
    """Trigger an SOS directly when the victim has internet."""
    return _process_sos(
        victim_id=payload.victim_id,
        lat=payload.lat,
        lng=payload.lng,
        trigger_method=payload.trigger_method,
        risk_score=payload.risk_score,
        battery_level=payload.battery_level,
        relay_ip=_get_client_ip(request),
        background_tasks=background_tasks,
    )


@app.post("/api/sos/location", response_model=LocationResponse, tags=["Tracking"])
@limiter.limit("30/minute")
def sos_location_update(request: Request, payload: LocationUpdateRequest):
    """Push a live-tracking telemetry update."""
    sos_check = (
        supabase.table("sos_events")
        .select("id")
        .eq("id", str(payload.sos_id))
        .eq("victim_id", payload.victim_id)
        .eq("status", "active")
        .execute()
    )
    if not sos_check.data:
        raise HTTPException(status_code=404, detail="No active SOS found with this ID for this victim.")

    tracking_row: Dict[str, Any] = {
        "sos_id": str(payload.sos_id),
        "victim_id": payload.victim_id,
        "lat": payload.lat,
        "lng": payload.lng,
        "updated_at": datetime.now(timezone.utc).isoformat(),
    }
    if payload.accuracy is not None:
        tracking_row["accuracy"] = payload.accuracy
    if payload.speed is not None:
        tracking_row["speed"] = payload.speed
    if payload.heading is not None:
        tracking_row["heading"] = payload.heading
    if payload.battery_level is not None:
        tracking_row["battery_level"] = payload.battery_level

    result = (
        supabase.table("live_tracking")
        .upsert(tracking_row, on_conflict="sos_id")
        .execute()
    )

    if not result.data:
        raise HTTPException(status_code=500, detail="Failed to upsert tracking data.")

    return LocationResponse(success=True, message="Location updated.", sos_id=payload.sos_id)


@app.post("/api/sos/resolve", response_model=ResolveResponse, tags=["SOS"])
@limiter.limit("10/minute")
def sos_resolve(request: Request, payload: SOSResolveRequest, background_tasks: BackgroundTasks):
    """Resolve an active SOS."""
    profile = _get_profile(payload.victim_id)
    full_name: str = profile["full_name"]
    now_utc = datetime.now(timezone.utc)

    update_result = (
        supabase.table("sos_events")
        .update({"status": "resolved", "resolved_at": now_utc.isoformat()})
        .eq("id", str(payload.sos_id))
        .eq("victim_id", payload.victim_id)
        .eq("status", "active")
        .execute()
    )

    if not update_result.data:
        raise HTTPException(status_code=404, detail="No active SOS found.")

    background_tasks.add_task(_send_safe_sms, payload.victim_id, full_name, str(payload.sos_id))

    return ResolveResponse(
        success=True,
        message="SOS resolved. Contacts are being notified you are safe.",
        sos_id=payload.sos_id,
        resolved_at=now_utc,
    )


# ============================================================
# 7. ENTRY POINT
# ============================================================

if __name__ == "__main__":
    import uvicorn
    port = int(os.getenv("PORT", "8000"))
    uvicorn.run("main:app", host="0.0.0.0", port=port, reload=True, log_level="info")