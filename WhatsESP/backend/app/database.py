"""
app/database.py

Responsabilidad:
- Cargar variables de entorno (desde .env)
- Construir la URL de conexión a MySQL
- Crear el engine de SQLAlchemy (conexión)
- Crear el SessionLocal (sesiones/transactions)
- Exponer Base (ORM) para que Alembic pueda autogenerar migraciones
- Proveer get_db() para FastAPI (inyección de dependencias)
"""

# -----------------------------
# 1) Imports (librerías)
# -----------------------------
import os
from typing import Generator

from dotenv import load_dotenv
from sqlalchemy import create_engine
from sqlalchemy.orm import declarative_base, sessionmaker

# -----------------------------
# 2) Cargar variables del .env
#    (si existe en la raíz del backend)
# -----------------------------
load_dotenv()

# -----------------------------
# 3) Leer configuración de BD
#    (con valores por defecto seguros para desarrollo local)
# -----------------------------
DB_HOST = os.getenv("DB_HOST", "127.0.0.1")
DB_PORT = os.getenv("DB_PORT", "3306")
DB_NAME = os.getenv("DB_NAME", "whatesp")
DB_USER = os.getenv("DB_USER", "whatesp")
DB_PASSWORD = os.getenv("DB_PASSWORD", "")

# -----------------------------
# 4) Construir la URL de conexión
#    Driver: mysql + pymysql
#    charset=utf8mb4 para soportar emojis/acentos
# -----------------------------
DATABASE_URL = (
    f"mysql+pymysql://{DB_USER}:{DB_PASSWORD}@{DB_HOST}:{DB_PORT}/{DB_NAME}"
    "?charset=utf8mb4"
)

# -----------------------------
# 5) Base ORM (IMPORTANTE)
#    Alembic usa Base.metadata para detectar tablas y autogenerar migraciones
# -----------------------------
Base = declarative_base()

# -----------------------------
# 6) Engine (conexión) + SessionLocal
#    pool_pre_ping=True evita conexiones “muertas” en el pool
# -----------------------------
engine = create_engine(DATABASE_URL, pool_pre_ping=True)

SessionLocal = sessionmaker(
    autocommit=False,
    autoflush=False,
    bind=engine,
)

# -----------------------------
# 7) Dependencia de FastAPI: get_db()
#    Abre una sesión por request y la cierra al terminar
# -----------------------------
def get_db() -> Generator:
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()
