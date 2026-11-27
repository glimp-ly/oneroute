# Arquitectura Backend Go, `shared/` y Regla Anti-Conflictos

---

## 1. Estructura de carpetas del Monorepo y Backend (Go)

```text
oneroute/
├── android/                         # Proyecto Android Studio (abrir esta subcarpeta en Android Studio)
├── contexto_OR/                     # Documentacion tecnica compartida (este directorio)
├── README.md
└── backend/                         # Root Directory para despliegue del servidor Go
    ├── cmd/
    │   └── api/
    │       └── main.go              # Wiring: carga config, conecta DB/MinIO/OSRM, inicia Hub/Scheduler y monta los 6 módulos
    ├── internal/
    │   ├── shared/                  # <-- PROPIEDAD DEL LÍDER (solo PRs dedicados)
    │   │   ├── config/              # Carga y validación de .env
    │   │   ├── db/                  # pgxpool, WithTx, mapeo de errores SQLSTATE/triggers
    │   │   ├── httpx/               # Envelope JSON {data, meta, error}, middlewares, validator
    │   │   ├── auth/                # JWT access/refresh, bcrypt, Claims, middlewares de rol/verificación
    │   │   ├── geo/                 # Helpers PostGIS + RoutingProvider (OSRM / Mock)
    │   │   ├── storage/             # StorageProvider (MinIO / S3) + presigned URLs
    │   │   ├── realtime/            # WebSocket Hub (salas user/trip/reservation) + PushProvider
    │   │   ├── scheduler/           # Workers periódicos (60s holds, 24h créditos, 24h ratings, GPS 30d)
    │   │   ├── providers/           # Interfaces + mocks de PaymentGateway, IdentityProvider, OTPProvider
    │   │   └── contracts/           # Interfaces inter-módulo (WalletPort, BookingPort, TripPort, etc.)
    │   └── modules/                 # <-- 1 CARPETA POR INTEGRANTE (trabajo en paralelo sin colisiones)
    │       ├── auth_identity/       # handler.go, service.go, repository.go, dto.go, routes.go
    │       ├── routes_trips/
    │       ├── search_bookings/
    │       ├── active_trip_gps/
    │       ├── payments_wallets/
    │       └── messaging_safety_support/
    ├── db_rutaCompartida.sql
    ├── seed_bd.sql
    ├── seed_dev.sql
    ├── docker-compose.yml
    ├── .env.example
    ├── go.mod
    └── Makefile
```

Cada módulo sigue internamente el mismo patrón de 4 capas:
`routes.go` → `handler.go` (HTTP/JSON + DTOs) → `service.go` (reglas de negocio + `db.WithTx` + llamadas a `contracts`) → `repository.go` (SQL parametrizado con `pgx`).

---

## 2. Regla anti-conflictos y Matriz de Propiedad de Tablas

> **REGLA ESTRICTA:** Ningún módulo puede ejecutar `INSERT`, `UPDATE` o `DELETE` sobre una tabla que pertenece a otro módulo. Si necesita mutar datos ajenos (por ejemplo, acreditar saldo en `billetera`, marcar una `reserva` como `cancelada` o crear una `notificacion`), **debe invocar el puerto correspondiente en `internal/shared/contracts`**.
>
> Las consultas `SELECT` de solo lectura sobre tablas de catálogos (`moneda`, `pais`, `configuracion`, `metodo_pago`, etc.) o vistas (`vw_*`) están permitidas para evitar sobrecarga de llamadas.

### Asignación de las 73 tablas de `db_rutaCompartida.sql`

| Módulo propietario (único con escritura) | Tablas asignadas | Total |
|---|---|---|
| **Transversal (`shared` / Admin Seed)** | `moneda`, `pais`, `configuracion`, `auditoria` | 4 |
| **`auth_identity`** | `rol`, `usuario`, `usuario_rol`, `dispositivo`, `usuario_sesion`, `codigo_verificacion`, `usuario_direccion`, `verificacion_identidad`, `documento_privado`, `contacto_emergencia`, `perfil_conductor`, `vehiculo`, `solicitud_conductor`, `documento_conductor`, `consentimiento_usuario` | 15 |
| **`routes_trips`** | `ruta`, `ruta_punto`, `viaje`, `viaje_parada`, `viaje_segmento` | 5 |
| **`search_bookings`** | `solicitud_reserva`, `solicitud_intento`, `reserva`, `reserva_segmento`, `reserva_evento`, `reserva_modificacion` | 6 |
| **`active_trip_gps`** | `viaje_ubicacion_gps`, `enlace_seguimiento`, `reserva_cancelacion`, `reserva_no_show`, `historial_no_show`, `calificacion` | 6 |
| **`payments_wallets`** | `metodo_pago`, `pago`, `pago_intento`, `reembolso`, `billetera`, `movimiento_billetera`, `recarga_billetera`, `retiro`, `tarifa_comision`, `comision_viaje`, `promocion`, `promocion_aplicacion` | 12 |
| **`messaging_safety_support`** | `categoria_incidente`, `categoria_reporte`, `categoria_notificacion`, `alerta_sos`, `alerta_sos_destinatario`, `incidente`, `incidente_evidencia`, `reporte`, `reporte_categoria`, `reporte_comunicacion`, `reporte_evidencia`, `investigacion`, `descargo`, `descargo_evidencia`, `restriccion_preventiva`, `sancion`, `apelacion_sancion`, `chat_reserva`, `mensaje_chat`, `mensaje_reporte`, `notificacion`, `preferencia_notificacion`, `ticket_soporte`, `ticket_mensaje`, `ticket_adjunto` | 25 |
| **TOTAL** | | **73** |

---

## 3. Especificación técnica de `internal/shared/`

El líder implementa y mantiene `internal/shared/`. Todo módulo recibe sus dependencias por inyección en `cmd/api/main.go`.

### 3.1 `shared/config`
- Carga `.env` (con `godotenv` en dev) y valida variables obligatorias al iniciar.
- Expone `type Config struct` con sub-structs: `App`, `DB`, `JWT`, `Storage`, `Routing`, `Providers`, `Scheduler`.

### 3.2 `shared/db`
- Pool con `pgxpool.NewWithConfig` (`MaxConns: 25`, `MinConns: 5`, `MaxConnLifetime: 30m`).
- Al abrir conexión ejecuta `SET TIME ZONE 'America/Lima'` en `AfterConnect` para consistencia de sesión.
- **Helper transaccional obligatorio:**
  ```go
  func WithTx(ctx context.Context, pool *pgxpool.Pool, fn func(tx pgx.Tx) error) error
  ```
  - Ejecuta `pgx.TxOptions{IsoLevel: pgx.ReadCommitted}`.
  - Si un módulo recibe un `pgx.Tx` desde un puerto inter-módulo (para operaciones atómicas como confirmar reserva + crear chat, o finalizar viaje + cobrar + liquidar comisión), usar `WithExistingOrNewTx(ctx, tx, pool, fn)`.
- **Mapeo centralizado de errores PostgreSQL (`MapPgError(err)`):**
  - `23505` (`unique_violation`) → `409 Conflict` (ej. `uq_conductor_viaje_activo`, `uq_pasajero_reserva_activa`, `uq_identidad_documento_aprobado`, `client_event_id`).
  - `23503` (`foreign_key_violation`) → `400 Bad Request` / `404 Not Found`.
  - `23514` (`check_violation`) → `422 Unprocessable Entity`.
  - `P0001` (`raise_exception` de triggers como `fn_validar_capacidad_reserva`, `fn_validar_plazo_apelacion`, `fn_proteger_reporte_original`) → `409` o `422` conservando el mensaje claro del trigger.

### 3.3 `shared/httpx`
- **Envelope JSON único en toda la API:**
  ```json
  {
    "success": true,
    "data": {},
    "meta": { "page": 1, "limit": 20, "total": 45 },
    "error": null
  }
  ```
  En error:
  ```json
  {
    "success": false,
    "data": null,
    "meta": null,
    "error": {
      "code": "HOLD_EXPIRED",
      "message": "El tiempo de retención de 60 segundos ha expirado.",
      "details": {}
    }
  }
  ```
- **Middlewares incluidos:** `RequestID`, `StructuredLogger` (`slog`), `Recoverer`, `CORS`, `Timeout(15s)`, `RateLimiter` (por IP en auth/OTP y por usuario en endpoints sensibles).

### 3.4 `shared/auth`
- **Access Token (JWT HS256, TTL 15 min):**
  Claims:
  ```go
  type Claims struct {
      UserID           uuid.UUID `json:"sub"`
      SessionID        uuid.UUID `json:"sid"`
      Roles            []string  `json:"roles"`
      IdentityVerified bool      `json:"id_ver"`
      DriverApproved   bool      `json:"drv_ok"`
      jwt.RegisteredClaims
  }
  ```
- **Refresh Token (opaco criptográfico 32 bytes hex, TTL 30 días):** se almacena **solo su hash SHA-256** en `usuario_sesion.refresh_token_hash` y soporta rotación y revocación por dispositivo.
- **Hashing de contraseñas y OTPs:** `golang.org/x/crypto/bcrypt` (costo 10, compatible con `crypt(..., gen_salt('bf', 10))` usado en `seed_dev.sql`).
- **Middlewares listos para usar en `routes.go`:**
  - `RequireAuth`: valida Bearer JWT y verifica que `usuario.estado = 'activo'` y sesión no revocada.
  - `RequireRoles("soporte", "moderador", "admin")`: valida roles desde `usuario_rol`.
  - `RequireVerifiedIdentity`: exige `identidad_verificada = true` en BD (no solo en el claim cacheado) antes de solicitar/confirmar reserva.
  - `RequireApprovedDriver`: exige `perfil_conductor.estado = 'aprobado'`.
  - `CheckActiveRestrictions(alcance)`: consulta `restriccion_preventiva` (`activa = TRUE` y dentro de vigencia) para bloquear operaciones de `pasajero`, `conductor` o `total`.

### 3.5 `shared/geo`
- Structs estándar: `Point { Lat float64; Lng float64 }`, `RouteGeometry { LineStringWKT string; GeoJSON any; DistanceKm float64; DurationMin int }`.
- **Funciones PostGIS reutilizables** (queries o helpers SQL):
  - `PointToEWKT(p Point) string` → `'SRID=4326;POINT(lng lat)'` *(cuidado: PostGIS siempre usa orden **longitud primero, latitud después**).*
  - `ProjectPointOnRoute(ctx, tx, routeGeoEWKT, point)`: usa `ST_LineLocatePoint(geometria::geometry, punto::geometry)` para devolver `fraccion_ruta` (`0.0..1.0`) y `desvio_km` con `ST_Distance(geometria, punto) / 1000.0`.
  - `SegmentDistanceKm(ctx, tx, routeGeoEWKT, fStart, fEnd)`: usa `ST_Length(ST_LineSubstring(geometria::geometry, fStart, fEnd)::geography) / 1000.0`.
  - `ValidateOrderAhead(vehicleFrac, pickupFrac, dropoffFrac)`: garantiza `pickupFrac >= vehicleFrac` y `dropoffFrac > pickupFrac`.
- **Interfaz `RoutingProvider`:** implementada con OSRM (`osrm_public` / `osrm_local`) y fallback `mock` (ver `05_INTEGRACIONES_MOCK_Y_PRODUCCION.md`).

### 3.6 `shared/storage`
- Interfaz:
  ```go
  type StorageProvider interface {
      UploadPublic(ctx context.Context, key string, r io.Reader, size int64, contentType string) (publicURL string, err error)
      UploadPrivate(ctx context.Context, key string, r io.Reader, size int64, contentType string) (objectKey string, err error)
      PresignedGetURL(ctx context.Context, key string, ttl time.Duration) (string, error)
      Delete(ctx context.Context, bucket, key string) error
  }
  ```
- Reglas:
  - **Bucket público (`rutacompartida-public`):** fotos de perfil y fotos de vehículo.
  - **Bucket privado (`rutacompartida-private`):** `documento_privado` (DNI), `documento_conductor` (licencia, SOAT, tarjeta de propiedad), evidencias de `incidente`, `reporte`, `descargo`, adjuntos de `ticket_soporte` y multimedia de `mensaje_chat`. Nunca exponer URL permanente; generar URL firmada con TTL de 5 min tras validar permisos.

### 3.7 `shared/realtime`
- **WebSocket Hub** (`/api/v1/ws`): autenticado por query `?token=<jwt>` o header `Authorization`.
- Salas soportadas:
  - `user:{usuario_id}` (notificaciones en vivo, solicitudes 60 s, hold 60 s, cambios de estado).
  - `trip:{viaje_id}` (tracking GPS en vivo del conductor y paradas).
  - `reservation:{reserva_id}` (chat privado en tiempo real).
- Método expuesto: `Publish(room string, event string, payload any)`.
- Interfaz `PushProvider` para FCM (mock en dev).

### 3.8 `shared/scheduler`
Workers en goroutines independientes con `SELECT ... FOR UPDATE SKIP LOCKED`:
1. **Cada 5 s:** expira `solicitud_reserva` y `solicitud_intento` vencidas (`estado = 'pendiente' AND expira_at <= NOW()`), incrementa `intentos_consumidos`, y expira `reserva` en `'esperando_pasajero'` (`hold_expira_at <= NOW()`), emitiendo evento WS.
2. **Cada 60 s:** libera `reembolso` en estado `'programado'` donde `disponible_at <= NOW()` acreditando vía `WalletPort.CreditPassengerBalance(...)` (retención de 24 h por reducción de asientos).
3. **Cada 60 s:** publica `calificacion` donde `publicada = FALSE AND publicar_at <= NOW()` y recalcula promedio en `usuario` / `perfil_conductor`.
4. **Cada 60 s:** desactiva `enlace_seguimiento` expirados (`activo = TRUE AND expira_at <= NOW()`).
5. **Cada 60 min:** purga `viaje_ubicacion_gps` con antigüedad mayor a `gps.retencion_dias` (30 días) de viajes ya `completado`/`cancelado`.

### 3.9 `shared/contracts` (puertos entre los 6 módulos)
```go
// Implementado por payments_wallets
type WalletPort interface {
    EnsureUserWallets(ctx context.Context, tx pgx.Tx, userID uuid.UUID) error
    HasSufficientCommissionBalance(ctx context.Context, userID uuid.UUID) (bool, error)
    CreditPassengerRefund(ctx context.Context, tx pgx.Tx, userID uuid.UUID, refType string, refID uuid.UUID, amount float64, desc string) error
    ScheduleDelayedCredit(ctx context.Context, tx pgx.Tx, reservaID, modID, pagoID, userID uuid.UUID, amount float64, availableAt time.Time, reason string) error
    SettleRidePayment(ctx context.Context, tx pgx.Tx, cmd SettlePaymentCmd) (*SettlePaymentResult, error)
    ProcessTipPayment(ctx context.Context, tx pgx.Tx, cmd TipCmd) (pagoID uuid.UUID, err error)
}

// Implementado por routes_trips
type TripPort interface {
    GetTripForBooking(ctx context.Context, viajeID uuid.UUID) (*TripSnapshot, error)
    UpdateOperationalRoute(ctx context.Context, tx pgx.Tx, viajeID uuid.UUID, newGeoEWKT string, newDistKm float64) error
    AddOperationalStop(ctx context.Context, tx pgx.Tx, stop AddStopCmd) (uuid.UUID, error)
    CancelAllTripBookingsTrigger(ctx context.Context, tx pgx.Tx, viajeID, actorID uuid.UUID, reason string) error
}

// Implementado por search_bookings
type BookingPort interface {
    CancelReservationsByTrip(ctx context.Context, tx pgx.Tx, viajeID, actorID uuid.UUID, reason string) ([]CancelledBookingInfo, error)
    CompleteEarlyByPassenger(ctx context.Context, tx pgx.Tx, reservaID, userID uuid.UUID, dropoff geo.Point, newFrac, newDistKm, newPrice float64) error
    MarkReservationNoShow(ctx context.Context, tx pgx.Tx, reservaID, driverID uuid.UUID) error
    MarkReservationBoarded(ctx context.Context, tx pgx.Tx, reservaID, driverID uuid.UUID) error
    MarkReservationCompleted(ctx context.Context, tx pgx.Tx, reservaID uuid.UUID) error
}

// Implementado por messaging_safety_support
type NotificationPort interface {
    Notify(ctx context.Context, cmd NotifyCmd) error
    CreateReservationChat(ctx context.Context, tx pgx.Tx, reservaID uuid.UUID) error
    CloseReservationChat(ctx context.Context, tx pgx.Tx, reservaID uuid.UUID) error
}

// Implementado por auth_identity
type IdentityPort interface {
    GetUserSummary(ctx context.Context, userID uuid.UUID) (*UserSummary, error)
    GetEmergencyContacts(ctx context.Context, userID uuid.UUID) ([]EmergencyContactDTO, error)
    RecalculateRatingStats(ctx context.Context, tx pgx.Tx, userID uuid.UUID, rolEvaluado string) error
}
```
