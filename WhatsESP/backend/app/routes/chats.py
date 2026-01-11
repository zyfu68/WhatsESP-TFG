"""
app/routes/chats.py

Hito C (MVP defendible):
- Crear u obtener chat 1-1 (DM) entre dos usuarios
- Listar chats del usuario
- Enviar mensajes de texto
- Listar mensajes con paginación simple

OJO: tu esquema Alembic usa:
- chats.kind ('dm' / 'group')
- chats.created_by_user_id (obligatorio)
(no existe is_group)
"""

from __future__ import annotations

from datetime import datetime
from typing import List, Optional

from fastapi import APIRouter, Depends, HTTPException, Query
from pydantic import BaseModel, Field
from sqlalchemy import text

from app.database import engine
from app.security.tokens import get_current_principal

router = APIRouter(prefix="/chats", tags=["chats"])


# -------------------------
# DTOs
# -------------------------
class DMCreateIn(BaseModel):
    other_username: str = Field(..., min_length=1, max_length=50)


class DMCreateOut(BaseModel):
    chat_id: int
    created: bool
    other_username: str


class ChatListItem(BaseModel):
    chat_id: int
    is_group: bool
    title: Optional[str]
    created_at: str
    other_username: Optional[str] = None
    last_message_at: Optional[str] = None
    last_message_preview: Optional[str] = None


class MessageCreateIn(BaseModel):
    content: str = Field(..., min_length=1, max_length=2000)


class MessageOut(BaseModel):
    id: int
    chat_id: int
    sender_user_id: int
    content: str
    created_at: str


class MessagesPage(BaseModel):
    items: List[MessageOut]
    next_before_id: Optional[int] = None


# -------------------------
# Helpers
# -------------------------
def _utc_now() -> datetime:
    return datetime.utcnow()


def _ensure_member(conn, chat_id: int, user_id: int) -> None:
    row = conn.execute(
        text(
            """
            SELECT 1
            FROM chat_members
            WHERE chat_id = :cid AND user_id = :uid
            LIMIT 1
            """
        ),
        {"cid": int(chat_id), "uid": int(user_id)},
    ).first()

    if not row:
        raise HTTPException(status_code=404, detail="Chat not found")


# -------------------------
# Endpoints
# -------------------------
@router.post("/dm", response_model=DMCreateOut)
def create_or_get_dm(payload: DMCreateIn, principal: dict = Depends(get_current_principal)) -> DMCreateOut:
    user_id = int(principal["user_id"])
    my_username = str(principal["username"])

    other_username = payload.other_username.strip()
    if not other_username:
        raise HTTPException(status_code=400, detail="other_username required")
    if other_username == my_username:
        raise HTTPException(status_code=400, detail="Cannot DM yourself")

    now = _utc_now()

    with engine.begin() as conn:
        # 1) Usuario destino
        other = conn.execute(
            text(
                """
                SELECT id, username, is_active
                FROM users
                WHERE username = :u
                LIMIT 1
                """
            ),
            {"u": other_username},
        ).mappings().first()

        if not other or int(other["is_active"]) != 1:
            raise HTTPException(status_code=404, detail="User not found")

        other_id = int(other["id"])

        # 2) DM existente (kind='dm' y exactamente 2 miembros)
        existing = conn.execute(
            text(
                """
                SELECT c.id
                FROM chats c
                JOIN chat_members cm1 ON cm1.chat_id = c.id AND cm1.user_id = :u1
                JOIN chat_members cm2 ON cm2.chat_id = c.id AND cm2.user_id = :u2
                WHERE c.kind = 'dm'
                  AND (SELECT COUNT(*) FROM chat_members WHERE chat_id = c.id) = 2
                LIMIT 1
                """
            ),
            {"u1": user_id, "u2": other_id},
        ).mappings().first()

        if existing:
            return DMCreateOut(chat_id=int(existing["id"]), created=False, other_username=other_username)

        # 3) Crear chat DM
        res = conn.execute(
            text(
                """
                INSERT INTO chats (kind, title, created_by_user_id, created_at)
                VALUES ('dm', NULL, :creator, :now)
                """
            ),
            {"creator": user_id, "now": now},
        )
        chat_id = int(res.lastrowid)

        # 4) Miembros (role tiene default 'member')
        conn.execute(
            text("INSERT INTO chat_members (chat_id, user_id, joined_at) VALUES (:c, :u, :now)"),
            {"c": chat_id, "u": user_id, "now": now},
        )
        conn.execute(
            text("INSERT INTO chat_members (chat_id, user_id, joined_at) VALUES (:c, :u, :now)"),
            {"c": chat_id, "u": other_id, "now": now},
        )

        return DMCreateOut(chat_id=chat_id, created=True, other_username=other_username)


@router.get("", response_model=List[ChatListItem])
def list_chats(principal: dict = Depends(get_current_principal)) -> List[ChatListItem]:
    user_id = int(principal["user_id"])

    with engine.begin() as conn:
        chats = conn.execute(
            text(
                """
                SELECT c.id, c.kind, c.title, c.created_at
                FROM chats c
                JOIN chat_members cm ON cm.chat_id = c.id
                WHERE cm.user_id = :uid
                ORDER BY c.id DESC
                """
            ),
            {"uid": user_id},
        ).mappings().all()

        out: List[ChatListItem] = []

        for c in chats:
            chat_id = int(c["id"])
            kind = str(c["kind"])
            is_group = (kind == "group")
            title = c["title"]
            created_at = str(c["created_at"])

            other_username = None
            if not is_group:
                other = conn.execute(
                    text(
                        """
                        SELECT u.username
                        FROM chat_members cm
                        JOIN users u ON u.id = cm.user_id
                        WHERE cm.chat_id = :cid AND cm.user_id <> :uid
                        LIMIT 1
                        """
                    ),
                    {"cid": chat_id, "uid": user_id},
                ).mappings().first()
                if other:
                    other_username = str(other["username"])

            last = conn.execute(
                text(
                    """
                    SELECT id, content, created_at
                    FROM messages
                    WHERE chat_id = :cid
                    ORDER BY created_at DESC, id DESC
                    LIMIT 1
                    """
                ),
                {"cid": chat_id},
            ).mappings().first()

            last_message_at = str(last["created_at"]) if last else None
            last_message_preview = str(last["content"])[:80] if last else None

            out.append(
                ChatListItem(
                    chat_id=chat_id,
                    is_group=is_group,
                    title=title,
                    created_at=created_at,
                    other_username=other_username,
                    last_message_at=last_message_at,
                    last_message_preview=last_message_preview,
                )
            )

        return out


@router.post("/{chat_id}/messages", response_model=MessageOut)
def send_message(chat_id: int, payload: MessageCreateIn, principal: dict = Depends(get_current_principal)) -> MessageOut:
    user_id = int(principal["user_id"])
    content = payload.content.strip()
    if not content:
        raise HTTPException(status_code=400, detail="content required")

    now = _utc_now()

    with engine.begin() as conn:
        _ensure_member(conn, int(chat_id), user_id)

        res = conn.execute(
            text(
                """
                INSERT INTO messages (chat_id, sender_user_id, content, created_at)
                VALUES (:cid, :sid, :content, :now)
                """
            ),
            {"cid": int(chat_id), "sid": user_id, "content": content, "now": now},
        )
        msg_id = int(res.lastrowid)

        return MessageOut(
            id=msg_id,
            chat_id=int(chat_id),
            sender_user_id=user_id,
            content=content,
            created_at=str(now),
        )


@router.get("/{chat_id}/messages", response_model=MessagesPage)
def list_messages(
    chat_id: int,
    limit: int = Query(30, ge=1, le=100),
    before_id: Optional[int] = Query(None, ge=1),
    principal: dict = Depends(get_current_principal),
) -> MessagesPage:
    user_id = int(principal["user_id"])

    with engine.begin() as conn:
        _ensure_member(conn, int(chat_id), user_id)

        if before_id is None:
            rows = conn.execute(
                text(
                    """
                    SELECT id, chat_id, sender_user_id, content, created_at
                    FROM messages
                    WHERE chat_id = :cid
                    ORDER BY id DESC
                    LIMIT :lim
                    """
                ),
                {"cid": int(chat_id), "lim": int(limit)},
            ).mappings().all()
        else:
            rows = conn.execute(
                text(
                    """
                    SELECT id, chat_id, sender_user_id, content, created_at
                    FROM messages
                    WHERE chat_id = :cid AND id < :before
                    ORDER BY id DESC
                    LIMIT :lim
                    """
                ),
                {"cid": int(chat_id), "before": int(before_id), "lim": int(limit)},
            ).mappings().all()

        items_desc: List[MessageOut] = [
            MessageOut(
                id=int(r["id"]),
                chat_id=int(r["chat_id"]),
                sender_user_id=int(r["sender_user_id"]),
                content=str(r["content"]),
                created_at=str(r["created_at"]),
            )
            for r in rows
        ]

        items = list(reversed(items_desc))
        next_before = int(items[0].id) if items else None

        return MessagesPage(items=items, next_before_id=next_before)

