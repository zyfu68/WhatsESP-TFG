from datetime import datetime
from decimal import Decimal

from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel, Field
from sqlalchemy import text

from app.database import engine
from app.security.tokens import get_current_principal


router = APIRouter(prefix="/emergency", tags=["emergency"])


class EmergencyCreateIn(BaseModel):
    latitude: Decimal = Field(..., ge=-90, le=90)
    longitude: Decimal = Field(..., ge=-180, le=180)
    note: str | None = Field(default=None, max_length=255)


class EmergencyCreateOut(BaseModel):
    detail: str = "emergency event created"
    event_id: int
    user_id: int
    device_id: int
    latitude: Decimal
    longitude: Decimal
    note: str | None = None
    created_at: datetime


class EmergencyLatestOut(BaseModel):
    id: int
    user_id: int
    username: str
    device_id: int
    latitude: Decimal
    longitude: Decimal
    note: str | None = None
    created_at: datetime


@router.post("", response_model=EmergencyCreateOut)
def create_emergency_event(
    payload: EmergencyCreateIn,
    principal: dict = Depends(get_current_principal),
) -> EmergencyCreateOut:
    user_id = int(principal["user_id"])
    device_id = int(principal["device_id"])

    now = datetime.utcnow()

    with engine.begin() as conn:
        result = conn.execute(
            text(
                """
                INSERT INTO emergency_events (
                    user_id,
                    device_id,
                    latitude,
                    longitude,
                    note,
                    created_at
                )
                VALUES (
                    :uid,
                    :did,
                    :lat,
                    :lon,
                    :note,
                    :created_at
                )
                """
            ),
            {
                "uid": user_id,
                "did": device_id,
                "lat": payload.latitude,
                "lon": payload.longitude,
                "note": payload.note,
                "created_at": now,
            },
        )

        event_id = int(result.lastrowid or 0)

        if event_id <= 0:
            raise HTTPException(status_code=500, detail="Emergency event could not be created")

    return EmergencyCreateOut(
        event_id=event_id,
        user_id=user_id,
        device_id=device_id,
        latitude=payload.latitude,
        longitude=payload.longitude,
        note=payload.note,
        created_at=now,
    )


@router.get("/latest", response_model=EmergencyLatestOut)
def get_latest_emergency(
    principal: dict = Depends(get_current_principal),
) -> EmergencyLatestOut:
    with engine.connect() as conn:
        row = conn.execute(
            text(
                """
                SELECT
                    e.id,
                    e.user_id,
                    u.username,
                    e.device_id,
                    e.latitude,
                    e.longitude,
                    e.note,
                    e.created_at
                FROM emergency_events e
                JOIN users u ON u.id = e.user_id
                ORDER BY e.id DESC
                LIMIT 1
                """
            )
        ).mappings().first()

    if not row:
        raise HTTPException(status_code=404, detail="No emergency events found")

    return EmergencyLatestOut(
        id=int(row["id"]),
        user_id=int(row["user_id"]),
        username=row["username"],
        device_id=int(row["device_id"]),
        latitude=row["latitude"],
        longitude=row["longitude"],
        note=row["note"],
        created_at=row["created_at"],
    )
