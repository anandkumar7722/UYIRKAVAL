"""
============================================================
SHE-SHIELD Backend – Pydantic Models
============================================================
"""

from datetime import datetime
from typing import Optional, List
from uuid import UUID

from pydantic import BaseModel, Field


# ============================================================
# REQUEST MODELS
# ============================================================


class SOSRelayRequest(BaseModel):
    victim_id: str = Field(..., min_length=1)
    lat: float = Field(..., ge=-90.0, le=90.0)
    lng: float = Field(..., ge=-180.0, le=180.0)
    trigger_method: str = Field(..., min_length=1)
    risk_score: Optional[int] = Field(default=None, ge=0, le=100)
    battery_level: Optional[int] = Field(default=None, ge=0, le=100)
    audio_url: Optional[str] = None
    image_urls: Optional[List[str]] = None

    model_config = {"extra": "ignore"}


class SOSTriggerRequest(BaseModel):
    victim_id: str = Field(..., min_length=1)
    lat: float = Field(..., ge=-90.0, le=90.0)
    lng: float = Field(..., ge=-180.0, le=180.0)
    trigger_method: str = Field(..., min_length=1)
    risk_score: Optional[int] = Field(default=None, ge=0, le=100)
    battery_level: Optional[int] = Field(default=None, ge=0, le=100)
    audio_url: Optional[str] = None
    image_urls: Optional[List[str]] = None

    model_config = {"extra": "ignore"}


class LocationUpdateRequest(BaseModel):
    sos_id: UUID = Field(..., description="UUID of the active SOS event.")
    victim_id: str = Field(..., min_length=1)
    lat: float = Field(..., ge=-90.0, le=90.0)
    lng: float = Field(..., ge=-180.0, le=180.0)
    accuracy: Optional[float] = Field(default=None, ge=0.0)
    speed: Optional[float] = Field(default=None, ge=0.0)
    heading: Optional[float] = Field(default=None, ge=0.0, le=360.0)
    battery_level: Optional[int] = Field(default=None, ge=0, le=100)

    model_config = {"extra": "ignore"}


class SOSResolveRequest(BaseModel):
    sos_id: UUID = Field(..., description="UUID of the SOS event to resolve.")
    victim_id: str = Field(..., min_length=1)
    secure_pin: str = Field(..., min_length=1)

    model_config = {"extra": "ignore"}


# ============================================================
# RESPONSE MODELS
# ============================================================


class SOSResponse(BaseModel):
    success: bool
    message: str
    sos_id: UUID
    is_new: bool


class LocationResponse(BaseModel):
    success: bool
    message: str
    sos_id: UUID


class ResolveResponse(BaseModel):
    success: bool
    message: str
    sos_id: UUID
    resolved_at: datetime


class MediaUploadResponse(BaseModel):
    success: bool
    message: str
    sos_id: UUID
    audio_url: Optional[str] = None
    image_urls: Optional[List[str]] = None