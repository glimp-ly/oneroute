-- ============================================================
-- RUTACOMPARTIDA
-- Datos iniciales (seed) de catálogos y configuración
-- Motor: PostgreSQL 16+
-- Prerrequisito: ejecutar primero db_rutaCompartida.sql
-- ============================================================

BEGIN;

INSERT INTO moneda (codigo, nombre, simbolo, decimales)
VALUES ('PEN', 'Sol peruano', 'S/', 2)
ON CONFLICT (codigo) DO NOTHING;

INSERT INTO pais (codigo_iso2, codigo_iso3, nombre, codigo_telefonico)
VALUES ('PE', 'PER', 'Perú', '+51')
ON CONFLICT (codigo_iso2) DO NOTHING;

INSERT INTO rol (codigo, nombre, descripcion, es_interno)
VALUES
    ('pasajero', 'Pasajero', 'Usuario estándar que solicita y reserva tramos', FALSE),
    ('conductor', 'Conductor', 'Usuario habilitado para publicar viajes y conducir', FALSE),
    ('soporte', 'Soporte', 'Agente de atención de tickets e incidentes', TRUE),
    ('moderador', 'Moderador', 'Responsable de investigaciones, restricciones y sanciones', TRUE),
    ('admin', 'Administrador', 'Administrador general de la plataforma', TRUE)
ON CONFLICT (codigo) DO NOTHING;

INSERT INTO metodo_pago (codigo, nombre, es_combinado)
VALUES
    ('digital', 'Pago digital', FALSE),
    ('efectivo', 'Efectivo', FALSE),
    ('billetera', 'Saldo de billetera', FALSE),
    ('mixto', 'Pago combinado', TRUE)
ON CONFLICT (codigo) DO NOTHING;

INSERT INTO categoria_incidente (nombre, descripcion, severidad_defecto)
VALUES
    ('Falla mecánica', 'Problemas mecánicos durante el viaje', 'alta'),
    ('Comportamiento sospechoso', 'Conducta sospechosa de algún participante', 'alta'),
    ('Conflicto', 'Discusión o conflicto entre participantes', 'normal'),
    ('Desvío de ruta', 'Desvío no autorizado de la ruta', 'alta'),
    ('Acoso', 'Situación de acoso', 'critica'),
    ('Otro', 'Otro tipo de incidente', 'normal')
ON CONFLICT (nombre) DO NOTHING;

INSERT INTO categoria_reporte (nombre, descripcion, es_grave)
VALUES
    ('Acoso', 'Conductas de acoso', TRUE),
    ('Amenazas', 'Amenazas o intimidación', TRUE),
    ('Mal comportamiento', 'Comportamiento inadecuado', FALSE),
    ('Incumplimiento', 'Incumplimiento de condiciones del viaje', FALSE),
    ('Seguridad', 'Problemas relacionados con seguridad', TRUE),
    ('Otro', 'Otro motivo', FALSE)
ON CONFLICT (nombre) DO NOTHING;

INSERT INTO categoria_notificacion (codigo, nombre, descripcion, es_critica, desactivable, prioridad_defecto)
VALUES
    ('sos', 'Alerta SOS', 'Alertas críticas de emergencia durante el viaje', TRUE, FALSE, 'critica'),
    ('seguridad_critica', 'Seguridad crítica', 'Avisos urgentes de seguridad o incidentes graves', TRUE, FALSE, 'critica'),
    ('sancion', 'Sanciones y restricciones', 'Notificaciones formales sobre reportes, descargos y sanciones', TRUE, FALSE, 'alta'),
    ('reserva', 'Reservas y solicitudes', 'Estados de solicitudes, confirmaciones, cambios y cancelaciones', FALSE, TRUE, 'alta'),
    ('viaje', 'Estado del viaje', 'Inicio, aproximación, recogida, cambios de ruta y fin del viaje', FALSE, TRUE, 'normal'),
    ('pago', 'Pagos y billeteras', 'Cobros, reembolsos, retiros, recargas y comisiones', FALSE, TRUE, 'normal'),
    ('chat', 'Mensajes de chat', 'Mensajes nuevos dentro de una reserva', FALSE, TRUE, 'normal'),
    ('calificacion', 'Calificaciones', 'Recordatorios y publicación de calificaciones', FALSE, TRUE, 'baja'),
    ('promocion', 'Promociones y novedades', 'Campañas promocionales y comunicaciones informativas', FALSE, TRUE, 'baja')
ON CONFLICT (codigo) DO NOTHING;

INSERT INTO tarifa_comision (porcentaje, vigente_desde, activo)
SELECT 10.00, NOW(), TRUE
WHERE NOT EXISTS (SELECT 1 FROM tarifa_comision);

INSERT INTO configuracion (clave, valor, descripcion)
VALUES
    ('sistema.zona_horaria', '"America/Lima"', 'Zona horaria oficial para plazos calendario y eventos'),
    ('tarifa.definida_por_conductor', 'true', 'Indica que el conductor define libremente el precio al publicar el viaje'),
    ('tarifa.referencial_por_km_pen', '2.00', 'Tarifa referencial sugerida por kilómetro en soles (el conductor define el precio final)'),
    ('tarifa.base_pen', '0.00', 'Tarifa base fija en soles (sin tarifa base obligatoria)'),
    ('metrica.co2_kg_por_km', '0.12', 'Factor referencial de kg de CO2 evitados por km compartido'),
    ('ruta.desvio_maximo_default_km', '1.50', 'Desvío máximo permitido por defecto respecto a la ruta'),
    ('reserva.hold_segundos', '60', 'Tiempo para confirmar una reserva aceptada'),
    ('reserva.expiracion_solicitud_segundos', '60', 'Tiempo máximo del conductor para responder manualmente'),
    ('reserva.max_intentos', '3', 'Máximo de intentos por pasajero en un mismo viaje'),
    ('reserva.cambio_recogida_maximo', '1', 'Cantidad máxima de cambios de punto de recogida'),
    ('reserva.cambio_destino_maximo', '1', 'Cantidad máxima de cambios de destino'),
    ('reserva.retencion_reduccion_horas', '24', 'Horas de retención antes de acreditar saldo por reducción de asientos'),
    ('reserva.espera_no_show_minutos', '5', 'Espera inicial antes de marcar no-show'),
    ('cancelacion.distancia_lejos_km', '2.00', 'Distancia mínima del conductor para devolución total'),
    ('cancelacion.porcentaje_reembolso_cerca', '50', 'Porcentaje de devolución cuando el conductor está cerca'),
    ('comision.saldo_minimo_publicar_pen', '0.00', 'Saldo mínimo en billetera de comisiones para publicar viajes'),
    ('reporte.plazo_descargo_dias', '7', 'Plazo en días calendario para presentar descargos'),
    ('sancion.plazo_apelacion_dias', '7', 'Plazo en días calendario para apelar una sanción'),
    ('gps.retencion_dias', '30', 'Retención estándar de ubicaciones GPS')
ON CONFLICT (clave) DO NOTHING;

COMMIT;

-- ============================================================
-- FIN DEL SEED RUTACOMPARTIDA
-- ============================================================
