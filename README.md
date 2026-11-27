# RutaCompartida / OneRoute (Monorepo)

Repositorio unificado del proyecto **RutaCompartida (OneRoute)**: aplicación móvil de transporte compartido por tramos (**Kotlin + Jetpack Compose M3**), infraestructura de base de datos (**PostgreSQL 16 + PostGIS**) y backend modular (**Go**).

---

## Estructura del monorepo

```text
oneroute/
├── android/          # App movil Kotlin + Jetpack Compose (abrir esta subcarpeta en Android Studio)
├── backend/          # Esquema SQL, seeds, Docker Compose y backend modular Go
│   ├── db_rutaCompartida.sql
│   ├── seed_bd.sql
│   ├── seed_dev.sql
│   ├── docker-compose.yml
│   ├── .env.example
│   └── RutaCompartida_Documentacion_Avance.docx
├── contexto_OR/      # Documentacion tecnica para el equipo de 6 integrantes y agentes de codigo
└── README.md
```

---

## Inicio rapido

### 1. Base de datos y entorno local (Docker)
Desde la carpeta `backend/`:

```bash
cd backend
cp .env.example .env
docker compose up -d
```

Servicios levantados:
- **PostgreSQL 16 + PostGIS 3.4** (`localhost:5432`): inicializa automaticamente `db_rutaCompartida.sql` (73 tablas), `seed_bd.sql` (catalogos) y `seed_dev.sql` (datos de prueba).
- **Mailpit** (`http://localhost:8025`): captura correos y codigos OTP en desarrollo.
- **MinIO** (`http://localhost:9001`): almacenamiento S3 local con buckets `rutacompartida-public` y `rutacompartida-private`.
- **OSRM local** (opcional): `docker compose --profile osrm-local up -d`.

### 2. Aplicacion movil Android (`android/`)
**Importante:** en Android Studio abrir la subcarpeta `oneroute/android/` (no la raiz del monorepo) para que Gradle reconozca el proyecto.

Desde terminal:
```bash
cd android
./gradlew testDebugUnitTest assembleDebug
```

---

## Documentacion tecnica y trabajo en paralelo

Todo el equipo (6 integrantes: Lider en `backend/internal/shared/` + 1 modulo por persona) y los agentes de codigo deben consultar primero la carpeta `contexto_OR/`:

- [contexto_OR/00_README_CONTEXTO.md](./contexto_OR/00_README_CONTEXTO.md): vision general, stack, reglas de negocio y asignacion del equipo.
- [contexto_OR/01_ARQUITECTURA_Y_SHARED.md](./contexto_OR/01_ARQUITECTURA_Y_SHARED.md): arquitectura Go modular, regla anti-conflictos, matriz de propiedad de las 73 tablas y especificacion de `internal/shared/`.
- [contexto_OR/02_DOCKER_BD_Y_ENTORNO.md](./contexto_OR/02_DOCKER_BD_Y_ENTORNO.md): configuracion Docker, usuarios de prueba (`Password123!`), OSRM y pruebas con telefonos fisicos.
- [contexto_OR/03_MODULOS_Y_DIVISION_EQUIPO.md](./contexto_OR/03_MODULOS_Y_DIVISION_EQUIPO.md): alcance detallado de los 6 modulos (`auth_identity`, `routes_trips`, `search_bookings`, `active_trip_gps`, `payments_wallets`, `messaging_safety_support`).
- [contexto_OR/04_CONTRATO_API_Y_TIEMPO_REAL.md](./contexto_OR/04_CONTRATO_API_Y_TIEMPO_REAL.md): contrato REST `/api/v1` y eventos WebSocket `/api/v1/ws`.
- [contexto_OR/05_INTEGRACIONES_MOCK_Y_PRODUCCION.md](./contexto_OR/05_INTEGRACIONES_MOCK_Y_PRODUCCION.md): OSRM, pasarela de pagos mock, verificacion DNI + OTP mock y reemplazo por servicios reales.
- [contexto_OR/06_GUIA_AGENTES_Y_PRUEBAS.md](./contexto_OR/06_GUIA_AGENTES_Y_PRUEBAS.md): flujo Git en paralelo, estandares Go, pruebas de concurrencia y despliegue en la nube.

---

## Nota para despliegue en la nube (Backend)

Al conectar este monorepo con Railway, Render, Fly.io o VPS:
- Configurar **Root Directory = `backend`** (y *watch path* `backend/**`) para que los commits dentro de `android/` o `contexto_OR/` no disparen redeploys innecesarios del servidor.
