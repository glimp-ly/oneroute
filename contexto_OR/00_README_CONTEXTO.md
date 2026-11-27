# RutaCompartida / OneRoute — Contexto Técnico Maestro

Este directorio (`contexto_OR/`) es la **fuente única de verdad** del monorepo `oneroute/` para el equipo de **6 integrantes** y para cualquier agente de IA que participe en el desarrollo del backend en **Go** (`backend/`), la base de datos **PostgreSQL 16 + PostGIS** (`backend/*.sql`) y la aplicación móvil en **Kotlin (`android/`)**.

> **Importante:** Antes de escribir código o abrir una rama, todo integrante o agente debe leer este documento, `01_ARQUITECTURA_Y_SHARED.md`, `03_MODULOS_Y_DIVISION_EQUIPO.md` (en especial su módulo asignado) y `06_GUIA_AGENTES_Y_PRUEBAS.md`.

---

## 1. Índice de documentación

| Documento | Contenido |
|---|---|
| [00_README_CONTEXTO.md](./00_README_CONTEXTO.md) | Visión general, stack definitivo, reglas de negocio y mapa del equipo. |
| [01_ARQUITECTURA_Y_SHARED.md](./01_ARQUITECTURA_Y_SHARED.md) | Estructura Go modular, regla anti-conflictos, matriz de propiedad de las 73 tablas y especificación completa de `internal/shared/`. |
| [02_DOCKER_BD_Y_ENTORNO.md](./02_DOCKER_BD_Y_ENTORNO.md) | Documentación técnica de `docker-compose.yml`, PostGIS, Mailpit, MinIO, OSRM, `seed_bd.sql`, `seed_dev.sql` y `.env.example`. |
| [03_MODULOS_Y_DIVISION_EQUIPO.md](./03_MODULOS_Y_DIVISION_EQUIPO.md) | División exacta en **6 módulos independientes** (Líder + 5 compañeros), tablas propias, puertos, pantallas Android, mocks vs real y DoD. |
| [04_CONTRATO_API_Y_TIEMPO_REAL.md](./04_CONTRATO_API_Y_TIEMPO_REAL.md) | Contrato REST (`/api/v1/...`), códigos de error de negocio, paginación, idempotencia y eventos WebSocket (`/ws`). |
| [05_INTEGRACIONES_MOCK_Y_PRODUCCION.md](./05_INTEGRACIONES_MOCK_Y_PRODUCCION.md) | OSRM (ruteo/polilíneas), pasarela de pagos mock, verificación DNI + OTP mock (Mailpit/consola) y cómo reemplazarlos por servicios reales. |
| [06_GUIA_AGENTES_Y_PRUEBAS.md](./06_GUIA_AGENTES_Y_PRUEBAS.md) | Reglas para agentes e integrantes, flujo Git en paralelo, estándares Go, pruebas de concurrencia y pruebas con teléfonos físicos / nube. |

---

## 2. Stack tecnológico definitivo

| Capa | Tecnología | Notas clave |
|---|---|---|
| **App Móvil** | **Kotlin + Jetpack Compose (Material 3)** | Subproyecto en `android/` (`minSdk 29`, `compileSdk 37`, AGP `9.4.1`, Kotlin `2.2.10`). |
| **Backend API & Realtime** | **Go 1.23+** (monolito modular) | Subproyecto en `backend/`: router `chi/v5`, driver `pgx/v5` (`pgxpool`), `golang-jwt/jwt/v5`, `gorilla/websocket`, `minio-go/v7`, `golang-migrate`. |
| **Base de Datos** | **PostgreSQL 16 + PostGIS 3.4 + pgcrypto** | Esquema en `../backend/db_rutaCompartida.sql` (**73 tablas**, 17 índices `GIST`, 23 triggers, 16 funciones, 4 vistas) + `../backend/seed_bd.sql` + `../backend/seed_dev.sql`. |
| **Ruteo y Polilíneas** | **OSRM** + PostGIS | OSRM público (`router.project-osrm.org`) para MVP sin fricción, con soporte plug-and-play para OSRM local en Docker (`--profile osrm-local`) y mock offline. |
| **Almacenamiento** | **MinIO (S3-compatible)** | Bucket público (`rutacompartida-public`) y privado con URLs firmadas (`rutacompartida-private`). |
| **Correos / OTP Dev** | **Mailpit (SMTP + UI Web)** | Captura OTPs en `http://localhost:8025` e imprime en consola en `APP_ENV=development`. |
| **Despliegue / Pruebas Móvil** | **Docker Compose + Nube a definir** | Entorno local idéntico con Docker; despliegue en nube (Railway / Render / Fly.io / VPS) para probar con 2 teléfonos Android físicos. |

---

## 3. Reglas de negocio innegociables del dominio

1. **Transporte compartido por tramos:** Un conductor publica un recorrido; los pasajeros reservan segmentos (`fraccion_recogida` → `fraccion_destino` entre `0.0` y `1.0`). Un asiento se reutiliza automáticamente en tramos posteriores que no se superpongan.
2. **El conductor define el precio:** Al publicar el viaje, el conductor fija la tarifa (`tipo_tarifa`: `'por_asiento'` o `'por_km'`; la plataforma solo sugiere una referencia de `S/ 2.00/km` en `configuracion`). No hay tarifa base obligatoria ni multiplicadores automáticos.
3. **Verificación previa:**
   - Explorar la app no exige DNI verificado.
   - **Solicitar o confirmar** una reserva exige `usuario.identidad_verificada = TRUE` (DNI aprobado).
   - **Publicar viajes** exige `perfil_conductor.estado = 'aprobado'`, vehículo `aprobado`, **solo 1 viaje activo (`publicado`/`en_curso`) por conductor** y saldo en billetera de comisiones `>= comision.saldo_minimo_publicar_pen`.
4. **Doble confirmación y límites de solicitud:**
   - Flujo obligatorio: **Solicitud (`pendiente`) → Aceptación (manual o automática si `gps_confiable`) → Retención (`esperando_pasajero`, hold 60 s) → Confirmación (`confirmada`)**.
   - Una solicitud `pendiente` **no** ocupa capacidad.
   - Tanto la respuesta manual del conductor como la confirmación del pasajero tienen un límite de **60 segundos**.
   - Máximo **3 intentos** por pasajero en un mismo viaje. **Cancelar voluntariamente antes de que el conductor responda no consume intento.**
   - Un pasajero puede tener múltiples solicitudes/retenciones en distintos viajes, pero al confirmar una se cancelan automáticamente las demás y solo puede tener **1 reserva confirmada/en curso activa**.
5. **Modificaciones y finalización anticipada:**
   - Máximo **1 cambio de recogida** y **1 cambio de destino** por reserva (validado en BD con índices únicos parciales).
   - Reducir asientos después de pagar requiere aprobación del conductor y deja la diferencia retenida **24 horas** (`reembolso.disponible_at`) antes de acreditarse al `Saldo Pasajero`.
   - *"Finalizar recorrido"* anticipado registra descenso real por GPS, recalcula tarifa, acredita diferencia al `Saldo Pasajero` y libera los tramos restantes.
6. **Cancelaciones y No-Show:**
   - Cancelación del pasajero: devolución según proximidad del conductor al punto de recogida (`cancelacion.distancia_lejos_km`).
   - No-show: espera inicial de **5 minutos** desde la llegada del conductor (ampliable si el pasajero avisa), libera capacidad y no ejecuta cobro digital.
7. **Pagos y Triple Billetera:**
   - Métodos: `digital`, `efectivo`, `billetera` y `mixto`. El pago digital se cobra **al abordar** (cuando el conductor confirma recogida); en efectivo, el conductor confirma recepción.
   - 3 billeteras por usuario:
     - `pasajero`: crédito interno para futuros viajes (recargable y receptor de reembolsos/reducciones; no retirable).
     - `ganancias`: neto de cobros digitales del conductor (solo retirable vía CCI/Yape/Plin; nunca se recarga).
     - `comisiones`: recargable por el conductor para cubrir comisiones de viajes en efectivo.
8. **Seguridad, reportes, calificaciones y métricas:**
   - `reporte`, `reporte_evidencia` y `movimiento_billetera` son **inmutables**.
   - Plazos de **descargo** y **apelación de sanción**: **7 días calendario** en zona horaria `America/Lima` (`fn_excede_dias_calendario`).
   - Alertas críticas (`sos`, `seguridad_critica`, `sancion`) **no se pueden desactivar** en `preferencia_notificacion`.
   - Calificación mutua 1–5 estrellas con publicación inmediata si ambos califican, o diferida a **24 horas**. Propinas, etiquetas y anonimato se persisten en `calificacion`/`pago`; `CO₂ evitado`, `puntualidad`, `total_viajes` e insignias se **calculan**.

---

## 4. División del equipo (6 personas en paralelo)

| Rol / Módulo | Carpeta Go | Responsable | Resumen de cobertura |
|---|---|---|---|
| **Core compartido** | `internal/shared/` | **Tú (Líder técnico)** | Conexión PostgreSQL/tx, errores, envelope HTTP, JWT/roles, helpers PostGIS + OSRM, MinIO Storage, Hub WebSocket, Scheduler y `contracts`. |
| **Módulo 1** | `internal/modules/auth_identity/` | **Integrante 1** *(o tú según asignación)* | Registro, login, JWT refresh, OTP (mock→real), verificación DNI (mock→real), perfil conductor, vehículos, documentos privados y contactos de emergencia. |
| **Módulo 2** | `internal/modules/routes_trips/` | **Integrante 2** | Rutas base, OSRM polilíneas, publicar/editar/cancelar viaje, paradas operativas, segmentos, pausar reservas y aceptar/rechazar solicitudes (60 s). |
| **Módulo 3** | `internal/modules/search_bookings/` | **Integrante 3** | Búsqueda espacial PostGIS (`ST_DWithin`), cotización por tramo, solicitar viaje (auto/manual), confirmar hold 60 s, límite 3 intentos y modificaciones de reserva. |
| **Módulo 4** | `internal/modules/active_trip_gps/` | **Integrante 4** | Ingesta GPS (online/offline), WebSocket tracking, llegada/abordaje, no-show (5 min), finalización anticipada, cancelaciones, enlaces de seguimiento y calificaciones. |
| **Módulo 5** | `internal/modules/payments_wallets/` | **Integrante 5** | Cobro al abordar (digital/efectivo/billetera/mixto), pasarela mock, 3 billeteras, recargas, retiros, comisiones, reembolsos/créditos 24 h, promociones y propinas. |
| **Módulo 6** | `internal/modules/messaging_safety_support/` | **Integrante 6** | Chat por reserva (REST + WS), notificaciones + preferencias, alertas SOS, incidentes, reportes inmutables, descargos, sanciones/apelaciones y tickets de soporte. |

> **Nota sobre la asignación del Líder:** Tú te encargas de **`internal/shared/`** y eliges **uno** de los 6 módulos; los otros 5 módulos se reparten entre tus 5 compañeros. En `03_MODULOS_Y_DIVISION_EQUIPO.md` se detalla exactamente cada uno.
