from fastapi import FastAPI, Depends
from sqlalchemy import text

from app.database import engine
from app.routes.auth import router as auth_router
from app.routes.devices import router as devices_router
from app.routes.chats import router as chats_router
from app.security.tokens import get_current_principal

app = FastAPI(title="WhatsESP API", version="0.1.0")

# Rutas
app.include_router(auth_router)
app.include_router(devices_router)
app.include_router(chats_router)


@app.get("/status")
def status():
    return {"status": "ok"}


@app.get("/health/db")
def health_db():
    with engine.connect() as conn:
        conn.execute(text("SELECT 1"))
    return {"db": "ok"}


@app.get("/me")
def me(principal: dict = Depends(get_current_principal)):
    return {
        "user_id": principal["user_id"],
        "username": principal["username"],
        "device_uuid": principal["device_uuid"],
        "device_name": principal["device_name"],
        "expires_at": principal["expires_at"],
    }
