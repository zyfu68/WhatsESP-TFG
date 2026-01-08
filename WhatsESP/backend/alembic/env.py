"""
alembic/env.py

Objetivo:
- Asegurar que Alembic puede importar "app.*" (añadiendo backend a sys.path)
- Cargar Base.metadata (target_metadata) para autogenerate
- Usar la DATABASE_URL del proyecto (misma que usa FastAPI)
"""

from __future__ import annotations

# -------------------------
# Imports estándar
# -------------------------
import sys
from pathlib import Path
from logging.config import fileConfig

# -------------------------
# Imports Alembic / SQLAlchemy
# -------------------------
from alembic import context
from sqlalchemy import engine_from_config, pool

# -------------------------
# Config Alembic
# -------------------------
config = context.config

# Logging (lee alembic.ini)
if config.config_file_name is not None:
    fileConfig(config.config_file_name)

# -------------------------
# 1) Asegurar imports: mete /backend en sys.path
#    __file__ = .../backend/alembic/env.py
#    parents[1] = .../backend
# -------------------------
BASE_DIR = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(BASE_DIR))

# -------------------------
# 2) Importa Base + DATABASE_URL y fuerza a cargar modelos
#    Importar app.models hace que se registren las tablas en Base.metadata
# -------------------------
from app.database import Base, DATABASE_URL  # noqa: E402
import app.models  # noqa: F401, E402  (solo para registrar modelos)

# Esta es la metadata que Alembic usará para autogenerate
target_metadata = Base.metadata

# -------------------------
# 3) Funciones estándar Alembic
# -------------------------
def run_migrations_offline() -> None:
    """
    Modo offline:
    - No conecta a la BD
    - Genera SQL “en seco”
    """
    context.configure(
        url=DATABASE_URL,
        target_metadata=target_metadata,
        literal_binds=True,
        dialect_opts={"paramstyle": "named"},
        compare_type=True,
    )

    with context.begin_transaction():
        context.run_migrations()


def run_migrations_online() -> None:
    """
    Modo online:
    - Conecta a la BD
    - Compara metadata vs BD real y aplica cambios
    """
    # Sobrescribe sqlalchemy.url del alembic.ini con la URL real del proyecto
    configuration = config.get_section(config.config_ini_section) or {}
    configuration["sqlalchemy.url"] = DATABASE_URL

    connectable = engine_from_config(
        configuration,
        prefix="sqlalchemy.",
        poolclass=pool.NullPool,
    )

    with connectable.connect() as connection:
        context.configure(
            connection=connection,
            target_metadata=target_metadata,
            compare_type=True,
        )

        with context.begin_transaction():
            context.run_migrations()


# Ejecuta el modo correspondiente
if context.is_offline_mode():
    run_migrations_offline()
else:
    run_migrations_online()
