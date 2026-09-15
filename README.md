# WhatsESP

WhatsESP es un proyecto desarrollado como Proyecto de Administración de Sistemas Informáticos en Red (ASIR).

Consiste en una aplicación Android de mensajería de uso restringido, acompañada de un backend REST y una base de datos MySQL.

## Arquitectura

- **Cliente:** Android, Kotlin y Jetpack Compose.
- **Backend:** Python y FastAPI.
- **Base de datos:** MySQL.
- **ORM y migraciones:** SQLAlchemy y Alembic.
- **Servidor:** Ubuntu Server.
- **Servidor de aplicaciones:** Uvicorn.
- **Proxy inverso:** Nginx.
- **Despliegue de demostración:** Cloudflare Tunnel.

## Funcionalidades principales

- Autenticación de usuarios.
- Sesiones mediante tokens Bearer.
- Asociación de dispositivos a usuarios.
- Chats individuales.
- Envío y recepción de mensajes.
- Revocación remota de sesiones.
- Envío de alertas SOS con ubicación puntual.
- Persistencia de usuarios, dispositivos, mensajes y eventos de emergencia.

## Estructura del repositorio

- `WhatsESP/android/`: aplicación Android.
- `WhatsESP/backend/`: API FastAPI y acceso a MySQL.
- `WhatsESP/deployment/`: configuraciones de despliegue.
- `APK/release/`: versión compilada para evaluación.

## Configuración

Las credenciales y secretos reales no se almacenan en este repositorio.

El backend incluye un archivo `.env.example` como referencia. Para una instalación propia debe crearse un archivo `.env` con los valores correspondientes al entorno.

## Seguridad

Los tokens de sesión no se almacenan en claro en la base de datos, sino mediante su hash SHA-256. Las sesiones pueden expirar o ser revocadas.

El proyecto no implementa cifrado de extremo a extremo (E2EE).

## Estado

Versión final del proyecto académico: **v1.0**.
