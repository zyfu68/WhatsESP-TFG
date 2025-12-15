from fastapi import FastAPI
from sqlalchemy import text

from app.database import engine

app = FastAPI(title="WhatsESP API", version="0.1.0")

@app.get("/status")
def status():
    return {"status": "ok"}

@app.get("/health/db")
def health_db():
    with engine.connect() as conn:
        conn.execute(text("SELECT 1"))
    return {"db": "ok"}
