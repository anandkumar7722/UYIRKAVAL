"""
============================================================
SHE-SHIELD Backend – Pydantic Models
============================================================
Hackathon / testing edition:

  • victim_id is now a plain TEXT string in Supabase
    (profiles.id TEXT PRIMARY KEY) so test IDs like
    "user_123" or "test_victim" work without UUID generation.

  • emergency_token has been removed from the database.
    The Android frontend still sends it in its JSON payload,
    so every request model accepts it as Optional[str] = None
    to avoid 422 errors.  The backend ignores it completely.

  • secure_pin verification on /api/sos/resolve is kept
    as the only authentication gate.

  • sos_id / SOS event PKs remain UUID (still uuid in DB).
============================================================
"""

from datetime import datetime
from typing import Optional
from uuid import UUID

from pydantic import BaseModel, Field


# ============================================================
# REQUEST MODELS
# ============================================================


class SOSRelayRequest(BaseModel):
    """
    Payload received from the Bluetooth mesh relay network.
    A nearby device with internet forwards the victim's broadcast
    here.  The backend only checks that victim_id exists in the
    profiles table; emergency_token is accepted but ignored.
    """

    victim_id: str = Field(
        ...,
        min_length=1,
        description="Text ID of the victim (matches profiles.id). Any string is accepted.",
    )
    lat: float = Field(
        ...,
        ge=-90.0,
        le=90.0,
        description="Latitude of the victim at SOS trigger time.",
    )
    lng: float = Field(
        ...,
        ge=-180.0,
        le=180.0,
        description="Longitude of the victim at SOS trigger time.",
    )
    trigger_method: str = Field(
        ...,
        min_length=1,
        description="How the SOS was triggered (e.g. 'button', 'shake', 'voice').",
    )
    risk_score: Optional[int] = Field(
        default=None,
        ge=0,
        le=100,
        description="AI-assessed risk score (0-100). Sent when available.",
    )
    battery_level: Optional[int] = Field(
        default=None,
        ge=0,
        le=100,
        description="Victim's phone battery percentage at trigger time.",
    )
    # emergency_token is intentionally absent — removed from the database.
    # The Android frontend may still send it; FastAPI will silently ignore
    # unknown extra fields (extra="ignore" is set on the model config below).

    model_config = {"extra": "ignore"}


class SOSTriggerRequest(BaseModel):
    """
    Payload for direct online SOS trigger (victim has internet).
    Identical fields to SOSRelayRequest.
    """

    victim_id: str = Field(
        ...,
        min_length=1,
        description="Text ID of the victim (matches profiles.id). Any string is accepted.",
    )
    lat: float = Field(..., ge=-90.0, le=90.0)
    lng: float = Field(..., ge=-180.0, le=180.0)
    trigger_method: str = Field(..., min_length=1)
    risk_score: Optional[int] = Field(default=None, ge=0, le=100)
    battery_level: Optional[int] = Field(default=None, ge=0, le=100)

    model_config = {"extra": "ignore"}


class LocationUpdateRequest(BaseModel):
    """
    Live-tracking telemetry update sent periodically while an SOS
    is active.  Uses UPSERT keyed on `sos_id`.
    Authentication: victim_id must exist in profiles.
    """

    sos_id: UUID = Field(
        ...,
        description="UUID of the active SOS event (PK in sos_events).",
    )
    victim_id: str = Field(
        ...,
        min_length=1,
        description="Text ID of the victim – used to verify ownership.",
    )
    lat: float = Field(..., ge=-90.0, le=90.0)

    model_config = {"extra": "ignore"}
    lng: float = Field(..., ge=-180.0, le=180.0)
    accuracy: Optional[float] = Field(
        default=None,
        ge=0.0,
        description="GPS accuracy in metres.",
    )
    speed: Optional[float] = Field(
        default=None,
        ge=0.0,
        description="Speed in m/s.",
    )
    heading: Optional[float] = Field(
        default=None,
        ge=0.0,
        le=360.0,
        description="Compass heading in degrees.",
    )
    battery_level: Optional[int] = Field(default=None, ge=0, le=100)


class SOSResolveRequest(BaseModel):
    """
    Payload to resolve / cancel an active SOS.
    secure_pin is still required – this is the only auth gate.
    """

    sos_id: UUID = Field(
        ...,
        description="UUID of the SOS event to resolve.",
    )
    victim_id: str = Field(
        ...,
        min_length=1,
        description="Text ID of the victim.",
    )
    secure_pin: str = Field(
        ...,
        min_length=4,
        description="Victim's secure PIN set during registration.",
    )


# ============================================================
# RESPONSE MODELS
# ============================================================


class SOSResponse(BaseModel):
    """Returned after successfully creating or deduplicating an SOS."""

    success: bool
    message: str
    sos_id: UUID
    is_new: bool = Field(
        ...,
        description="True if a new SOS event was created; False if idempotent hit.",
    )


class LocationResponse(BaseModel):
    """Returned after a successful live-tracking upsert."""

    success: bool
    message: str
    sos_id: UUID


class ResolveResponse(BaseModel):
    """Returned after successfully resolving an SOS."""

    success: bool
    message: str
    sos_id: UUID
    resolved_at: datetime
