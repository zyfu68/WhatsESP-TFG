"""
app/routes/devices.py

Hito B (MVP defendible):
- Listar dispositivos del usuario autenticado
- Revocar (logout remoto) todas las sesiones/tokens de un dispositivo

Endpoints:
- GET  /devices
- POST /devices/{device_id}/revoke
"""

from __future__ import annotations

from datetime import datetime
from typing import List

from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel
from sqlalchemy import text

from app.database import engine
from app.security.tokens import get_current_principal

router = APIRouter(prefix="/devices", tags=["devices"])


# -------------------------
# DTOs (salida)
# -------------------------
class DeviceOut(BaseModel):
    id: int
    device_uuid: str
    device_name: str | None = None
    last_seen_at: datetime | None = None
    is_current: bool = False


class RevokeResponse(BaseModel):
    detail: str = "device tokens revoked"
    device_id: int
    revoked_tokens: int


# -------------------------
# Endpoints
# -------------------------
@router.get("", response_model=List[DeviceOut])
def list_devices(principal: dict = Depends(get_current_principal)) -> List[DeviceOut]:
    """
    Lista los dispositivos asociados al usuario autenticado.
    - No expone tokens
    - Marca cuál es el dispositivo actual (según device_uuid del token actual)
    """
    user_id = int(principal["user_id"])
    current_device_uuid = principal.get("device_uuid")

    with engine.connect() as conn:
        rows = conn.execute(
            text(
                """
                SELECT id, device_uuid, device_name, last_seen_at
                FROM devices
                WHERE user_id = :uid
                ORDER BY last_seen_at DESC, id DESC
                """
            ),
            {"uid": user_id},
        ).mappings().all()

    result: List[DeviceOut] = []
    for r in rows:
        result.append(
            DeviceOut(
                id=int(r["id"]),
                device_uuid=r["device_uuid"],
                device_name=r.get("device_name"),
                last_seen_at=r.get("last_seen_at"),
                is_current=(r["device_uuid"] == current_device_uuid),
            )
        )

    return result


@router.post("/{device_id}/revoke", response_model=RevokeResponse)
def revoke_device_tokens(device_id: int, principal: dict = Depends(get_current_principal)) -> RevokeResponse:
    """
    Revoca (cierra sesión) todas las sesiones/tokens de un dispositivo del usuario.
    Seguridad:
    - Solo permite revocar dispositivos del usuario autenticado
    Nota:
    - Si revocas el dispositivo desde el que llamas, el token actual quedará revocado
      (la respuesta de esta llamada aún saldrá OK; la siguiente petición ya será 401).
    """
    user_id = int(principal["user_id"])
    now = datetime.utcnow()

    with engine.begin() as conn:
        # 1) Comprobar que el dispositivo pertenece al usuario
        device = conn.execute(
            text(
                """
                SELECT id
                FROM devices
                WHERE id = :did AND user_id = :uid
                LIMIT 1
                """
            ),
            {"did": int(device_id), "uid": user_id},
        ).mappings().first()

        if not device:
            raise HTTPException(status_code=404, detail="Device not found")

        # 2) Revocar tokens de ese dispositivo (solo los no revocados)
        res = conn.execute(
            text(
                """
                UPDATE tokens
                SET revoked_at = :now
                WHERE user_id = :uid
                  AND device_id = :did
                  AND revoked_at IS NULL
                """
            ),
            {"now": now, "uid": user_id, "did": int(device_id)},
        )

        revoked_count = int(getattr(res, "rowcount", 0) or 0)

    return RevokeResponse(device_id=int(device_id), revoked_tokens=revoked_count)
