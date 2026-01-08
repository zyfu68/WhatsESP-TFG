"""
Modelo: users
- Usuarios autorizados (sin registro público en MVP)
"""

import sqlalchemy as sa
from app.database import Base

class User(Base):
    __tablename__ = "users"

    # --- Identidad básica ---
    id = sa.Column(sa.Integer, primary_key=True, autoincrement=True)

    # --- Datos mínimos ---
    username = sa.Column(sa.String(50), unique=True, nullable=False)
    full_name = sa.Column(sa.String(120), nullable=True)

    # --- Estado ---
    is_active = sa.Column(sa.Boolean, nullable=False, server_default=sa.text("1"))

    # --- Auditoría ---
    created_at = sa.Column(sa.DateTime, nullable=False, server_default=sa.text("CURRENT_TIMESTAMP"))
