"""
app/models/__init__.py

Objetivo:
- Importar TODOS los modelos para que Alembic los “vea”
  cuando hace autogenerate.
"""

from .user import User
from .device import Device
from .token import Token
from .chat import Chat
from .chat_member import ChatMember
from .message import Message
from .emergency_event import EmergencyEvent
