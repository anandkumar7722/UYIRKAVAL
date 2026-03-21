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
    File,
    Form,
    HTTPException,
    Request,
    UploadFile,
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
    MediaUploadResponse,
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

SENDGRID_API_KEY: str = os.getenv("SENDGRID_API_KEY", "")
SENDER_EMAIL: str = os.getenv("GMAIL_USER", "")

TRACKING_BASE_URL: str = os.getenv(
    "TRACKING_BASE_URL", "https://nirbhay-1.onrender.com/track"
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


async def _send_sos_email(
    victim_id: str,
    full_name: str,
    sos_id: str,
    lat: float,
    lng: float,
    trigger_method: str,
    risk_score: int,
    battery_level: int,
    audio_url: Optional[str] = None,
    image_urls: Optional[List[str]] = None,
) -> None:
    if not SENDGRID_API_KEY or not SENDER_EMAIL:
        logger.warning("SendGrid not configured – skipping email for victim %s", victim_id)
        return
    try:
        contacts_result = (
            supabase.table("trusted_contacts")
            .select("contact_email, contact_name")
            .eq("user_id", victim_id)
            .eq("notify_via_email", True)
            .execute()
        )
        contacts = contacts_result.data or []
        if not contacts:
            logger.warning("No email contacts for victim %s", victim_id)
            return

        maps_url = f"https://www.google.com/maps?q={lat},{lng}"
        tracking_url = f"{TRACKING_BASE_URL}/{sos_id}"
        now_utc = datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M:%S UTC")

        # Build evidence section
        evidence_html = ""
        if audio_url:
            evidence_html += f"""
            <tr style="background:#fff8e1">
                <td style="padding:10px;"><b>🎤 Audio Evidence</b></td>
                <td style="padding:10px;">
                    <a href="{audio_url}" style="color:#e53935;">Listen / Download</a>
                    <br><small style="color:#888;">(file attached to this email if downloadable)</small>
                </td>
            </tr>"""
        if image_urls:
            for i, url in enumerate(image_urls, 1):
                evidence_html += f"""
                <tr style="background:{'#fff3f3' if i % 2 == 0 else 'white'}">
                    <td style="padding:10px;"><b>📷 Image {i}</b></td>
                    <td style="padding:10px;">
                        <a href="{url}" style="color:#e53935;">View Image {i}</a>
                        <br><small style="color:#888;">(file attached to this email if downloadable)</small>
                    </td>
                </tr>"""

        evidence_section = ""
        if evidence_html:
            evidence_section = f"""
            <div style="margin-top:20px;">
                <h3 style="color:#333; border-bottom:2px solid #e53935; padding-bottom:8px;">
                    📎 EVIDENCE CAPTURED
                </h3>
                <table style="width:100%; border-collapse:collapse;">
                    {evidence_html}
                </table>
            </div>"""

        html_body = f"""
        <html>
        <body style="font-family: Arial, sans-serif; background-color: #f8f8f8; padding: 20px;">
            <div style="max-width: 600px; margin: auto; background: white; border-radius: 10px; overflow: hidden; box-shadow: 0 2px 10px rgba(0,0,0,0.1);">

                <div style="background-color: #e53935; padding: 20px; text-align: center;">
                    <h1 style="color: white; margin: 0; font-size:24px;">🚨 Emergency SOS Alert</h1>
                    <p style="color: #ffcdd2; margin: 5px 0;">SHE-SHIELD Safety Network  •  Immediate Action Required</p>
                </div>

                <div style="padding: 25px;">
                    <h2 style="color: #e53935;">⚡ {full_name} has triggered an emergency!</h2>

                    <h3 style="color:#333; border-bottom:1px solid #eee; padding-bottom:8px;">Emergency Details</h3>
                    <table style="width: 100%; border-collapse: collapse;">
                        <tr style="background:#fff3f3">
                            <td style="padding:10px; width:40%;"><b>👤 Name</b></td>
                            <td style="padding:10px;">{full_name}</td>
                        </tr>
                        <tr>
                            <td style="padding:10px;"><b>📍 Location</b></td>
                            <td style="padding:10px;">Lat: {lat}, Lng: {lng}</td>
                        </tr>
                        <tr style="background:#fff3f3">
                            <td style="padding:10px;"><b>🌐 GPS</b></td>
                            <td style="padding:10px;">{lat}, {lng}</td>
                        </tr>
                        <tr>
                            <td style="padding:10px;"><b>⚡ Trigger</b></td>
                            <td style="padding:10px;">{trigger_method}</td>
                        </tr>
                        <tr style="background:#fff3f3">
                            <td style="padding:10px;"><b>🔴 Risk Score</b></td>
                            <td style="padding:10px;">{risk_score} /100</td>
                        </tr>
                        <tr>
                            <td style="padding:10px;"><b>🔋 Battery</b></td>
                            <td style="padding:10px;">{battery_level}%</td>
                        </tr>
                        <tr style="background:#fff3f3">
                            <td style="padding:10px;"><b>🕐 Time</b></td>
                            <td style="padding:10px;">{now_utc}</td>
                        </tr>
                        <tr>
                            <td style="padding:10px;"><b>🆔 SOS ID</b></td>
                            <td style="padding:10px;">{sos_id}</td>
                        </tr>
                    </table>

                    <div style="margin: 20px 0; text-align: center;">
                        <a href="{maps_url}" style="background:#4285f4; color:white; padding:12px 24px; border-radius:6px; text-decoration:none; margin-right:10px; display:inline-block;">
                            📍 Open in Google Maps
                        </a>
                        <a href="{tracking_url}" style="background:#e53935; color:white; padding:12px 24px; border-radius:6px; text-decoration:none; display:inline-block;">
                            📡 Live Track Now
                        </a>
                    </div>

                    {evidence_section}

                </div>

                <div style="background:#f5f5f5; padding:15px; text-align:center; color:#888; font-size:12px;">
                    <p>Automated emergency alert from SHE-SHIELD Safety Network</p>
                    <p>Please respond immediately. Do not reply to this email.</p>
                </div>
            </div>
        </body>
        </html>
        """

        for contact in contacts:
            if not contact.get("contact_email"):
                continue
            try:
                async with httpx.AsyncClient(timeout=15.0) as client:
                    resp = await client.post(
                        "https://api.sendgrid.com/v3/mail/send",
                        headers={
                            "Authorization": f"Bearer {SENDGRID_API_KEY}",
                            "Content-Type": "application/json",
                        },
                        json={
                            "personalizations": [{"to": [{"email": contact["contact_email"]}]}],
                            "from": {"email": SENDER_EMAIL, "name": "SHE-SHIELD Safety Network"},
                            "subject": f"🚨 EMERGENCY SOS – {full_name} needs help NOW!",
                            "content": [{"type": "text/html", "value": html_body}],
                        },
                    )
                    if resp.status_code == 202:
                        logger.info("SOS email sent to %s", contact["contact_email"])
                    else:
                        logger.error("SendGrid error [%s]: %s", resp.status_code, resp.text)
            except Exception:
                logger.exception("Failed to send email to %s", contact.get("contact_email"))

    except Exception:
        logger.exception("Failed to send SOS emails for victim %s", victim_id)


async def _send_safe_email(
    victim_id: str, full_name: str, sos_id: str
) -> None:
    if not SENDGRID_API_KEY or not SENDER_EMAIL:
        return
    try:
        contacts_result = (
            supabase.table("trusted_contacts")
            .select("contact_email, contact_name")
            .eq("user_id", victim_id)
            .eq("notify_via_email", True)
            .execute()
        )
        contacts = contacts_result.data or []
        now_utc = datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M:%S UTC")

        html_body = f"""
        <html>
        <body style="font-family: Arial, sans-serif; background:#f8f8f8; padding:20px;">
            <div style="max-width:600px; margin:auto; background:white; border-radius:10px; box-shadow:0 2px 10px rgba(0,0,0,0.1);">
                <div style="background:#43a047; padding:20px; text-align:center;">
                    <h1 style="color:white; margin:0;">✅ Safe Update</h1>
                    <p style="color:#c8e6c9;">SHE-SHIELD Safety Network</p>
                </div>
                <div style="padding:25px;">
                    <h2 style="color:#43a047;">🎉 {full_name} is safe!</h2>
                    <p style="font-size:16px;">{full_name} has marked themselves safe. The emergency has been resolved.</p>
                    <table style="width:100%; border-collapse:collapse;">
                        <tr style="background:#f1f8e9">
                            <td style="padding:10px;"><b>🆔 SOS ID</b></td>
                            <td style="padding:10px;">{sos_id}</td>
                        </tr>
                        <tr>
                            <td style="padding:10px;"><b>🕐 Resolved At</b></td>
                            <td style="padding:10px;">{now_utc}</td>
                        </tr>
                    </table>
                </div>
                <div style="background:#f5f5f5; padding:15px; text-align:center; color:#888; font-size:12px;">
                    <p>Automated alert from SHE-SHIELD Safety Network. Do not reply.</p>
                </div>
            </div>
        </body>
        </html>
        """

        for contact in contacts:
            if not contact.get("contact_email"):
                continue
            try:
                async with httpx.AsyncClient(timeout=15.0) as client:
                    resp = await client.post(
                        "https://api.sendgrid.com/v3/mail/send",
                        headers={
                            "Authorization": f"Bearer {SENDGRID_API_KEY}",
                            "Content-Type": "application/json",
                        },
                        json={
                            "personalizations": [{"to": [{"email": contact["contact_email"]}]}],
                            "from": {"email": SENDER_EMAIL, "name": "SHE-SHIELD Safety Network"},
                            "subject": f"✅ SAFE UPDATE – {full_name} is safe now",
                            "content": [{"type": "text/html", "value": html_body}],
                        },
                    )
                    if resp.status_code == 202:
                        logger.info("Safe email sent to %s", contact["contact_email"])
                    else:
                        logger.error("SendGrid error [%s]: %s", resp.status_code, resp.text)
            except Exception:
                logger.exception("Failed to send safe email to %s", contact.get("contact_email"))
    except Exception:
        logger.exception("Failed to send safe emails for victim %s", victim_id)


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
    background_tasks.add_task(
        _send_sos_email,
        victim_id, full_name, sos_id,
        lat, lng, trigger_method,
        risk_score or 0,
        battery_level or 0,
    )

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
    return {"status": "ok", "service": "SHE-SHIELD"}


# --- Auth ---
@app.post("/api/auth/register", status_code=201, tags=["Auth"])
def register(payload: RegisterRequest):
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
    supabase.table("trusted_contacts").delete().eq("id", guardian_id).eq("user_id", payload.user_id).execute()
    return {"success": True, "message": "Guardian deleted successfully."}


# --- SOS ---
@app.post("/api/sos/relay", response_model=SOSResponse, status_code=201, tags=["SOS"])
@limiter.limit("10/minute")
def sos_relay(request: Request, payload: SOSRelayRequest, background_tasks: BackgroundTasks):
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
    sos_check = (
        supabase.table("sos_events")
        .select("id")
        .eq("id", str(payload.sos_id))
        .eq("victim_id", payload.victim_id)
        .eq("status", "active")
        .execute()
    )
    if not sos_check.data:
        raise HTTPException(
            status_code=404,
            detail="No active SOS found with this ID for this victim."
        )

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

    return LocationResponse(
        success=True,
        message="Location updated.",
        sos_id=payload.sos_id,
    )


@app.post("/api/sos/resolve", response_model=ResolveResponse, tags=["SOS"])
@limiter.limit("10/minute")
def sos_resolve(request: Request, payload: SOSResolveRequest, background_tasks: BackgroundTasks):
    profile = _get_profile(payload.victim_id)
    full_name: str = profile["full_name"]
    now_utc = datetime.now(timezone.utc)

    update_result = (
        supabase.table("sos_events")
        .update({
            "status": "resolved",
            "resolved_at": now_utc.isoformat(),
        })
        .eq("id", str(payload.sos_id))
        .eq("victim_id", payload.victim_id)
        .eq("status", "active")
        .execute()
    )

    if not update_result.data:
        raise HTTPException(status_code=404, detail="No active SOS found.")

    background_tasks.add_task(_send_safe_sms, payload.victim_id, full_name, str(payload.sos_id))
    background_tasks.add_task(_send_safe_email, payload.victim_id, full_name, str(payload.sos_id))

    return ResolveResponse(
        success=True,
        message="SOS resolved. Contacts are being notified you are safe.",
        sos_id=payload.sos_id,
        resolved_at=now_utc,
    )


# --- Media Upload ---
@app.post("/api/sos/media", response_model=MediaUploadResponse, tags=["Evidence"])
@limiter.limit("20/minute")
async def sos_media_upload(
    request: Request,
    background_tasks: BackgroundTasks,
    sos_id: str = Form(...),
    victim_id: str = Form(...),
    audio: Optional[UploadFile] = File(None),
    images: Optional[List[UploadFile]] = File(None),
):
    """Upload audio and images captured at SOS time. Sends updated email with evidence."""

    # Verify SOS is active and belongs to victim
    sos_check = (
        supabase.table("sos_events")
        .select("*")
        .eq("id", sos_id)
        .eq("victim_id", victim_id)
        .eq("status", "active")
        .execute()
    )
    if not sos_check.data:
        raise HTTPException(status_code=404, detail="No active SOS found.")

    sos = sos_check.data[0]
    audio_url = None
    image_urls = []

    # Upload audio
    if audio and audio.filename:
        try:
            audio_bytes = await audio.read()
            ext = audio.filename.split(".")[-1] if "." in audio.filename else "m4a"
            path = f"{sos_id}/audio.{ext}"
            supabase.storage.from_("sos-media").upload(
                path, audio_bytes,
                {"content-type": audio.content_type or "audio/m4a"}
            )
            audio_url = f"{SUPABASE_URL}/storage/v1/object/public/sos-media/{path}"
            logger.info("Audio uploaded: %s", audio_url)
        except Exception:
            logger.exception("Failed to upload audio for SOS %s", sos_id)

    # Upload images
    if images:
        for i, image in enumerate(images):
            if image and image.filename:
                try:
                    image_bytes = await image.read()
                    ext = image.filename.split(".")[-1] if "." in image.filename else "jpg"
                    path = f"{sos_id}/image_{i}.{ext}"
                    supabase.storage.from_("sos-media").upload(
                        path, image_bytes,
                        {"content-type": image.content_type or "image/jpeg"}
                    )
                    url = f"{SUPABASE_URL}/storage/v1/object/public/sos-media/{path}"
                    image_urls.append(url)
                    logger.info("Image %d uploaded: %s", i, url)
                except Exception:
                    logger.exception("Failed to upload image %d for SOS %s", i, sos_id)

    if not audio_url and not image_urls:
        raise HTTPException(status_code=400, detail="At least one file must be provided.")

    # Update sos_events with media URLs
    try:
        supabase.table("sos_events").update({
            "audio_url": audio_url,
            "image_urls": image_urls,
        }).eq("id", sos_id).execute()
    except Exception:
        logger.exception("Failed to update sos_events with media URLs")

    # Get victim profile
    profile = _get_profile(victim_id)
    full_name = profile["full_name"]

    
   # Send updated email WITH evidence immediately
    await _send_sos_email(
    victim_id=victim_id,
    full_name=full_name,
    sos_id=sos_id,
    lat=sos.get("initial_lat", 0),
    lng=sos.get("initial_lng", 0),
    trigger_method=sos.get("trigger_method", "unknown"),
    risk_score=sos.get("risk_score", 0),
    battery_level=sos.get("battery_level", 0),
    audio_url=audio_url,
    image_urls=image_urls,
  )

    return MediaUploadResponse(
        success=True,
        message="Media uploaded and evidence email sent to guardians.",
        sos_id=sos_id,
        audio_url=audio_url,
        image_urls=image_urls,
    )


# ============================================================
# 7. ENTRY POINT
# ============================================================

if __name__ == "__main__":
    import uvicorn
    port = int(os.getenv("PORT", "8000"))
    uvicorn.run("main:app", host="0.0.0.0", port=port, reload=True, log_level="info")