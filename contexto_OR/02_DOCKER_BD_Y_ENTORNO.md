# Entorno Local con Docker, Base de Datos y Seeds

Este documento explica en detalle cómo está armado el entorno Docker en `backend/docker-compose.yml`, el flujo de inicialización automática de PostgreSQL + PostGIS, los datos precargados en `backend/seed_bd.sql` y `backend/seed_dev.sql`, y cómo conectarse desde el backend Go y desde **teléfonos Android físicos**. Todos los comandos `docker compose` siguientes se ejecutan dentro de `oneroute/backend/`.

---

## 1. Servicios incluidos en `docker-compose.yml`

| Servicio | Imagen | Puertos | Propósito |
|---|---|---|---|
| `postgres` | `postgis/postgis:16-3.4` | `5432:5432` | Base de datos PostgreSQL 16 con extensión **PostGIS 3.4** y `pgcrypto`. Ejecuta automáticamente el esquema y seeds en el primer arranque. |
| `mailpit` | `axllent/mailpit:latest` | `1025` (SMTP)<br>`8025` (Web UI) | Servidor SMTP local para capturar correos y códigos OTP enviados en desarrollo (`http://localhost:8025`). |
| `minio` | `coollabsio/minio:latest` | `9000` (API S3)<br>`9001` (Consola) | Almacenamiento de objetos compatible con S3 para fotos, DNI y documentos (`http://localhost:9001`). |
| `minio-init` | `coollabsio/minio:latest` | — | Contenedor efímero que espera a `minio` (`service_healthy`) y crea automáticamente los buckets `rutacompartida-public` (descarga pública) y `rutacompartida-private` (solo presigned URLs). |
| `osrm` *(opcional)* | `ghcr.io/project-osrm/osrm-backend:latest` | `5000:5000` | Motor de ruteo local bajo `profiles: ["osrm-local"]`. Solo arranca si se solicita explícitamente; por defecto el MVP usa `ROUTING_PROVIDER=osrm_public`. |

Red común: `rutacompartida_net` (`bridge`). Volúmenes persistentes: `pg_data` y `minio_data`.

---

## 2. Inicialización automática de la Base de Datos

El servicio `postgres` monta los 3 scripts SQL en `/docker-entrypoint-initdb.d/` en orden alfabético estricto (en modo solo lectura `:ro`):

1. `01_schema.sql` ← `db_rutaCompartida.sql`: crea extensiones (`pgcrypto`, `postgis`), funciones (`fn_excede_dias_calendario`, validaciones de capacidad), **73 tablas**, **60 índices** (17 `GIST` + 43 B-tree), **23 triggers** y **4 vistas**.
2. `02_seed_catalogs.sql` ← `seed_bd.sql`: inserta catálogos base obligatorios (`PEN`, `PE`, 5 roles, 4 métodos de pago, categorías de incidentes/reportes/notificaciones, tarifa de comisión inicial 10% y 19 parámetros en `configuracion`).
3. `03_seed_dev.sql` ← `seed_dev.sql`: inserta usuarios, vehículos, documentos, 3 billeteras, rutas en Lima, un viaje publicado, reserva confirmada, solicitud pendiente, chat y cupón `BIENVENIDO20`.

> **Importante:** `/docker-entrypoint-initdb.d/` solo se ejecuta cuando el volumen `pg_data` está vacío (primer arranque). Si se modifica el esquema o un seed y se desea recargar desde cero:
> ```bash
> docker compose down -v
> cp .env.example .env
> docker compose up -d
> ```

### Verificación rápida tras levantar Docker
```bash
docker compose ps
docker exec -it rutacompartida_postgres psql -U rutacompartida -d rutacompartida -c "SELECT PostGIS_Version(), COUNT(*) AS tablas FROM information_schema.tables WHERE table_schema = 'public';"
```
Debería responder con la versión de PostGIS y `73` tablas base (más vistas = `77` en `information_schema.tables`).

---

## 3. Usuarios y datos precargados en `seed_dev.sql`

Todos los usuarios de prueba comparten la contraseña **`Password123!`** (hasheada con `crypt('Password123!', gen_salt('bf', 10))`, compatible con `golang.org/x/crypto/bcrypt`).

| Correo | Nombre | Roles | Estado clave | UUID fijo |
|---|---|---|---|---|
| `admin@rutacompartida.pe` | Admin Plataforma | `admin`, `soporte`, `moderador` | Operador interno | `10000000-0000-0000-0000-000000000001` |
| `conductor@rutacompartida.pe` | Mateo Mendoza Valdez | `pasajero`, `conductor` | DNI y perfil conductor **aprobados**, vehículo `ABC-782` aprobado (4 plazas), licencia + SOAT + tarjeta propiedad aprobados. Billeteras: Pasajero `S/ 45`, Ganancias `S/ 845`, Comisiones `S/ 35`. | `10000000-0000-0000-0000-000000000002` |
| `pasajero1@rutacompartida.pe` | Lucía Paredes Quispe | `pasajero` | DNI **aprobado**, saldo pasajero `S/ 60`, tiene **1 reserva confirmada** (`RESDEV01`) con chat activo. | `10000000-0000-0000-0000-000000000003` |
| `pasajero2@rutacompartida.pe` | Diego Valdés Torres | `pasajero` | DNI **aprobado**, saldo pasajero `S/ 30`, tiene **1 solicitud pendiente** para probar aceptación/rechazo. | `10000000-0000-0000-0000-000000000004` |
| `nuevo@rutacompartida.pe` | Sofía Rojas Chávez | `pasajero` | **Sin verificar DNI** (`identidad_verificada = FALSE`) para probar bloqueo de reservas. | `10000000-0000-0000-0000-000000000005` |

Otros datos listos:
- **Viaje publicado:** `32000000-0000-0000-0000-000000000001` (`RTDEV001`), ruta `Miraflores → San Isidro`, `precio_asiento_completo = 24.00`, `capacidad_total = 3`.
- **Cupón de descuento:** código `BIENVENIDO20` (20%, tope `S/ 10.00`).

---

## 4. Configuración de `.env.example`

Copiar `.env.example` a `.env` en local. Nunca subir `.env` al repositorio.

| Grupo | Variables clave | Descripción |
|---|---|---|
| **App** | `APP_ENV`, `APP_PORT`, `APP_TIMEZONE` | Puerto del API Go (`8080`) y zona horaria oficial (`America/Lima`) para plazos de 7 días calendario. |
| **DB** | `DATABASE_URL` | Cadena PostgreSQL con `sslmode=disable` en local y `sslmode=require` en nube. |
| **JWT** | `JWT_ACCESS_SECRET`, `JWT_REFRESH_SECRET`, `JWT_ACCESS_TTL_MINUTES`, `JWT_REFRESH_TTL_DAYS` | Access token corto (15 min) y refresh token rotativo (30 días). |
| **MinIO** | `STORAGE_ENDPOINT`, `STORAGE_PUBLIC_URL`, `STORAGE_BUCKET_PUBLIC`, `STORAGE_BUCKET_PRIVATE` | Conexión S3. En nube cambiar por Cloudflare R2 / AWS S3 / Supabase Storage. |
| **OSRM** | `ROUTING_PROVIDER`, `OSRM_BASE_URL`, `OSRM_LOCAL_URL` | `osrm_public` por defecto (sin descargas). Cambiar a `osrm_local` si se levanta el contenedor `osrm`. |
| **Mocks Dev** | `PAYMENT_PROVIDER`, `IDENTITY_PROVIDER`, `OTP_PROVIDER`, `PUSH_PROVIDER` | Todos configurados en modo `mock`/`mailpit_console` para trabajar sin tarjetas ni APIs de pago. |
| **Nube / Móvil** | `CLOUD_PUBLIC_API_URL` | URL HTTPS pública cuando se despliegue para pruebas con teléfonos físicos. |

---

## 5. Cómo activar OSRM local con mapa de Perú (Opcional post-MVP)

Para evitar límites de tasa del servidor público de OSRM en pruebas intensivas o en producción:

```bash
mkdir -p osrm-data
# 1. Descargar extracto OSM de Perú (~150 MB)
curl -L -o osrm-data/peru-latest.osm.pbf https://download.geofabrik.de/south-america/peru-latest.osm.pbf
# 2. Preprocesar con algoritmo MLD
docker run --rm -t -v "$PWD/osrm-data:/data" ghcr.io/project-osrm/osrm-backend osrm-extract -p /opt/car.lua /data/peru-latest.osm.pbf
docker run --rm -t -v "$PWD/osrm-data:/data" ghcr.io/project-osrm/osrm-backend osrm-partition /data/peru-latest.osrm
docker run --rm -t -v "$PWD/osrm-data:/data" ghcr.io/project-osrm/osrm-backend osrm-customize /data/peru-latest.osrm
# 3. Levantar perfil local y cambiar ROUTING_PROVIDER=osrm_local en .env
docker compose --profile osrm-local up -d
```

---

## 6. Pruebas con teléfonos Android físicos y despliegue en nube

- **Emulador Android:** usa `http://10.0.2.2:8080/api/v1` (apunta al `localhost` de la PC).
- **2 teléfonos físicos en la misma red Wi-Fi:**
  1. Verificar IP local de la PC con Go (`ip addr` o `hostname -I`), ej. `192.168.1.45`.
  2. Abrir puerto `8080` en el firewall local si aplica y configurar Android con `http://192.168.1.45:8080/api/v1` (habilitando `usesCleartextTraffic="true"` solo en `debug` manifest).
  3. **Ojo con MinIO en teléfono físico:** `STORAGE_PUBLIC_URL` debe usar la IP LAN (no `localhost`), o el teléfono no podrá descargar fotos de perfil/vehículo.
- **Despliegue compartido en la nube (recomendado para integración):**
  - El backend Go compilado en binario estático (`CGO_ENABLED=0`) pesa ~20 MB y corre en **Railway**, **Render**, **Fly.io** o un VPS con Docker.
  - Exige PostgreSQL con PostGIS habilitado, almacenamiento S3 (Cloudflare R2 / S3 / MinIO), HTTPS/WSS automático (para que Android no requiera cleartext) y actualizar `CLOUD_PUBLIC_API_URL` en la app.
