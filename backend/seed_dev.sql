-- ============================================================
-- RUTACOMPARTIDA
-- Datos ficticios de desarrollo (seed_dev)
-- Motor: PostgreSQL 16+ con PostGIS y pgcrypto
-- Prerrequisito: ejecutar después de db_rutaCompartida.sql y seed_bd.sql
-- Contraseña de todos los usuarios de prueba: Password123!
-- ============================================================

BEGIN;

-- ============================================================
-- 1. USUARIOS Y ROLES DE PRUEBA
-- ============================================================

INSERT INTO usuario (
    id, nombres, apellidos, correo, telefono, password_hash,
    pais_id, estado, correo_verificado, telefono_verificado, identidad_verificada,
    calificacion_pasajero_promedio, total_viajes_pasajero
)
VALUES
    (
        '10000000-0000-0000-0000-000000000001',
        'Admin', 'Plataforma', 'admin@rutacompartida.pe', '+51900000001',
        crypt('Password123!', gen_salt('bf', 10)),
        (SELECT id FROM pais WHERE codigo_iso2 = 'PE'),
        'activo', TRUE, TRUE, TRUE, 5.00, 0
    ),
    (
        '10000000-0000-0000-0000-000000000002',
        'Mateo', 'Mendoza Valdez', 'conductor@rutacompartida.pe', '+51987654321',
        crypt('Password123!', gen_salt('bf', 10)),
        (SELECT id FROM pais WHERE codigo_iso2 = 'PE'),
        'activo', TRUE, TRUE, TRUE, 4.95, 18
    ),
    (
        '10000000-0000-0000-0000-000000000003',
        'Lucía', 'Paredes Quispe', 'pasajero1@rutacompartida.pe', '+51987111222',
        crypt('Password123!', gen_salt('bf', 10)),
        (SELECT id FROM pais WHERE codigo_iso2 = 'PE'),
        'activo', TRUE, TRUE, TRUE, 4.90, 12
    ),
    (
        '10000000-0000-0000-0000-000000000004',
        'Diego', 'Valdés Torres', 'pasajero2@rutacompartida.pe', '+51999333444',
        crypt('Password123!', gen_salt('bf', 10)),
        (SELECT id FROM pais WHERE codigo_iso2 = 'PE'),
        'activo', TRUE, TRUE, TRUE, 4.80, 7
    ),
    (
        '10000000-0000-0000-0000-000000000005',
        'Sofía', 'Rojas Chávez', 'nuevo@rutacompartida.pe', '+51955444333',
        crypt('Password123!', gen_salt('bf', 10)),
        (SELECT id FROM pais WHERE codigo_iso2 = 'PE'),
        'activo', TRUE, FALSE, FALSE, 0.00, 0
    )
ON CONFLICT (id) DO NOTHING;

INSERT INTO usuario_rol (usuario_id, rol_id)
SELECT '10000000-0000-0000-0000-000000000001', id FROM rol WHERE codigo IN ('admin', 'soporte', 'moderador')
ON CONFLICT (usuario_id, rol_id) DO NOTHING;

INSERT INTO usuario_rol (usuario_id, rol_id)
SELECT '10000000-0000-0000-0000-000000000002', id FROM rol WHERE codigo IN ('pasajero', 'conductor')
ON CONFLICT (usuario_id, rol_id) DO NOTHING;

INSERT INTO usuario_rol (usuario_id, rol_id)
SELECT u_id, r.id
FROM (VALUES
    ('10000000-0000-0000-0000-000000000003'::uuid),
    ('10000000-0000-0000-0000-000000000004'::uuid),
    ('10000000-0000-0000-0000-000000000005'::uuid)
) AS u(u_id)
CROSS JOIN rol r
WHERE r.codigo = 'pasajero'
ON CONFLICT (usuario_id, rol_id) DO NOTHING;

-- ============================================================
-- 2. VERIFICACIÓN DE IDENTIDAD Y CONTACTOS DE EMERGENCIA
-- ============================================================

INSERT INTO verificacion_identidad (
    id, usuario_id, tipo_documento, numero_documento, pais_emisor_id,
    proveedor, referencia_proveedor, estado, verificado_at, revisado_por
)
VALUES
    (
        '11000000-0000-0000-0000-000000000002',
        '10000000-0000-0000-0000-000000000002',
        'DNI', '72381920', (SELECT id FROM pais WHERE codigo_iso2 = 'PE'),
        'mock', 'MOCK-DNI-002', 'aprobado', NOW() - INTERVAL '30 days',
        '10000000-0000-0000-0000-000000000001'
    ),
    (
        '11000000-0000-0000-0000-000000000003',
        '10000000-0000-0000-0000-000000000003',
        'DNI', '74128901', (SELECT id FROM pais WHERE codigo_iso2 = 'PE'),
        'mock', 'MOCK-DNI-003', 'aprobado', NOW() - INTERVAL '15 days',
        '10000000-0000-0000-0000-000000000001'
    ),
    (
        '11000000-0000-0000-0000-000000000004',
        '10000000-0000-0000-0000-000000000004',
        'DNI', '75901234', (SELECT id FROM pais WHERE codigo_iso2 = 'PE'),
        'mock', 'MOCK-DNI-004', 'aprobado', NOW() - INTERVAL '10 days',
        '10000000-0000-0000-0000-000000000001'
    )
ON CONFLICT (id) DO NOTHING;

INSERT INTO contacto_emergencia (id, usuario_id, nombre, parentesco, telefono, es_principal)
VALUES
    ('12000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000002', 'Elena Mendoza', 'Madre', '+51987111000', TRUE),
    ('12000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000002', 'Carlos Mendoza', 'Hermano', '+51987111001', FALSE),
    ('12000000-0000-0000-0000-000000000003', '10000000-0000-0000-0000-000000000003', 'Rosa Quispe', 'Madre', '+51987222000', TRUE)
ON CONFLICT (id) DO NOTHING;

-- ============================================================
-- 3. PERFIL CONDUCTOR, VEHÍCULO Y DOCUMENTACIÓN
-- ============================================================

INSERT INTO perfil_conductor (
    id, usuario_id, estado, calificacion_promedio, total_viajes,
    acepta_nuevas_reservas, aprobado_at
)
VALUES (
    '20000000-0000-0000-0000-000000000001',
    '10000000-0000-0000-0000-000000000002',
    'aprobado', 4.95, 142, TRUE, NOW() - INTERVAL '30 days'
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO vehiculo (
    id, perfil_conductor_id, placa, marca, modelo, anio, color,
    capacidad_pasajeros, tipo, comodidades, estado
)
VALUES (
    '21000000-0000-0000-0000-000000000001',
    '20000000-0000-0000-0000-000000000001',
    'ABC-782', 'Volkswagen', 'Jetta Trendline', 2023, 'Azul Marino',
    4, 'sedan',
    '["Aire Acondicionado", "Cargador USB-C", "Audio Bluetooth"]'::jsonb,
    'aprobado'
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO solicitud_conductor (
    id, usuario_id, perfil_conductor_id, vehiculo_id,
    estado, observaciones, revisado_por, revisado_at
)
VALUES (
    '22000000-0000-0000-0000-000000000001',
    '10000000-0000-0000-0000-000000000002',
    '20000000-0000-0000-0000-000000000001',
    '21000000-0000-0000-0000-000000000001',
    'aprobado', 'Documentación completa y verificada en dev',
    '10000000-0000-0000-0000-000000000001', NOW() - INTERVAL '30 days'
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO documento_conductor (
    id, perfil_conductor_id, vehiculo_id, solicitud_conductor_id,
    tipo, numero_documento, archivo_cifrado, fecha_emision, fecha_vencimiento,
    estado, revisado_por, revisado_at
)
VALUES
    (
        '23000000-0000-0000-0000-000000000001',
        '20000000-0000-0000-0000-000000000001', NULL, '22000000-0000-0000-0000-000000000001',
        'licencia', 'Q72381920', 'private/conductor/licencia_mock.pdf',
        '2022-08-15', '2028-08-15', 'aprobado',
        '10000000-0000-0000-0000-000000000001', NOW() - INTERVAL '30 days'
    ),
    (
        '23000000-0000-0000-0000-000000000002',
        '20000000-0000-0000-0000-000000000001', '21000000-0000-0000-0000-000000000001', '22000000-0000-0000-0000-000000000001',
        'soat', 'SOAT-48201-PE', 'private/conductor/soat_mock.pdf',
        '2026-01-01', '2027-01-01', 'aprobado',
        '10000000-0000-0000-0000-000000000001', NOW() - INTERVAL '30 days'
    ),
    (
        '23000000-0000-0000-0000-000000000003',
        '20000000-0000-0000-0000-000000000001', '21000000-0000-0000-0000-000000000001', '22000000-0000-0000-0000-000000000001',
        'tarjeta_propiedad', 'TP-782-XYZ', 'private/conductor/tarjeta_propiedad_mock.pdf',
        '2023-03-10', NULL, 'aprobado',
        '10000000-0000-0000-0000-000000000001', NOW() - INTERVAL '30 days'
    )
ON CONFLICT (id) DO NOTHING;

-- ============================================================
-- 4. BILLETERAS Y MOVIMIENTOS INICIALES (3 BILLETERAS)
-- ============================================================

INSERT INTO billetera (id, usuario_id, tipo, moneda_id, saldo, permite_saldo_negativo)
VALUES
    -- Conductor: las 3 billeteras
    ('50000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000002', 'pasajero',   (SELECT id FROM moneda WHERE codigo = 'PEN'), 45.00,  FALSE),
    ('50000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000002', 'ganancias',  (SELECT id FROM moneda WHERE codigo = 'PEN'), 845.00, FALSE),
    ('50000000-0000-0000-0000-000000000003', '10000000-0000-0000-0000-000000000002', 'comisiones', (SELECT id FROM moneda WHERE codigo = 'PEN'), 35.00,  TRUE),
    -- Pasajero 1
    ('50000000-0000-0000-0000-000000000004', '10000000-0000-0000-0000-000000000003', 'pasajero',   (SELECT id FROM moneda WHERE codigo = 'PEN'), 60.00,  FALSE),
    -- Pasajero 2
    ('50000000-0000-0000-0000-000000000005', '10000000-0000-0000-0000-000000000004', 'pasajero',   (SELECT id FROM moneda WHERE codigo = 'PEN'), 30.00,  FALSE)
ON CONFLICT (id) DO NOTHING;

INSERT INTO movimiento_billetera (
    id, billetera_id, tipo, direccion, monto, saldo_anterior, saldo_nuevo, descripcion
)
VALUES
    ('51000000-0000-0000-0000-000000000001', '50000000-0000-0000-0000-000000000001', 'ajuste',         'entrada', 45.00,  0.00, 45.00,  'Saldo inicial dev pasajero'),
    ('51000000-0000-0000-0000-000000000002', '50000000-0000-0000-0000-000000000002', 'ganancia_viaje', 'entrada', 845.00, 0.00, 845.00, 'Saldo inicial dev ganancias'),
    ('51000000-0000-0000-0000-000000000003', '50000000-0000-0000-0000-000000000003', 'recarga',        'entrada', 35.00,  0.00, 35.00,  'Recarga inicial dev comisiones'),
    ('51000000-0000-0000-0000-000000000004', '50000000-0000-0000-0000-000000000004', 'recarga',        'entrada', 60.00,  0.00, 60.00,  'Saldo inicial pasajero 1'),
    ('51000000-0000-0000-0000-000000000005', '50000000-0000-0000-0000-000000000005', 'recarga',        'entrada', 30.00,  0.00, 30.00,  'Saldo inicial pasajero 2')
ON CONFLICT (id) DO NOTHING;

-- ============================================================
-- 5. RUTAS EN LIMA Y VIAJE PUBLICADO
-- ============================================================

INSERT INTO ruta (
    id, nombre, descripcion, origen_nombre, destino_nombre,
    geometria, distancia_km, creado_por
)
VALUES
    (
        '30000000-0000-0000-0000-000000000001',
        'Miraflores → San Isidro (Vía Av. Arequipa)',
        'Ruta troncal por Av. Arequipa hacia Centro Empresarial San Isidro',
        'Óvalo de Miraflores', 'Centro Empresarial San Isidro',
        ST_GeogFromText('SRID=4326;LINESTRING(-77.0290 -12.1191, -77.0320 -12.1060, -77.0365 -12.0942)'),
        5.80, '10000000-0000-0000-0000-000000000002'
    ),
    (
        '30000000-0000-0000-0000-000000000002',
        'Surco → San Isidro (Vía Benavides / Paseo de la República)',
        'Ruta desde Óvalo Higuereta hacia Av. Rivera Navarrete',
        'Óvalo Higuereta', 'Av. Rivera Navarrete',
        ST_GeogFromText('SRID=4326;LINESTRING(-77.0110 -12.1292, -77.0215 -12.1130, -77.0272 -12.0955)'),
        6.40, '10000000-0000-0000-0000-000000000002'
    )
ON CONFLICT (id) DO NOTHING;

INSERT INTO ruta_punto (id, ruta_id, orden, nombre, ubicacion, tipo, fraccion_ruta, distancia_acumulada_km)
VALUES
    ('31000000-0000-0000-0000-000000000001', '30000000-0000-0000-0000-000000000001', 1, 'Óvalo de Miraflores',         ST_GeogFromText('SRID=4326;POINT(-77.0290 -12.1191)'), 'origen',  0.000000, 0.000),
    ('31000000-0000-0000-0000-000000000002', '30000000-0000-0000-0000-000000000001', 2, 'Av. Arequipa cdra. 35',       ST_GeogFromText('SRID=4326;POINT(-77.0320 -12.1060)'), 'parada',  0.520000, 3.000),
    ('31000000-0000-0000-0000-000000000003', '30000000-0000-0000-0000-000000000001', 3, 'Centro Empresarial San Isidro', ST_GeogFromText('SRID=4326;POINT(-77.0365 -12.0942)'), 'destino', 1.000000, 5.800)
ON CONFLICT (id) DO NOTHING;

INSERT INTO viaje (
    id, codigo, conductor_id, vehiculo_id, ruta_id, moneda_id,
    salida_programada_at, estado, acepta_reservas, aceptacion_automatica, gps_confiable,
    ultima_ubicacion, ultima_ubicacion_at, fraccion_avance_ruta,
    capacidad_total, tipo_tarifa, precio_asiento_completo, tarifa_km,
    equipaje_permitido, reglas_abordo, desvio_maximo_km, distancia_operativa_km, ruta_operativa
)
VALUES (
    '32000000-0000-0000-0000-000000000001',
    'RTDEV001',
    '10000000-0000-0000-0000-000000000002',
    '21000000-0000-0000-0000-000000000001',
    '30000000-0000-0000-0000-000000000001',
    (SELECT id FROM moneda WHERE codigo = 'PEN'),
    NOW() + INTERVAL '2 hours',
    'publicado', TRUE, FALSE, TRUE,
    ST_GeogFromText('SRID=4326;POINT(-77.0290 -12.1191)'), NOW(), 0.000000,
    3, 'por_asiento', 24.00, 2.00,
    'Mediano (Mochila / Maletín)', '["No fumar", "Aire acondicionado"]'::jsonb,
    1.50, 5.80,
    ST_GeogFromText('SRID=4326;LINESTRING(-77.0290 -12.1191, -77.0320 -12.1060, -77.0365 -12.0942)')
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO viaje_parada (
    id, viaje_id, orden, tipo, nombre, ubicacion, fraccion_ruta, distancia_acumulada_km, desvio_km
)
VALUES
    ('33000000-0000-0000-0000-000000000001', '32000000-0000-0000-0000-000000000001', 1, 'origen',      'Óvalo de Miraflores',         ST_GeogFromText('SRID=4326;POINT(-77.0290 -12.1191)'), 0.000000, 0.000, 0.000),
    ('33000000-0000-0000-0000-000000000002', '32000000-0000-0000-0000-000000000001', 2, 'parada_base', 'Av. Arequipa cdra. 35',       ST_GeogFromText('SRID=4326;POINT(-77.0320 -12.1060)'), 0.520000, 3.000, 0.000),
    ('33000000-0000-0000-0000-000000000003', '32000000-0000-0000-0000-000000000001', 3, 'destino',     'Centro Empresarial San Isidro', ST_GeogFromText('SRID=4326;POINT(-77.0365 -12.0942)'), 1.000000, 5.800, 0.000)
ON CONFLICT (id) DO NOTHING;

INSERT INTO viaje_segmento (
    id, viaje_id, orden, origen, destino, fraccion_inicio, fraccion_fin,
    distancia_km, capacidad, precio_por_asiento
)
VALUES
    (
        '34000000-0000-0000-0000-000000000001',
        '32000000-0000-0000-0000-000000000001', 1,
        ST_GeogFromText('SRID=4326;POINT(-77.0290 -12.1191)'),
        ST_GeogFromText('SRID=4326;POINT(-77.0320 -12.1060)'),
        0.000000, 0.520000, 3.000, 3, 12.00
    ),
    (
        '34000000-0000-0000-0000-000000000002',
        '32000000-0000-0000-0000-000000000001', 2,
        ST_GeogFromText('SRID=4326;POINT(-77.0320 -12.1060)'),
        ST_GeogFromText('SRID=4326;POINT(-77.0365 -12.0942)'),
        0.520000, 1.000000, 2.800, 3, 12.00
    )
ON CONFLICT (id) DO NOTHING;

-- ============================================================
-- 6. SOLICITUDES, RESERVA CONFIRMADA Y CHAT DE PRUEBA
-- ============================================================

-- Pasajero 1: solicitud aceptada y reserva confirmada
INSERT INTO solicitud_reserva (
    id, viaje_id, pasajero_id, punto_recogida, punto_destino,
    fraccion_recogida, fraccion_destino, nombre_recogida, nombre_destino,
    asientos_solicitados, estado, intentos_consumidos, modo_aceptacion, respuesta_at
)
VALUES (
    '40000000-0000-0000-0000-000000000001',
    '32000000-0000-0000-0000-000000000001',
    '10000000-0000-0000-0000-000000000003',
    ST_GeogFromText('SRID=4326;POINT(-77.0290 -12.1191)'),
    ST_GeogFromText('SRID=4326;POINT(-77.0365 -12.0942)'),
    0.000000, 1.000000, 'Óvalo de Miraflores', 'Centro Empresarial San Isidro',
    1, 'aceptada', 0, 'manual', NOW() - INTERVAL '10 minutes'
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO solicitud_intento (
    id, solicitud_id, secuencia, cuenta_para_limite, modo_aceptacion,
    resultado, finalizado_at
)
VALUES (
    '41000000-0000-0000-0000-000000000001',
    '40000000-0000-0000-0000-000000000001',
    1, TRUE, 'manual', 'aceptada', NOW() - INTERVAL '10 minutes'
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO reserva (
    id, codigo, viaje_id, pasajero_id, solicitud_id, solicitud_intento_id,
    parada_recogida_id, parada_destino_id, fraccion_recogida, fraccion_destino,
    distancia_reservada_km, asientos_reservados, estado, precio_original, precio_total,
    moneda_id, conductor_acepto_at, pasajero_confirmo_at,
    punto_recogida, punto_destino, nombre_recogida, nombre_destino
)
VALUES (
    '42000000-0000-0000-0000-000000000001',
    'RESDEV01',
    '32000000-0000-0000-0000-000000000001',
    '10000000-0000-0000-0000-000000000003',
    '40000000-0000-0000-0000-000000000001',
    '41000000-0000-0000-0000-000000000001',
    '33000000-0000-0000-0000-000000000001',
    '33000000-0000-0000-0000-000000000003',
    0.000000, 1.000000, 5.800, 1, 'confirmada', 24.00, 24.00,
    (SELECT id FROM moneda WHERE codigo = 'PEN'),
    NOW() - INTERVAL '10 minutes', NOW() - INTERVAL '9 minutes',
    ST_GeogFromText('SRID=4326;POINT(-77.0290 -12.1191)'),
    ST_GeogFromText('SRID=4326;POINT(-77.0365 -12.0942)'),
    'Óvalo de Miraflores', 'Centro Empresarial San Isidro'
)
ON CONFLICT (id) DO NOTHING;

-- Pasajero 2: solicitud pendiente lista para probar aceptación/rechazo del conductor
INSERT INTO solicitud_reserva (
    id, viaje_id, pasajero_id, punto_recogida, punto_destino,
    fraccion_recogida, fraccion_destino, nombre_recogida, nombre_destino,
    asientos_solicitados, estado, intentos_consumidos, expira_at
)
VALUES (
    '40000000-0000-0000-0000-000000000002',
    '32000000-0000-0000-0000-000000000001',
    '10000000-0000-0000-0000-000000000004',
    ST_GeogFromText('SRID=4326;POINT(-77.0320 -12.1060)'),
    ST_GeogFromText('SRID=4326;POINT(-77.0365 -12.0942)'),
    0.520000, 1.000000, 'Av. Arequipa cdra. 35', 'Centro Empresarial San Isidro',
    1, 'pendiente', 0, NOW() + INTERVAL '10 minutes'
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO solicitud_intento (
    id, solicitud_id, secuencia, cuenta_para_limite, resultado, expira_at
)
VALUES (
    '41000000-0000-0000-0000-000000000002',
    '40000000-0000-0000-0000-000000000002',
    1, TRUE, 'pendiente', NOW() + INTERVAL '10 minutes'
)
ON CONFLICT (id) DO NOTHING;

-- Chat de la reserva confirmada
INSERT INTO chat_reserva (id, reserva_id, activo)
VALUES ('43000000-0000-0000-0000-000000000001', '42000000-0000-0000-0000-000000000001', TRUE)
ON CONFLICT (id) DO NOTHING;

INSERT INTO mensaje_chat (id, chat_id, remitente_id, tipo, contenido)
VALUES
    ('44000000-0000-0000-0000-000000000001', '43000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000002', 'texto', 'Hola Lucía, confirmo tu punto de recogida en el Óvalo de Miraflores.'),
    ('44000000-0000-0000-0000-000000000002', '43000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000003', 'texto', 'Perfecto Mateo, estaré 5 minutos antes.')
ON CONFLICT (id) DO NOTHING;

-- ============================================================
-- 7. PROMOCIÓN DE DESARROLLO
-- ============================================================

INSERT INTO promocion (
    id, codigo, nombre, descripcion, tipo, valor, moneda_id,
    monto_maximo, usos_maximos, usos_maximos_por_usuario,
    vigente_desde, vigente_hasta, activa
)
VALUES (
    '52000000-0000-0000-0000-000000000001',
    'BIENVENIDO20',
    '20% en tu primer viaje',
    'Promoción de prueba financiada por la plataforma',
    'porcentaje', 20.00, (SELECT id FROM moneda WHERE codigo = 'PEN'),
    10.00, 1000, 1,
    NOW() - INTERVAL '1 day', NOW() + INTERVAL '90 days', TRUE
)
ON CONFLICT (id) DO NOTHING;

COMMIT;

-- ============================================================
-- FIN DEL SEED DE DESARROLLO RUTACOMPARTIDA
-- ============================================================
