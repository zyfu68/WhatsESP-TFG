"""
Modelo: devices
- Dispositivos autorizados por usuario (control de dispositivo)
"""

import sqlalchemy as sa
from app.database import Base

class Device(Base):
    __tablename__ = "devices"

    id = sa.Column(sa.Integer, primary_key=True, autoincrement=True)

    # Relación con users
    user_id = sa.Column(sa.Integer, sa.ForeignKey("users.id"), nullable=False, index=True)

    # Identidad del dispositivo
    device_uuid = sa.Column(sa.String(64), unique=True, nullable=False)   # UUID o id generado por la app
    device_name = sa.Column(sa.String(100), nullable=True)

    # Auditoría
    created_at = sa.Column(sa.DateTime, nullable=False, server_default=sa.text("CURRENT_TIMESTAMP"))
    last_seen_at = sa.Column(sa.DateTime, nullable=True)
