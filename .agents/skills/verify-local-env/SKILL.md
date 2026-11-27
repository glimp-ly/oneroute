---
name: verify-local-env
description: >-
  Use this skill when verifying that Docker services, PostgreSQL schema and
  seeds, Mailpit, MinIO, Android Gradle build, or Go backend build work properly.
---

# Verify Local Development Environment (OneRoute)

Ejecuta esta lista de comprobacion para validar que el entorno local este operativo antes de desarrollar o abrir un PR.

## Steps

1. **Verificar servicios Docker (`backend/`):**
   ```bash
   cd backend && docker compose ps
   ```
   Asegura que `rutacompartida_postgres`, `rutacompartida_minio` y `rutacompartida_mailpit` esten en estado `Up` / `healthy`.

2. **Verificar esquema, objetos e idempotencia SQL en PostgreSQL 16 + PostGIS:**
   ```bash
   docker exec rutacompartida_postgres psql -U rutacompartida -d rutacompartida -v ON_ERROR_STOP=1 -c "
   SELECT extname, extversion FROM pg_extension ORDER BY extname;
   SELECT table_type, count(*) FROM information_schema.tables WHERE table_schema='public' GROUP BY table_type ORDER BY table_type;
   SELECT conname FROM pg_constraint WHERE connamespace='public'::regnamespace AND NOT convalidated;
   SELECT indexrelid::regclass FROM pg_index WHERE NOT indisvalid OR NOT indisready;
   "
   ```
   Y valida los scripts de seed sin mutar la BD dentro de una transaccion con `ROLLBACK`:
   ```bash
   (
     echo "BEGIN;"
     sed -E '/^(BEGIN|COMMIT);$/d' backend/seed_bd.sql
     sed -E '/^(BEGIN|COMMIT);$/d' backend/seed_dev.sql
     echo "ROLLBACK;"
   ) | docker exec -i rutacompartida_postgres psql -U rutacompartida -d rutacompartida -v ON_ERROR_STOP=1 -q
   ```

3. **Verificar Mailpit y MinIO:**
   - Mailpit Web UI / API: `curl --noproxy '*' -sf http://127.0.0.1:8025/api/v1/info`
   - MinIO Health: `curl --noproxy '*' -sf http://127.0.0.1:9000/minio/health/live`

4. **Verificar Android (`android/`):**
   ```bash
   cd android && ./gradlew testDebugUnitTest assembleDebug --no-daemon
   ```

5. **Verificar Backend Go (cuando exista `backend/go.mod`):**
   ```bash
   cd backend && go build ./... && go test ./...
   ```
