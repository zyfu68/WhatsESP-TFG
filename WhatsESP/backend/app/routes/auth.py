"""
app/routes/auth.py

Autenticación MVP:
- Credencial individual por usuario almacenada como hash SHA-256 en users.credential_hash
- Multi-dispositivo mediante device_uuid
- Genera token y devuelve SOLO al cliente (en claro)
- En BD guardamos SOLO el hash (tokens.token_hash)

Tablas implicadas:
- users (username, credential_hash, is_active)
- devices (user_id, device_uuid, device_name, last_seen_at)
- tokens (user_id, device_id, token_hash, expires_at, revoked_at)
"""

from __future__ import annotations

# -------------------------
# Imports estándar
# -------------------------
import hashlib
import secrets
from datetime import datetime, timedelta

# -------------------------
# Imports FastAPI / Pydantic
# -------------------------
from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel, Field

# -------------------------
# SQLAlchemy (SQL raw con text)
# -------------------------
from sqlalchemy import text

# Importa el engine
from app.database import engine
from app.security.tokens import get_current_principal

router = APIRouter(prefix="/auth", tags=["auth"])


# -------------------------
# DTOs (entrada/salida)
# -------------------------
class LoginRequest(BaseModel):
    username: str = Field(..., min_length=1, max_length=50)
    credential: str = Field(..., min_length=1, max_length=256)
    device_uuid: str = Field(..., min_length=3, max_length=64)
    device_name: str | None = Field(default=None, max_length=100)


class LoginResponse(BaseModel):
    access_token: str
    token_type: str = "bearer"
    expires_at: datetime


class LogoutResponse(BaseModel):
    detail: str = "logged out"


# -------------------------
# Helpers
# -------------------------
def _sha256_hex(value: str) -> str:
    """Devuelve SHA-256 hex de un texto."""
    return hashlib.sha256(value.encode("utf-8")).hexdigest()


def _hash_token(raw_token: str) -> str:
    """Devuelve SHA-256 hex del token."""
    return _sha256_hex(raw_token)


# -------------------------
# Endpoints
# -------------------------
@router.post("/login", response_model=LoginResponse)
def login(payload: LoginRequest) -> LoginResponse:
    """
    Login MVP:
    1) Verifica usuario activo en BD
    2) Verifica credencial individual del usuario
    3) Registra/reutiliza device_uuid
    4) Bloquea device_uuid si pertenece a otro usuario
    5) Genera token y guarda su hash en tokens con expiración
    """
    now = datetime.utcnow()
    expires_at = now + timedelta(days=30)

    raw_token = secrets.token_urlsafe(32)
    token_hash = _hash_token(raw_token)

    username = payload.username.strip()
    credential_hash = _sha256_hex(payload.credential)

    with engine.begin() as conn:
        user = conn.execute(
            text(
                """
                SELECT id, is_active, credential_hash
                FROM users
                WHERE username = :u
                LIMIT 1
                """
            ),
            {"u": username},
        ).mappings().first()

        if not user or int(user["is_active"]) != 1:
            raise HTTPException(status_code=401, detail="Invalid credentials")

        stored_credential_hash = user["credential_hash"]

        if not stored_credential_hash or stored_credential_hash != credential_hash:
            raise HTTPException(status_code=401, detail="Invalid credentials")

        user_id = int(user["id"])

        device = conn.execute(
            text("SELECT id, user_id FROM devices WHERE device_uuid = :du LIMIT 1"),
            {"du": payload.device_uuid},
        ).mappings().first()

        if device:
            if int(device["user_id"]) != user_id:
                raise HTTPException(
                    status_code=403,
                    detail="Device already registered to another user"
                )

            device_id = int(device["id"])

            conn.execute(
                text(
                    """
                    UPDATE devices
                    SET
                      last_seen_at = :now,
                      device_name = COALESCE(:dn, device_name)
                    WHERE id = :id
                    """
                ),
                {"now": now, "dn": payload.device_name, "id": device_id},
            )

        else:
            res = conn.execute(
                text(
                    """
                    INSERT INTO devices (user_id, device_uuid, device_name, last_seen_at)
                    VALUES (:uid, :du, :dn, :now)
                    """
                ),
                {
                    "uid": user_id,
                    "du": payload.device_uuid,
                    "dn": payload.device_name,
                    "now": now,
                },
            )
            device_id = int(res.lastrowid)

        conn.execute(
            text(
                """
                INSERT INTO tokens (user_id, device_id, token_hash, expires_at)
                VALUES (:uid, :did, :th, :exp)
                """
            ),
            {"uid": user_id, "did": device_id, "th": token_hash, "exp": expires_at},
        )

    return LoginResponse(access_token=raw_token, expires_at=expires_at)


@router.post("/logout", response_model=LogoutResponse)
def logout(principal: dict = Depends(get_current_principal)) -> LogoutResponse:
    """
    Logout MVP:
    - Requiere Bearer token válido.
    - Revoca ese token marcando revoked_at.
    """
    now = datetime.utcnow()

    with engine.begin() as conn:
        conn.execute(
            text(
                """
                UPDATE tokens
                SET revoked_at = :now
                WHERE id = :tid AND revoked_at IS NULL
                """
            ),
            {"now": now, "tid": int(principal["token_id"])},
        )

    return LogoutResponse()