"""
Modelo: emergency_events
- Eventos SOS enviados por usuarios
"""

import sqlalchemy as sa
from app.database import Base

class EmergencyEvent(Base):
    __tablename__ = "emergency_events"

    id = sa.Column(sa.Integer, primary_key=True, autoincrement=True)

    user_id = sa.Column(sa.Integer, sa.ForeignKey("users.id"), nullable=False, index=True)
    device_id = sa.Column(sa.Integer, sa.ForeignKey("devices.id"), nullable=False, index=True)

    latitude = sa.Column(sa.Float, nullable=False)
    longitude = sa.Column(sa.Float, nullable=False)
    note = sa.Column(sa.String(255), nullable=True)

    created_at = sa.Column(sa.DateTime, nullable=False, server_default=sa.text("CURRENT_TIMESTAMP"))
