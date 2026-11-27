# Guía para Agentes de Código, Flujo Git, Pruebas y Despliegue

Este documento define las reglas operativas para que **6 integrantes** (y asistentes/agentes de IA) programen en paralelo sin romper el trabajo de los demás.

---

## 1. Protocolo obligatorio para Agentes e Integrantes

Antes de implementar una tarea en un módulo:
1. Leer `00_README_CONTEXTO.md`, `01_ARQUITECTURA_Y_SHARED.md`, la sección de tu módulo en `03_MODULOS_Y_DIVISION_EQUIPO.md` y sus endpoints en `04_CONTRATO_API_Y_TIEMPO_REAL.md`.
2. Verificar qué **archivos puedes modificar**:
   - **Permitido libremente:** todo dentro de `backend/internal/modules/<tu_modulo>/`, tus tests `*_test.go` y las pantallas asignadas dentro de `android/app/src/main/java/.../feature/<tu_feature>/`.
   - **Con coordinación:** `backend/cmd/api/main.go`, `android/.../core/navigation/Route.kt` y `AppNavHost.kt`.
   - **Prohibido editar directamente** sin PR aprobado por el líder: `backend/internal/shared/*`, `backend/*.sql`, `backend/docker-compose.yml`, `backend/.env.example` y módulos de otros integrantes.
3. **Nunca escribir SQL `INSERT/UPDATE/DELETE` contra tablas de otro módulo.** Si falta un método en `shared/contracts`, pedirlo al líder o proponerlo en un PR separado hacia `shared/contracts`.

---

## 2. Flujo Git para 6 personas en paralelo

- `main`: solo versiones estables listas para demo o despliegue.
- `develop`: rama de integración diaria.
- Ramas de trabajo cortas (1–3 días máximo): `feat/<modulo>-<subtarea>` (ej. `feat/bookings-hold-confirm`, `feat/wallets-settle-cash`).
- **Sincronización diaria:** antes de empezar y antes de abrir PR, ejecutar:
  ```bash
  git fetch origin
  git rebase origin/develop
  ```
- **Reglas de PR:** mínimo 1 revisor (idealmente el líder o quien consume tu contrato), compilación limpia (`go build ./...`) y pruebas en verde (`go test ./...`).

---

## 3. Estándares de código Go (calidad y concurrencia)

1. **Propagar siempre `ctx context.Context`** desde el handler HTTP hasta `pgx` y llamadas externas.
2. **Transacciones críticas con `db.WithTx`:** toda operación que modifique más de una tabla o consulte disponibilidad/saldos debe ejecutarse dentro de `WithTx` y usar `SELECT ... FOR UPDATE` en la fila padre (`viaje`, `reserva`, `billetera`, `solicitud_reserva`).
3. **SQL parametrizado siempre (`$1, $2, ...`):** prohibido concatenar strings en queries.
4. **Idempotencia offline:** todo endpoint que reciba `client_event_id` (GPS, chat, pagos, recargas, SOS, incidentes) debe hacer `ON CONFLICT (client_event_id) DO NOTHING` (o retornar el registro existente si ya fue procesado) para que la cola `WorkManager` de Android pueda reintentar sin duplicar registros.
5. **Manejo de errores:** retornar siempre errores mapeados con `httpx.WriteError(w, err)` que traduzca automáticamente los errores de PostgreSQL/triggers (`db.MapPgError`).

---

## 4. Pruebas mínimas obligatorias (Integración y Concurrencia)

Antes de dar por cerrado un módulo, el repositorio debe contar con pruebas reales contra la BD PostgreSQL + PostGIS de Docker (`go test -tags=integration ./...`):

| Prueba crítica | Módulo(s) | Qué valida |
|---|---|---|
| **Carrera por último asiento en tramos solapados** | `search_bookings` | Lanzar **2 goroutines simultáneas** (`sync.WaitGroup`) confirmando/aceptando reservas en tramos que se cruzan cuando solo queda 1 asiento disponible: exactamente **1 debe triunfar** y **1 debe recibir `409 INSUFFICIENT_CAPACITY`** gracias a `trg_validar_capacidad_reserva` + `FOR UPDATE`. |
| **Hold 60 s y cancelación en cascada** | `search_bookings` + `routes_trips` | Al confirmar una retención, verificar que otras solicitudes activas del pasajero en otros viajes pasen a `cancelada` y liberen cupo; si expira >60 s, debe rechazarse con `HOLD_EXPIRED`. |
| **Cancelación voluntaria sin consumir intento** | `search_bookings` | Cancelar en `pendiente` deja `intentos_consumidos = 0`; 3 rechazos bloquean el 4.º intento. |
| **Pago digital fallido → efectivo + comisiones** | `payments_wallets` | Simular fallo (`X-Mock-Payment-Result: failed`), reintentar o cambiar a efectivo, confirmar recepción y validar que `billetera` (`comisiones`) y `movimiento_billetera` cuadren sin doble abono. |
| **Idempotencia GPS / Chat / SOS** | `active_trip_gps`, `messaging_safety_support` | Enviar 2 veces el mismo `client_event_id` y confirmar que solo existe 1 fila en BD. |
| **Plazo 7 días calendario e inmutabilidad** | `messaging_safety_support` | Intentar editar/borrar `reporte` (debe fallar por trigger) e insertar `descargo` pasado el día 7 en `America/Lima` (debe fallar por `trg_validar_plazo_descargo`). |

---

## 5. Pruebas con teléfonos Android físicos y despliegue en la nube

### 5.1 Conexión desde `android/`
En `android/app/build.gradle.kts`:
- Definir `buildConfigField("String", "API_BASE_URL", ...)` y `WS_URL` por `buildType` / `flavor`:
  - `localEmulator`: `http://10.0.2.2:8080/api/v1/`
  - `localLan` (teléfonos físicos en mismo Wi-Fi): `http://<IP_LAN_PC>:8080/api/v1/`
  - `stagingCloud`: `https://<TU_APP_EN_LA_NUBE>/api/v1/` (con `wss://...` para WebSockets).

### 5.2 Checklist de despliegue en nube (a definir proveedor: Railway / Render / Fly.io / VPS)
1. Configurar **`Root Directory = backend`** en el servicio cloud del backend (para no redesplegar por cambios en `android/`).
2. Provisionar **PostgreSQL 16 con PostGIS habilitado** (`CREATE EXTENSION postgis; CREATE EXTENSION pgcrypto;`) y ejecutar `backend/db_rutaCompartida.sql` + `backend/seed_bd.sql` (y `backend/seed_dev.sql` solo si es ambiente de pruebas/staging).
2. Provisionar bucket S3 compatible (Cloudflare R2, AWS S3 o MinIO gestionado) con 1 bucket público y 1 privado.
3. Configurar variables de entorno de producción (`.env.example` como plantilla):
   - `APP_ENV=production`
   - Secretos JWT fuertes (`openssl rand -hex 32`)
   - `CORS_ALLOWED_ORIGINS` restringido
   - `ROUTING_PROVIDER=osrm_public` (para MVP; o instancia OSRM propia)
   - `CLOUD_PUBLIC_API_URL=https://...`
4. Verificar endpoint de salud `GET /healthz` (que compruebe `SELECT PostGIS_Version()` en PostgreSQL y conectividad con Storage) y probar flujo completo con **2 teléfonos físicos** (1 conductor + 1 pasajero) en datos reales antes de la entrega final.
