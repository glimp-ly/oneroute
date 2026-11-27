---
name: code-quality-review
description: >-
  Use this skill before committing or opening a pull request to audit OneRoute
  changes for module boundaries, security, localization, Android visual
  consistency, test coverage, and zero emojis.
---

# Pre-Commit and PR Code Quality Review (OneRoute)

Ejecuta esta auditoria sobre `git diff` antes de confirmar un commit o abrir un Pull Request hacia `develop`.

## Steps

1. **Limites de modulo y anti-conflictos:**
   - Confirma con `git status -s` y `git diff --name-only` que solo se modificaron archivos del modulo asignado (y que `internal/shared/*`, `backend/*.sql`, `docker-compose.yml` o modulos ajenos no fueron tocados sin aprobacion).
   - Verifica que no haya `INSERT`, `UPDATE` o `DELETE` sobre tablas de otros modulos fuera de `internal/shared/contracts`.

2. **Cero emojis y localizacion peruana:**
   - Confirma **0 emojis** en todos los archivos tocados (documentacion, Go, Kotlin, SQL, logs y comentarios).
   - Verifica moneda en `S/` y `PEN` y terminos locales (`DNI`, `CCI`, `Yape`, `Plin`, `comprobante electronico`, `America/Lima`); sin `$` de moneda, `MXN`, `INE`, `CLABE`, `SPEI` ni `CFDI`.

3. **Consistencia visual y recursos en Android (`android/`):**
   - Confirma que cualquier vista nueva/modificada usa `OneRouteTheme`, colores de `core/theme/Color.kt`, tipografia de `Type.kt`, `Scaffold(containerColor = CanvasBackground)` y componentes existentes en `core/components/` (con `Icons.Rounded.*` en lugar de emojis).
   - Verifica que ningun archivo en `res/drawable/` o `res/mipmap*/` tenga mayusculas.

4. **Seguridad y robustez en Go (`backend/`):**
   - Verifica propagacion de `ctx`, SQL 100% parametrizado (`$1, $2`), transacciones `db.WithTx` + `SELECT ... FOR UPDATE` en lecturas-escrituras criticas, `ON CONFLICT (client_event_id)` en ingesta offline y respuestas via `shared/httpx` (`WriteError`).

5. **Validacion final en verde:**
   - Ejecuta las pruebas y builds aplicables despues del ultimo cambio (`./gradlew testDebugUnitTest assembleDebug --no-daemon` en `android/` y/o `go build ./... && go test -race ./...` en `backend/`).
   - No reportes nada como verificado sin haber observado la salida exitosa completa.
