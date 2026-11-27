---
name: postgis-query
description: >-
  Use this skill when writing, optimizing, or validating PostGIS geospatial
  queries, route fractions, detour checks, or GIST indexes in OneRoute.
---

# PostGIS Spatial Query Guide (OneRoute)

Sigue estas reglas al escribir consultas espaciales para `routes_trips`, `search_bookings` o `active_trip_gps` sobre PostgreSQL 16 + PostGIS 3.4.

## Steps

1. **Respetar el formato y orden de coordenadas:**
   - Todas las columnas espaciales usan `GEOGRAPHY(..., 4326)` (`POINT` o `LINESTRING`).
   - En EWKT y `ST_MakePoint`, el orden obligatorio en PostGIS es **longitud primero, latitud despues**:
     - `ST_GeogFromText('SRID=4326;POINT(lng lat)')`
     - `ST_SetSRID(ST_MakePoint($1::double precision, $2::double precision), 4326)::geography`

2. **Usar funciones que aprovechen los 17 indices GIST:**
   - Para buscar viajes cercanos a un punto de recogida o destino dentro del desvio maximo, filtra sobre `GEOGRAPHY` en metros:
     ```sql
     WHERE ST_DWithin(v.ruta_operativa, $1::geography, v.desvio_maximo_km * 1000.0)
     ```
   - Evita envolver la columna indexada con casts que impidan usar el indice GIST en el filtro principal.

3. **Calcular fraccion de tramo y distancia sobre la ruta:**
   - Fraccion normalizada `[0.0, 1.0]` sobre la polilinea (`LINESTRING`):
     ```sql
     ST_LineLocatePoint(geometria::geometry, punto::geometry)
     ```
   - Distancia del sub-tramo en kilometros:
     ```sql
     ST_Length(ST_LineSubstring(geometria::geometry, fraccion_inicio, fraccion_fin)::geography) / 1000.0
     ```
   - Verifica siempre que `fraccion_recogida >= v.fraccion_avance_ruta` y `fraccion_destino > fraccion_recogida`.

4. **Verificar sintaxis y plan de ejecucion en PostgreSQL real:**
   - Prueba la consulta dentro de una transaccion sin mutar datos contra `rutacompartida_postgres`:
     ```bash
     docker exec rutacompartida_postgres psql -U rutacompartida -d rutacompartida -v ON_ERROR_STOP=1 -c "BEGIN; EXPLAIN <TU_QUERY>; ROLLBACK;"
     ```
   - Confirma que los `CHECK` de `viaje_segmento`, `solicitud_reserva` y `reserva` y el trigger `trg_validar_capacidad_reserva` se cumplan.
