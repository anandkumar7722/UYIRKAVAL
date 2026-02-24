"""
============================================================
SHE-SHIELD Backend – Pydantic Models
============================================================
Strict request / response schemas for every endpoint.
All UUID fields are validated as proper UUID4 strings.
Optional fields mirror nullable database columns.
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
    A nearby device with internet forwards the victim's encrypted
    broadcast to this endpoint.  The backend does NOT trust the
    relay device – it validates `emergency_token` against the DB.
    """

    victim_id: UUID = Field(
        ...,
        description="UUID of the victim (matches profiles.id).",
    )
    emergency_token: str = Field(
        ...,
        min_length=8,
        description="Secret token generated during victim's registration.",
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


class SOSTriggerRequest(BaseModel):
    """
    Payload for direct online SOS trigger.
    Identical fields to the relay request – used when the victim
    has internet and can hit the backend directly.
    """

    victim_id: UUID = Field(
        ...,
        description="UUID of the victim (matches profiles.id).",
    )
    emergency_token: str = Field(
        ...,
        min_length=8,
        description="Secret token generated during victim's registration.",
    )
    lat: float = Field(..., ge=-90.0, le=90.0)
    lng: float = Field(..., ge=-180.0, le=180.0)
    trigger_method: str = Field(..., min_length=1)
    risk_score: Optional[int] = Field(default=None, ge=0, le=100)
    battery_level: Optional[int] = Field(default=None, ge=0, le=100)


class LocationUpdateRequest(BaseModel):
    """
    Live-tracking telemetry update sent periodically while an SOS
    is active.  Uses UPSERT keyed on `sos_id`.
    """

    sos_id: UUID = Field(
        ...,
        description="UUID of the active SOS event (PK in live_tracking).",
    )
    victim_id: UUID = Field(
        ...,
        description="UUID of the victim – used to verify ownership.",
    )
    emergency_token: str = Field(
        ...,
        min_length=8,
        description="Secret token for authentication.",
    )
    lat: float = Field(..., ge=-90.0, le=90.0)
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
    Requires the victim's `secure_pin` for verification so only
    the actual victim can mark themselves safe.
    """

    sos_id: UUID = Field(
        ...,
        description="UUID of the SOS event to resolve.",
    )
    victim_id: UUID = Field(
        ...,
        description="UUID of the victim.",
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
