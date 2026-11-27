---
name: tdd-bdd-workflow
description: >-
  Use this skill when implementing new business logic, endpoints, state
  transitions, or bug fixes in Go or Kotlin using Behavior-Driven Development
  (Given-When-Then) and Test-Driven Development (Red-Green-Refactor).
---

# TDD + BDD Development Workflow (OneRoute)

Aplica este flujo antes de escribir logica de produccion en `backend/` o `android/` para garantizar que el codigo cumpla exactamente las reglas de negocio de `contexto_OR/`.

## Steps

1. **Confirmar modulo y alcance:**
   - Verifica que sabes que modulo (`auth_identity`, `routes_trips`, `search_bookings`, `active_trip_gps`, `payments_wallets`, `messaging_safety_support` o `shared/`) desarrolla el integrante. Si no esta indicado, pregunta primero.
   - Consulta las reglas del caso en [00_README_CONTEXTO.md](../../../contexto_OR/00_README_CONTEXTO.md), [03_MODULOS_Y_DIVISION_EQUIPO.md](../../../contexto_OR/03_MODULOS_Y_DIVISION_EQUIPO.md) y [04_CONTRATO_API_Y_TIEMPO_REAL.md](../../../contexto_OR/04_CONTRATO_API_Y_TIEMPO_REAL.md).

2. **Fase BDD (Especificar comportamiento antes de codificar):**
   - Redacta de 2 a 5 escenarios concretos en formato **Dado / Cuando / Entonces** (`Given / When / Then`) cubriendo:
     - Camino feliz (estado inicial, transicion y respuesta esperada).
     - Reglas limite del dominio (hold 60 s, maximo 3 intentos, 1 cambio recogida/destino, retencion 24 h, plazo 7 dias en `America/Lima`).
     - Fallos esperados y codigos de error de negocio (`INSUFFICIENT_CAPACITY`, `HOLD_EXPIRED`, `MAX_ATTEMPTS_REACHED`, `409 Conflict`, etc.).
     - Concurrencia o idempotencia (`client_event_id`) cuando aplique.

3. **Fase TDD Rojo (Escribir pruebas que fallen primero):**
   - Traduce cada escenario BDD a pruebas reales sin usar emojis:
     - **Go:** tests table-driven (`t.Run`) en `backend/internal/modules/<modulo>/*_test.go`.
     - **Android:** tests JUnit4 en `android/app/src/test/java/com/oneroute/`.
   - Ejecuta la suite para confirmar que la prueba nueva falla por falta de implementacion (y no por error de sintaxis o entorno).

4. **Fase TDD Verde (Implementacion minima correcta):**
   - Implementa la logica minima dentro de las capas permitidas (`dto.go`, `repository.go`, `service.go`, `handler.go`, `routes.go` en Go; `AppState.kt` / `feature/<tu_feature>/` en Android) respetando puertos `shared/contracts` y estilo visual existente en Android.
   - Ejecuta las pruebas hasta obtener 100% en verde.

5. **Refactorizar y validar regresiones:**
   - Elimina duplicacion y verifica nombres claros sin alterar comportamiento ni romper contratos.
   - Ejecuta la verificacion completa del modulo antes de terminar:
     - Backend Go: `cd backend && go test -race ./...`
     - Android: `cd android && ./gradlew testDebugUnitTest assembleDebug --no-daemon`
