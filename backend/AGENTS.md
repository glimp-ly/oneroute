# Reglas para Agentes en `backend/` (Go + PostgreSQL 16 + PostGIS)

Consulta primero `AGENTS.md` en la raiz, `contexto_OR/01_ARQUITECTURA_Y_SHARED.md`, `contexto_OR/03_MODULOS_Y_DIVISION_EQUIPO.md` y `contexto_OR/04_CONTRATO_API_Y_TIEMPO_REAL.md`.

## 1. Regla obligatoria antes de avanzar

Antes de escribir o modificar codigo Go o SQL en `backend/`, **si no se indica explicitamente cual de los 6 modulos (o `internal/shared/`) esta desarrollando el integrante, pregunta primero antes de avanzar**.

## 2. Regla cero emojis

- **Nunca uses emojis** en codigo Go, comentarios, logs estructurados (`slog`), mensajes de error HTTP, scripts SQL ni archivos de prueba.

## 3. Propiedad de carpetas y tablas (regla anti-conflictos)

| Carpeta en `backend/internal/` | Responsable | Tablas propias de escritura |
|---|---|---|
| `shared/` | Lider tecnico | Configuracion/infraestructura, contratos y `auditoria` |
| `modules/auth_identity/` | Integrante 1 | `rol`, `usuario*`, `dispositivo`, `codigo_verificacion`, `verificacion_identidad`, `documento_privado`, `contacto_emergencia`, `perfil_conductor`, `vehiculo`, `solicitud_conductor`, `documento_conductor`, `consentimiento_usuario` (15) |
| `modules/routes_trips/` | Integrante 2 | `ruta`, `ruta_punto`, `viaje`, `viaje_parada`, `viaje_segmento` (5) |
| `modules/search_bookings/` | Integrante 3 | `solicitud_reserva`, `solicitud_intento`, `reserva`, `reserva_segmento`, `reserva_evento`, `reserva_modificacion` (6) |
| `modules/active_trip_gps/` | Integrante 4 | `viaje_ubicacion_gps`, `enlace_seguimiento`, `reserva_cancelacion`, `reserva_no_show`, `historial_no_show`, `calificacion` (6) |
| `modules/payments_wallets/` | Integrante 5 | `metodo_pago`, `pago`, `pago_intento`, `reembolso`, `billetera`, `movimiento_billetera`, `recarga_billetera`, `retiro`, `tarifa_comision`, `comision_viaje`, `promocion`, `promocion_aplicacion` (12) |
| `modules/messaging_safety_support/` | Integrante 6 | `categoria_*`, `alerta_sos*`, `incidente*`, `reporte*`, `investigacion`, `descargo*`, `restriccion_preventiva`, `sancion`, `apelacion_sancion`, `chat_reserva`, `mensaje_*`, `notificacion`, `preferencia_notificacion`, `ticket_*` (25) |

- **Regla estricta:** prohibido hacer `INSERT`, `UPDATE` o `DELETE` sobre tablas de otro modulo; invoca siempre las interfaces de `internal/shared/contracts`.
- `SELECT` de solo lectura sobre catalogos o vistas `vw_*` esta permitido.

## 4. Estandares Go obligatorios

- Estructura de 4 capas por modulo: `routes.go` -> `handler.go` -> `service.go` -> `repository.go` (+ `dto.go` y `*_test.go`).
- Propagar `ctx context.Context` en todas las funciones hasta `pgx`.
- Usar `db.WithTx` / `db.WithExistingOrNewTx` + `SELECT ... FOR UPDATE` en mutaciones de cupo, saldos, retenciones y liquidaciones.
- Usar siempre SQL parametrizado (`$1, $2, ...`); nunca concatenar strings en queries.
- Idempotencia offline con `ON CONFLICT (client_event_id) DO NOTHING` en GPS, chat, pagos, recargas, SOS e incidentes.
- Responder siempre con el envelope de `shared/httpx` (`{success, data, meta, error}`) y mapear errores SQL/triggers con `httpx.WriteError(w, err)` + `db.MapPgError`.

## 5. PostgreSQL 16 + PostGIS

- No editar `db_rutaCompartida.sql`, `seed_bd.sql`, `seed_dev.sql` ni `docker-compose.yml` sin coordinacion con el lider.
- En PostGIS (`SRID=4326`) el orden de coordenadas es siempre **longitud primero, latitud despues**: `POINT(lng lat)`.
- Respetar los triggers de negocio en BD (`trg_validar_viaje`, `trg_validar_capacidad_reserva`, inmutabilidad de `reporte`/`movimiento_billetera`, plazos de 7 dias calendario en `America/Lima`).

## 6. Pruebas y verificacion

- Aplica los skills `.agents/skills/tdd-bdd-workflow/SKILL.md` y `.agents/skills/build-tests/SKILL.md` para logica de negocio, concurrencia y contratos.
- Con codigo Go presente: `cd backend && go build ./... && go test -race ./...` (e integracion contra Docker con `go test -tags=integration -race ./...`).
- Para validar Docker y SQL, usa `.agents/skills/verify-local-env/SKILL.md`.
