# OneRoute / RutaCompartida - Reglas Globales para Agentes

Este monorepo contiene la aplicacion movil Android (`android/`), la base de datos y backend modular en Go (`backend/`) y la documentacion tecnica (`contexto_OR/`).

---

## 1. Regla obligatoria antes de avanzar (Identificacion del modulo)

Antes de crear archivos, editar codigo o proponer una implementacion concreta, verifica si conoces con certeza que modulo esta desarrollando el integrante:

- `backend/internal/shared/` (Core compartido - Lider tecnico)
- `auth_identity` (Modulo 1)
- `routes_trips` (Modulo 2)
- `search_bookings` (Modulo 3)
- `active_trip_gps` (Modulo 4)
- `payments_wallets` (Modulo 5)
- `messaging_safety_support` (Modulo 6)
- Pantalla/feature especifica de `android/` asignada a uno de los 6 modulos

**Si el integrante no indico explicitamente su modulo asignado y no se deduce sin ambiguedad de la solicitud o de la rama Git actual, DETENTE y PREGUNTA primero antes de avanzar en el desarrollo.**

---

## 2. Regla cero emojis en todo el repositorio

- **Nunca uses emojis en ningun archivo del repositorio** (ni en `README.md`, `contexto_OR/`, `AGENTS.md`, `.agents/`, codigo Go/Kotlin, comentarios, logs, mensajes de error, tests ni textos nuevos de UI).
- En Android Compose usa siempre iconografia Vector (`Icons.Rounded.*` o `Icons.AutoMirrored.Rounded.*`).

---

## 3. Fuente unica de verdad (`contexto_OR/`)

No dupliques ni inventes reglas fuera de estos documentos:

| Documento | Proposito |
|---|---|
| `contexto_OR/00_README_CONTEXTO.md` | Stack definitivo, reglas de negocio e integracion del equipo |
| `contexto_OR/01_ARQUITECTURA_Y_SHARED.md` | Arquitectura Go modular, regla anti-conflictos, 73 tablas y `internal/shared/` |
| `contexto_OR/02_DOCKER_BD_Y_ENTORNO.md` | Docker Compose, PostgreSQL/PostGIS, MinIO, Mailpit, OSRM y seeds |
| `contexto_OR/03_MODULOS_Y_DIVISION_EQUIPO.md` | Alcance exacto de los 6 modulos, tablas, puertos y pantallas Android |
| `contexto_OR/04_CONTRATO_API_Y_TIEMPO_REAL.md` | Contrato REST (`/api/v1`), codigos de error y WebSocket (`/api/v1/ws`) |
| `contexto_OR/05_INTEGRACIONES_MOCK_Y_PRODUCCION.md` | OSRM, pagos mock, DNI/OTP mock y reemplazo por produccion |
| `contexto_OR/06_GUIA_AGENTES_Y_PRUEBAS.md` | Flujo Git en paralelo, estandares Go, concurrencia y pruebas |

---

## 4. Reglas anti-conflictos y propiedad de archivos

- **Prohibido escribir en tablas de otro modulo:** ningun modulo puede hacer `INSERT`, `UPDATE` o `DELETE` sobre tablas ajenas; debe invocar su puerto en `backend/internal/shared/contracts`.
- **Edicion libre (segun modulo confirmado):** `backend/internal/modules/<tu_modulo>/` y `android/app/src/main/java/com/oneroute/feature/<tu_feature>/`.
- **Edicion coordinada con el equipo/lider:** `backend/cmd/api/main.go`, `android/.../core/navigation/Route.kt`, `AppNavHost.kt` y `AppState.kt`.
- **Prohibido modificar sin PR aprobado por el lider:** `backend/internal/shared/*`, `backend/*.sql`, `backend/docker-compose.yml`, `backend/.env.example` y modulos de otros integrantes.

---

## 5. Localizacion (Peru), UI Android y flujo de desarrollo

- **Moneda oficial:** siempre Soles peruanos (`S/` y `PEN`). Prohibido usar `$` como moneda, `MXN`, `pesos`, `CLABE`, `SPEI`, `CFDI` o `INE`.
- **Terminos locales:** `DNI`, `CCI`, `Yape`, `Plin`, comprobante electronico y zona horaria `America/Lima`.
- **Consistencia visual en Android:** toda pantalla o componente nuevo en `android/` debe respetar el estilo, paleta (`core/theme/Color.kt`), tipografia (`Type.kt`), tema (`OneRouteTheme`) y componentes existentes en `core/components/` (ver `android/AGENTS.md`).
- **Dominio:** tarifa definida por el conductor (referencia `S/ 2.00/km`), reserva en 2 etapas con hold de 60 s y maximo 3 intentos, triple billetera (`pasajero`, `ganancias`, `comisiones`), inmutabilidad en `reporte`, `reporte_evidencia` y `movimiento_billetera`.
- **Flujo Git:** ramas principales `main` y `develop`; trabajo diario en `feat/<modulo>-<subtarea>` con `git fetch origin && git rebase origin/develop`.

---

## 6. Skills disponibles en `.agents/skills/`

- `tdd-bdd-workflow`: desarrollar funcionalidades y correcciones con escenarios BDD (Dado-Cuando-Entonces) y ciclo TDD (Rojo-Verde-Refactorizar).
- `build-tests`: construir y ejecutar pruebas unitarias, de integracion, concurrencia en Go y JUnit en Android.
- `backend-module-endpoint`: crear/actualizar endpoints Go por modulo respetando las 4 capas y contratos.
- `postgis-query`: escribir y validar consultas PostGIS (`SRID=4326`, orden `lng lat`, indices GIST).
- `android-screen`: crear/actualizar pantallas Compose manteniendo el sistema visual actual, rutas y estado en `android/`.
- `code-quality-review`: auditar limites de modulo, seguridad, cero emojis, localizacion PEN, estilo visual y pruebas antes de commit/PR.
- `verify-local-env`: verificar Docker Compose, esquema/seeds PostgreSQL, Mailpit, MinIO y build Android/Go.
