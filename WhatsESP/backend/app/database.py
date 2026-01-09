"""
app/database.py

Objetivo:
- Cargar variables de entorno (.env)
- Construir la URL de conexión a MySQL (soporta contraseñas con @, !, etc.)
- Crear el engine de SQLAlchemy
- Crear SessionLocal
- Definir Base (modelos)
- Proveer get_db() para FastAPI
"""

# -------------------------
# Imports estándar
# -------------------------
import os
from pathlib import Path
from typing import Generator

# -------------------------
# Imports de terceros
# -------------------------
from dotenv import load_dotenv
from sqlalchemy import create_engine
from sqlalchemy.engine import URL
from sqlalchemy.orm import sessionmaker, declarative_base

# -------------------------
# 1) Cargar .env (ruta explícita para evitar líos de cwd)
# -------------------------
ENV_PATH = Path(__file__).resolve().parents[1] / ".env"  # .../backend/.env
load_dotenv(dotenv_path=ENV_PATH)

DB_HOST = os.getenv("DB_HOST", "127.0.0.1")
DB_PORT = int(os.getenv("DB_PORT", "3306"))
DB_NAME = os.getenv("DB_NAME", "whatesp")
DB_USER = os.getenv("DB_USER", "whatesp")
DB_PASSWORD = os.getenv("DB_PASSWORD", "")

# -------------------------
# 2) URL segura (NO concatenar strings con passwords raras)
# -------------------------
DATABASE_URL = URL.create(
    "mysql+pymysql",
    username=DB_USER,
    password=DB_PASSWORD,   # aquí ya puede haber @ ! lo que sea
    host=DB_HOST,
    port=DB_PORT,
    database=DB_NAME,
    query={"charset": "utf8mb4"},
)

# -------------------------
# 3) Engine
# -------------------------
engine = create_engine(
    DATABASE_URL,
    pool_pre_ping=True,
)

# -------------------------
# 4) SessionLocal
# -------------------------
SessionLocal = sessionmaker(
    autocommit=False,
    autoflush=False,
    bind=engine,
)

# -------------------------
# 5) Base
# -------------------------
Base = declarative_base()

# -------------------------
# 6) get_db()
# -------------------------
def get_db() -> Generator:
    """
    Devuelve una sesión y la cierra al terminar.
    Se usa con Depends(get_db) en endpoints.
    """
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()
