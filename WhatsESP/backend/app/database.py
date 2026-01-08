"""
app/database.py

Objetivo:
- Cargar variables de entorno (.env)
- Construir la URL de conexión a MySQL
- Crear el "engine" (conexión) de SQLAlchemy
- Crear SessionLocal (sesiones/transactions)
- Definir Base (clase base de modelos)
- Proveer get_db() para FastAPI (inyección de dependencias)
"""

# -------------------------
# Imports estándar
# -------------------------
import os
from typing import Generator

# -------------------------
# Imports de terceros
# -------------------------
from dotenv import load_dotenv
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker, declarative_base

# -------------------------
# 1) Cargar variables del .env (ubicado en la raíz del backend)
# -------------------------
load_dotenv()

DB_HOST = os.getenv("DB_HOST", "127.0.0.1")
DB_PORT = os.getenv("DB_PORT", "3306")
DB_NAME = os.getenv("DB_NAME", "whatesp")
DB_USER = os.getenv("DB_USER", "whatesp")
DB_PASSWORD = os.getenv("DB_PASSWORD", "")

# -------------------------
# 2) Construir URL de conexión
#    - mysql+pymysql: driver PyMySQL
#    - charset=utf8mb4: emojis/acentos OK
# -------------------------
DATABASE_URL = (
    f"mysql+pymysql://{DB_USER}:{DB_PASSWORD}@{DB_HOST}:{DB_PORT}/{DB_NAME}"
    "?charset=utf8mb4"
)

# -------------------------
# 3) Engine: la “puerta” a la base de datos
#    pool_pre_ping=True evita conexiones muertas
# -------------------------
engine = create_engine(
    DATABASE_URL,
    pool_pre_ping=True,
)

# -------------------------
# 4) SessionLocal: fábrica de sesiones (una sesión = conversación con la BD)
# -------------------------
SessionLocal = sessionmaker(
    autocommit=False,
    autoflush=False,
    bind=engine,
)

# -------------------------
# 5) Base: clase base que usan los modelos para generar tablas/metadata
# -------------------------
Base = declarative_base()

# -------------------------
# 6) get_db(): dependencia típica en FastAPI
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
