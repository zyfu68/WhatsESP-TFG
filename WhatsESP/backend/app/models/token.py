"""
Modelo: tokens
- Tokens de sesión por usuario/dispositivo (revocables)
- Guardamos hash del token, NO el token en claro
"""

import sqlalchemy as sa
from app.database import Base

class Token(Base):
    __tablename__ = "tokens"

    id = sa.Column(sa.Integer, primary_key=True, autoincrement=True)

    user_id = sa.Column(sa.Integer, sa.ForeignKey("users.id"), nullable=False, index=True)
    device_id = sa.Column(sa.Integer, sa.ForeignKey("devices.id"), nullable=False, index=True)

    token_hash = sa.Column(sa.String(128), unique=True, nullable=False)

    created_at = sa.Column(sa.DateTime, nullable=False, server_default=sa.text("CURRENT_TIMESTAMP"))
    expires_at = sa.Column(sa.DateTime, nullable=True)
    revoked_at = sa.Column(sa.DateTime, nullable=True)
