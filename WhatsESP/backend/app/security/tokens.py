"""
app/security/tokens.py

Objetivo:
- Validar Bearer Token (Authorization: Bearer <token>)
- Guardamos SOLO el hash (SHA-256) en BD
- Comprobamos: existe, no revocado, no expirado, usuario activo
- (Opcional) actualizamos last_seen_at del dispositivo
"""

from __future__ import annotations

from datetime import datetime
import hashlib

from fastapi import Depends, HTTPException
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from sqlalchemy import text

from app.database import engine


# Lee el header Authorization y extrae Bearer <token>
bearer_scheme = HTTPBearer(auto_error=False)


def _hash_token(raw_token: str) -> str:
    """Devuelve SHA-256 hex del token (lo que comparamos con tokens.token_hash)."""
    return hashlib.sha256(raw_token.encode("utf-8")).hexdigest()


def get_current_principal(
    credentials: HTTPAuthorizationCredentials | None = Depends(bearer_scheme),
) -> dict:
    """
    Devuelve un "principal" (info mínima del usuario/dispositivo) si el token es válido.
    Lanza 401/403 si no.
    """
    if credentials is None or credentials.scheme.lower() != "bearer":
        raise HTTPException(status_code=401, detail="Missing bearer token")

    raw_token = credentials.credentials.strip()
    if not raw_token:
        raise HTTPException(status_code=401, detail="Missing bearer token")

    token_hash = _hash_token(raw_token)
    now = datetime.utcnow()

    with engine.begin() as conn:
        row = conn.execute(
            text(
                """
                SELECT
                  t.id          AS token_id,
                  t.user_id     AS user_id,
                  t.device_id   AS device_id,
                  t.expires_at  AS expires_at,
                  t.revoked_at  AS revoked_at,
                  u.username    AS username,
                  u.is_active   AS is_active,
                  d.device_uuid AS device_uuid,
                  d.device_name AS device_name
                FROM tokens t
                JOIN users u   ON u.id = t.user_id
                JOIN devices d ON d.id = t.device_id
                WHERE t.token_hash = :h
                LIMIT 1
                """
            ),
            {"h": token_hash},
        ).mappings().first()

        if not row:
            raise HTTPException(status_code=401, detail="Invalid token")

        if row["revoked_at"] is not None:
            raise HTTPException(status_code=401, detail="Token revoked")

        # expires_at puede ser NULL, pero en nuestro MVP lo ponemos siempre
        expires_at = row["expires_at"]
        if expires_at is not None and expires_at <= now:
            raise HTTPException(status_code=401, detail="Token expired")

        if int(row["is_active"]) != 1:
            raise HTTPException(status_code=403, detail="User inactive")

        # (Opcional) marcar último uso del dispositivo
        conn.execute(
            text("UPDATE devices SET last_seen_at = :now WHERE id = :did"),
            {"now": now, "did": int(row["device_id"])},
        )

        return {
            "token_id": int(row["token_id"]),
            "user_id": int(row["user_id"]),
            "username": str(row["username"]),
            "device_id": int(row["device_id"]),
            "device_uuid": str(row["device_uuid"]),
            "device_name": row["device_name"],
            "expires_at": expires_at,
        }
