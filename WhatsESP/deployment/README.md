\# Despliegue de WhatsESP



Este directorio contiene copias versionadas de los principales archivos utilizados para desplegar el backend de WhatsESP en Ubuntu Server.



\## Arquitectura de despliegue



El tráfico de la aplicación sigue este recorrido:



Android → Cloudflare Tunnel → Nginx → Uvicorn → FastAPI → SQLAlchemy → MySQL



\## Nginx



El archivo `nginx/whatesp` configura Nginx como proxy inverso.



Nginx escucha las peticiones en el puerto 80 y las reenvía al backend ejecutado mediante Uvicorn en:



`127.0.0.1:8000`



Esto permite que Uvicorn quede restringido a la interfaz local del servidor y no se exponga directamente a la red.



\## Servicio systemd



El archivo `systemd/whatesp-backend.service` permite ejecutar el backend como un servicio del sistema.



El servicio:



\- espera a que la red y Docker estén disponibles;

\- ejecuta Uvicorn dentro del entorno virtual del proyecto;

\- inicia el backend automáticamente al arrancar el servidor;

\- reinicia el proceso en caso de fallo.



Las rutas y el usuario incluidos corresponden al servidor utilizado durante el desarrollo y la demostración del proyecto. Deben adaptarse si el sistema se despliega en otro equipo.



\## Cloudflare Tunnel



El dominio `api.whatesp.app` está configurado mediante Cloudflare Tunnel para dirigir el tráfico hacia:



`http://localhost:80`



La configuración remota, los tokens y las credenciales del túnel no se almacenan en este repositorio.



\## Archivos incluidos



```text

deployment/

├── README.md

├── nginx/

│   └── whatesp

└── systemd/

&#x20;   └── whatesp-backend.service

