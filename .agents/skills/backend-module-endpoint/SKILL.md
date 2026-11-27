---
name: backend-module-endpoint
description: >-
  Use this skill when implementing, modifying, or reviewing a Go HTTP endpoint,
  service, or repository inside one of the six backend modules in OneRoute.
---

# Backend Module Endpoint Workflow (OneRoute)

Sigue este procedimiento para implementar o modificar un endpoint dentro de `backend/internal/modules/<modulo>/`.

## Steps

1. **Confirmar el modulo asignado:**
   - Verifica que el integrante haya indicado cual de los 6 modulos (`auth_identity`, `routes_trips`, `search_bookings`, `active_trip_gps`, `payments_wallets`, `messaging_safety_support`) o `shared/` esta desarrollando.
   - Si no esta claro, **detente y pregunta** antes de escribir codigo.

2. **Revisar especificaciones en `contexto_OR/`:**
   - Alcance del modulo: [03_MODULOS_Y_DIVISION_EQUIPO.md](../../../contexto_OR/03_MODULOS_Y_DIVISION_EQUIPO.md)
   - Endpoint, payload, codigos de error y eventos WS: [04_CONTRATO_API_Y_TIEMPO_REAL.md](../../../contexto_OR/04_CONTRATO_API_Y_TIEMPO_REAL.md)
   - Matriz de propiedad de tablas y contratos: [01_ARQUITECTURA_Y_SHARED.md](../../../contexto_OR/01_ARQUITECTURA_Y_SHARED.md)

3. **Validar limites entre modulos:**
   - Confirma que toda operacion `INSERT`, `UPDATE` o `DELETE` en `repository.go` afecte unicamente tablas propias del modulo.
   - Si necesitas mutar datos de otro modulo (por ejemplo `billetera`, `reserva` o `notificacion`), usa el puerto correspondiente de `internal/shared/contracts`.

4. **Implementar respetando las 4 capas del modulo:**
   - `dto.go`: structs de request/response con etiquetas JSON alineadas a `04_CONTRATO_API_Y_TIEMPO_REAL.md` y validaciones de entrada.
   - `repository.go`: SQL parametrizado (`$1, $2, ...`) con `pgx`; usar `SELECT ... FOR UPDATE` para contencion de cupos o saldos y `ON CONFLICT (client_event_id) DO NOTHING` en eventos offline.
   - `service.go`: reglas de negocio dentro de `db.WithTx` (o `db.WithExistingOrNewTx` si recibe `pgx.Tx` por contrato), invocando puertos inter-modulo y `realtime.Publish` tras confirmar la transaccion o segun contrato.
   - `handler.go`: parsear JSON/params, propagar `r.Context()`, responder con el envelope de `shared/httpx` y delegar errores a `httpx.WriteError(w, err)`.
   - `routes.go`: montar rutas en el router `chi` aplicando los middlewares de `shared/auth` (`RequireAuth`, `RequireVerifiedIdentity`, `RequireApprovedDriver`, `RequireRoles`, `CheckActiveRestrictions`).

5. **Agregar pruebas y verificar:**
   - Crea o actualiza `*_test.go` dentro del mismo modulo (incluyendo pruebas de concurrencia o idempotencia cuando aplique).
   - Ejecuta desde `backend/`:
     ```bash
     go build ./... && go test ./...
     ```
   - Si hay pruebas contra Docker Postgres: `go test -tags=integration ./...`.
