---
name: build-tests
description: >-
  Use this skill when writing, expanding, or running unit, integration,
  concurrency, or Android JUnit tests in OneRoute.
---

# Build and Run Tests Guide (OneRoute)

Usa este procedimiento para construir pruebas solidas (unitarias, integracion y concurrencia) alineadas a [06_GUIA_AGENTES_Y_PRUEBAS.md](../../../contexto_OR/06_GUIA_AGENTES_Y_PRUEBAS.md).

## Steps

1. **Confirmar el modulo y capa a probar:**
   - Si no conoces el modulo asignado al integrante, pregunta antes de crear archivos de prueba.
   - Nunca uses emojis en nombres de test, mensajes de asercion ni fixtures.

2. **Pruebas unitarias en Go (`backend/internal/modules/<modulo>/*_test.go`):**
   - Usa pruebas dirigidas por tabla (`[]struct{ name string; ... }` con `t.Run`).
   - Simula puertos externos de `internal/shared/contracts` mediante stubs/mocks en memoria en el paquete de test; evita llamadas de red reales.
   - Cubre validaciones de DTO, calculos en `PEN`, transiciones de estado y mapeo de errores.

3. **Pruebas de integracion y concurrencia en Go (`//go:build integration`):**
   - Ejecutalas contra PostgreSQL + PostGIS del Docker local (`rutacompartida_postgres`) con fixtures aislados (UUIDs unicos por test o transaccion revertida con `ROLLBACK`).
   - Casos obligatorios segun modulo (ver [06_GUIA_AGENTES_Y_PRUEBAS.md](../../../contexto_OR/06_GUIA_AGENTES_Y_PRUEBAS.md)):
     - **Carrera de ultimo asiento (`search_bookings`):** 2 goroutines con `sync.WaitGroup` reservando tramos solapados; exactamente 1 triunfa y 1 falla por `trg_validar_capacidad_reserva` (`409 INSUFFICIENT_CAPACITY`).
     - **Hold 60 s y cascada (`search_bookings` + `routes_trips`):** confirmar una retencion cancela solicitudes activas en otros viajes; expirar >60 s devuelve `HOLD_EXPIRED`.
     - **Limite 3 intentos (`search_bookings`):** cancelacion voluntaria en `pendiente` no suma intento; 3 rechazos bloquean el 4. intento.
     - **Fallo digital -> efectivo (`payments_wallets`):** verificar recálculo de `comision_viaje` y libro mayor `movimiento_billetera` sin doble abono.
     - **Idempotencia offline (`active_trip_gps`, `messaging_safety_support`):** reenviar el mismo `client_event_id` mantiene exactamente 1 fila en BD.
     - **Inmutabilidad y plazo 7 dias (`messaging_safety_support`):** editar/borrar `reporte` falla por trigger; `descargo` pasado el dia 7 en `America/Lima` falla por `trg_validar_plazo_descargo`.

4. **Pruebas JUnit en Android (`android/app/src/test/java/com/oneroute/`):**
   - Agrega casos en [AppStateTest.kt](../../../android/app/src/test/java/com/oneroute/AppStateTest.kt) (o archivos `*Test.kt` dedicados) verificando reglas de negocio, formateo en `S/`/`PEN` y pila de `AppNavigator`.

5. **Ejecutar y validar sin cache enganosa:**
   - Backend Go unitarias:
     ```bash
     cd backend && go test -v -race ./...
     ```
   - Backend Go integracion (con Docker arriba):
     ```bash
     cd backend && go test -tags=integration -v -race ./...
     ```
   - Android unitarias + compilacion debug:
     ```bash
     cd android && ./gradlew testDebugUnitTest assembleDebug --no-daemon
     ```
