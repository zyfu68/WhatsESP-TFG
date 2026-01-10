"""
app/routes/auth.py

Autenticación MVP:
- Credencial compartida (AUTH_SHARED_SECRET en .env)
- Multi-dispositivo mediante device_uuid
- Genera token y devuelve SOLO al cliente (en claro)
- En BD guardamos SOLO el hash (tokens.token_hash)

Tablas implicadas:
- users (username, is_active)
- devices (user_id, device_uuid, device_name, last_seen_at)
- tokens (user_id, device_id, token_hash, expires_at, revoked_at)
"""

from __future__ import annotations

# -------------------------
# Imports estándar
# -------------------------
import os
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

# Importa el engine (y de paso carga .env si tu database.py lo hace)
from app.database import engine
from app.security.tokens import get_current_principal

router = APIRouter(prefix="/auth", tags=["auth"])


# -------------------------
# DTOs (entrada/salida)
# -------------------------
class LoginRequest(BaseModel):
    # Usuario existente en tabla users
    username: str = Field(..., min_length=1, max_length=50)

    # Credencial compartida (MVP)
    credential: str = Field(..., min_length=1, max_length=256)

    # Identificador único del dispositivo (lo genera el cliente y lo persiste)
    device_uuid: str = Field(..., min_length=3, max_length=64)

    # Nombre opcional del dispositivo
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
def _hash_token(raw_token: str) -> str:
    """Devuelve SHA-256 hex del token (esto es lo que guardamos en BD)."""
    return hashlib.sha256(raw_token.encode("utf-8")).hexdigest()


# -------------------------
# Endpoints
# -------------------------
@router.post("/login", response_model=LoginResponse)
def login(payload: LoginRequest) -> LoginResponse:
    """
    Login MVP:
    1) Verifica credencial compartida (AUTH_SHARED_SECRET)
    2) Verifica usuario activo en BD
    3) Registra/reutiliza device_uuid (multi-dispositivo)
    4) Genera token y guarda su hash en tokens con expiración
    """
    # 1) Validar credencial compartida
    shared_secret = os.getenv("AUTH_SHARED_SECRET")
    if not shared_secret:
        # Error de configuración (no del usuario)
        raise HTTPException(status_code=500, detail="AUTH_SHARED_SECRET not configured")

    if payload.credential != shared_secret:
        raise HTTPException(status_code=401, detail="Invalid credentials")

    # 2) Preparar tiempos y token
    now = datetime.utcnow()
    expires_at = now + timedelta(days=30)

    raw_token = secrets.token_urlsafe(32)  # token en claro SOLO para el cliente
    token_hash = _hash_token(raw_token)    # hash para BD

    username = payload.username.strip()

    # 3) Operaciones BD en transacción
    with engine.begin() as conn:
        # 3.1) Usuario activo
        user = conn.execute(
            text("SELECT id, is_active FROM users WHERE username = :u LIMIT 1"),
            {"u": username},
        ).mappings().first()

        if not user or int(user["is_active"]) != 1:
            raise HTTPException(status_code=401, detail="Invalid credentials")

        user_id = int(user["id"])

        # 3.2) Buscar dispositivo por device_uuid
        device = conn.execute(
            text("SELECT id, user_id FROM devices WHERE device_uuid = :du LIMIT 1"),
            {"du": payload.device_uuid},
        ).mappings().first()

        if device:
            # Si ese device_uuid ya existe pero pertenece a otro usuario: fuera.
            if int(device["user_id"]) != user_id:
                raise HTTPException(status_code=403, detail="Device already registered to another user")

            device_id = int(device["id"])

            # Actualiza last_seen_at y, si llega, device_name
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
            # Crear dispositivo
            res = conn.execute(
                text(
                    """
                    INSERT INTO devices (user_id, device_uuid, device_name, last_seen_at)
                    VALUES (:uid, :du, :dn, :now)
                    """
                ),
                {"uid": user_id, "du": payload.device_uuid, "dn": payload.device_name, "now": now},
            )
            device_id = int(res.lastrowid)

        # 3.3) Guardar token_hash en BD (revoked_at NULL por defecto)
        conn.execute(
            text(
                """
                INSERT INTO tokens (user_id, device_id, token_hash, expires_at)
                VALUES (:uid, :did, :th, :exp)
                """
            ),
            {"uid": user_id, "did": device_id, "th": token_hash, "exp": expires_at},
        )

    # 4) Respuesta al cliente
    return LoginResponse(access_token=raw_token, expires_at=expires_at)


@router.post("/logout", response_model=LogoutResponse)
def logout(principal: dict = Depends(get_current_principal)) -> LogoutResponse:
    """
    Logout MVP:
    - Requiere Bearer token válido.
    - Revoca *ese* token marcando revoked_at.

    Resultado esperado:
    - /auth/logout -> 200
    - Cualquier endpoint protegido con ese token -> 401 (Token revoked)
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
