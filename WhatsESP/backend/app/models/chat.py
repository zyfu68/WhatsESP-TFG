"""
Modelo: chats
- Chats directos o grupos
"""

import sqlalchemy as sa
from app.database import Base

class Chat(Base):
    __tablename__ = "chats"

    id = sa.Column(sa.Integer, primary_key=True, autoincrement=True)

    # "direct" o "group"
    kind = sa.Column(sa.String(10), nullable=False)

    # Para grupos (en direct puede ir null)
    title = sa.Column(sa.String(120), nullable=True)

    # Quién lo crea
    created_by_user_id = sa.Column(sa.Integer, sa.ForeignKey("users.id"), nullable=False, index=True)

    created_at = sa.Column(sa.DateTime, nullable=False, server_default=sa.text("CURRENT_TIMESTAMP"))
