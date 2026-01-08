"""
Modelo: chat_members
- Tabla puente (muchos a muchos): users <-> chats
- PK compuesta (chat_id, user_id)
"""

import sqlalchemy as sa
from app.database import Base

class ChatMember(Base):
    __tablename__ = "chat_members"

    chat_id = sa.Column(sa.Integer, sa.ForeignKey("chats.id"), primary_key=True)
    user_id = sa.Column(sa.Integer, sa.ForeignKey("users.id"), primary_key=True)

    # "member" / "admin" (simple para MVP)
    role = sa.Column(sa.String(10), nullable=False, server_default=sa.text("'member'"))

    joined_at = sa.Column(sa.DateTime, nullable=False, server_default=sa.text("CURRENT_TIMESTAMP"))
