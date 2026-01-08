"""
Modelo: messages
- Mensajes de chat (texto en MVP)
"""

import sqlalchemy as sa
from app.database import Base

class Message(Base):
    __tablename__ = "messages"

    id = sa.Column(sa.BigInteger, primary_key=True, autoincrement=True)

    chat_id = sa.Column(sa.Integer, sa.ForeignKey("chats.id"), nullable=False, index=True)
    sender_user_id = sa.Column(sa.Integer, sa.ForeignKey("users.id"), nullable=False, index=True)

    # Contenido (MVP texto)
    content = sa.Column(sa.Text, nullable=False)

    created_at = sa.Column(sa.DateTime, nullable=False, server_default=sa.text("CURRENT_TIMESTAMP"))
