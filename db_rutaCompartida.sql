-- ============================================================
-- RUTACOMPARTIDA
-- Esquema integral de base de datos (revisado y ampliado)
-- Motor: PostgreSQL 16+
-- Extensiones: pgcrypto, PostGIS
-- ============================================================

BEGIN;

CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS postgis;

-- ============================================================
-- 1. FUNCIONES GENERALES
-- ============================================================

CREATE OR REPLACE FUNCTION fn_actualizar_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION fn_generar_codigo()
RETURNS TEXT AS $$
BEGIN
    RETURN UPPER(ENCODE(GEN_RANDOM_BYTES(6), 'hex'));
END;
$$ LANGUAGE plpgsql;

-- Evalúa plazos en días calendario respecto a la zona horaria configurada.
CREATE OR REPLACE FUNCTION fn_excede_dias_calendario(
    p_inicio TIMESTAMPTZ,
    p_evento TIMESTAMPTZ,
    p_dias INTEGER
)
RETURNS BOOLEAN AS $$
DECLARE
    v_tz TEXT;
BEGIN
    SELECT NULLIF(TRIM(valor #>> '{}'), '')
    INTO v_tz
    FROM configuracion
    WHERE clave = 'sistema.zona_horaria';

    v_tz := COALESCE(v_tz, 'America/Lima');

    RETURN (p_evento AT TIME ZONE v_tz)::DATE > ((p_inicio AT TIME ZONE v_tz)::DATE + p_dias);
END;
$$ LANGUAGE plpgsql STABLE;

-- ============================================================
-- 2. CATÁLOGOS Y CONFIGURACIÓN
-- ============================================================

CREATE TABLE moneda (
    id UUID PRIMARY KEY DEFAULT GEN_RANDOM_UUID(),
    codigo VARCHAR(3) NOT NULL UNIQUE,
    nombre VARCHAR(60) NOT NULL,
    simbolo VARCHAR(8) NOT NULL,
    decimales SMALLINT NOT NULL DEFAULT 2,
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    CHECK (decimales BETWEEN 0 AND 4)
);

CREATE TABLE pais (
    id UUID PRIMARY KEY DEFAULT GEN_RANDOM_UUID(),
    codigo_iso2 CHAR(2) NOT NULL UNIQUE,
    codigo_iso3 CHAR(3) UNIQUE,
    nombre VARCHAR(100) NOT NULL,
    codigo_telefonico VARCHAR(8),
    activo BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE configuracion (
    id UUID PRIMARY KEY DEFAULT GEN_RANDOM_UUID(),
    clave VARCHAR(100) NOT NULL UNIQUE,
    valor JSONB NOT NULL,
    descripcion TEXT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE categoria_incidente (
    id UUID PRIMARY KEY DEFAULT GEN_RANDOM_UUID(),
    nombre VARCHAR(80) NOT NULL UNIQUE,
    descripcion TEXT,
    severidad_defecto VARCHAR(20) NOT NULL DEFAULT 'normal',
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    CHECK (severidad_defecto IN ('baja', 'normal', 'alta', 'critica'))
);

CREATE TABLE categoria_reporte (
    id UUID PRIMARY KEY DEFAULT GEN_RANDOM_UUID(),
    nombre VARCHAR(80) NOT NULL UNIQUE,
    descripcion TEXT,
    es_grave BOOLEAN NOT NULL DEFAULT FALSE,
    activo BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE categoria_notificacion (
    codigo VARCHAR(40) PRIMARY KEY,
    nombre VARCHAR(100) NOT NULL,
    descripcion TEXT,
    es_critica BOOLEAN NOT NULL DEFAULT FALSE,
    desactivable BOOLEAN NOT NULL DEFAULT TRUE,
    prioridad_defecto VARCHAR(20) NOT NULL DEFAULT 'normal',
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    CHECK (prioridad_defecto IN ('baja', 'normal', 'alta', 'critica')),
    CHECK (NOT (es_critica AND desactivable))
);

CREATE TABLE metodo_pago (
    id UUID PRIMARY KEY DEFAULT GEN_RANDOM_UUID(),
    codigo VARCHAR(30) NOT NULL UNIQUE,
    nombre VARCHAR(80) NOT NULL,
    es_combinado BOOLEAN NOT NULL DEFAULT FALSE,
    activo BOOLEAN NOT NULL DEFAULT TRUE
);

-- ============================================================
-- 3. USUARIOS, ROLES, SESIONES E IDENTIDAD
-- ============================================================

CREATE TABLE rol (
    id UUID PRIMARY KEY DEFAULT GEN_RANDOM_UUID(),
    codigo VARCHAR(30) NOT NULL UNIQUE,
    nombre VARCHAR(80) NOT NULL,
    descripcion TEXT,
    es_interno BOOLEAN NOT NULL DEFAULT FALSE,
    activo BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE usuario (
    id UUID PRIMARY KEY DEFAULT GEN_RANDOM_UUID(),
    nombres VARCHAR(120) NOT NULL,
    apellidos VARCHAR(120) NOT NULL,
    correo VARCHAR(180) NOT NULL UNIQUE,
    telefono VARCHAR(25) UNIQUE,
    password_hash TEXT NOT NULL,
    fecha_nacimiento DATE,
    foto_perfil TEXT,
    pais_id UUID REFERENCES pais(id),
    estado VARCHAR(20) NOT NULL DEFAULT 'activo',
    correo_verificado BOOLEAN NOT NULL DEFAULT FALSE,
    telefono_verificado BOOLEAN NOT NULL DEFAULT FALSE,
    identidad_verificada BOOLEAN NOT NULL DEFAULT FALSE,
    calificacion_pasajero_promedio NUMERIC(3,2) NOT NULL DEFAULT 0,
    total_viajes_pasajero INTEGER NOT NULL DEFAULT 0,
    ultimo_acceso_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (estado IN ('activo', 'inactivo', 'bloqueado', 'eliminado')),
    CHECK (calificacion_pasajero_promedio BETWEEN 0 AND 5),
    CHECK (total_viajes_pasajero >= 0)
);

CREATE TABLE usuario_rol (
    id UUID PRIMARY KEY DEFAULT GEN_RANDOM_UUID(),
    usuario_id UUID NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
    rol_id UUID NOT NULL REFERENCES rol(id),
    asignado_por UUID REFERENCES usuario(id),
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (usuario_id, rol_id)
);

CREATE TABLE dispositivo (
    id UUID PRIMARY KEY DEFAULT GEN_RANDOM_UUID(),
    usuario_id UUID NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
    identificador VARCHAR(255) NOT NULL,
    plataforma VARCHAR(30),
    modelo VARCHAR(100),
    so_version VARCHAR(40),
    app_version VARCHAR(30),
    token_push TEXT,
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    ultimo_uso_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (usuario_id, identificador),
    CHECK (plataforma IS NULL OR plataforma IN ('android', 'ios', 'web'))
);

CREATE UNIQUE INDEX uq_dispositivo_token_push
ON dispositivo(token_push)
WHERE token_push IS NOT NULL AND activo = TRUE;

CREATE TABLE usuario_sesion (
    id UUID PRIMARY KEY DEFAULT GEN_RANDOM_UUID(),
    usuario_id UUID NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
    dispositivo_id UUID REFERENCES dispositivo(id),
    refresh_token_hash VARCHAR(128) NOT NULL UNIQUE,
    ip_origen INET,
    user_agent TEXT,
    expira_at TIMESTAMPTZ NOT NULL,
    revocado_at TIMESTAMPTZ,
    motivo_revocacion VARCHAR(80),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (expira_at > created_at)
);

CREATE TABLE codigo_verificacion (
    id UUID PRIMARY KEY DEFAULT GEN_RANDOM_UUID(),
    usuario_id UUID NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
    canal VARCHAR(20) NOT NULL,
    proposito VARCHAR(30) NOT NULL,
    destino VARCHAR(180) NOT NULL,
    codigo_hash VARCHAR(128) NOT NULL,
    intentos SMALLINT NOT NULL DEFAULT 0,
    max_intentos SMALLINT NOT NULL DEFAULT 5,
    expira_at TIMESTAMPTZ NOT NULL,
    usado_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (canal IN ('correo', 'sms', 'whatsapp')),
    CHECK (proposito IN ('verificar_correo', 'verificar_telefono', 'recuperar_password')),
    CHECK (intentos >= 0),
    CHECK (max_intentos > 0),
    CHECK (expira_at > created_at)
);

CREATE TABLE usuario_direccion (
    id UUID PRIMARY KEY DEFAULT GEN_RANDOM_UUID(),
    usuario_id UUID NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
    etiqueta VARCHAR(50),
    direccion TEXT NOT NULL,
    referencia TEXT,
    ubicacion GEOGRAPHY(POINT, 4326),
    es_principal BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX uq_usuario_direccion_principal
ON usuario_direccion(usuario_id)
WHERE es_principal = TRUE;

CREATE TABLE verificacion_identidad (
    id UUID PRIMARY KEY DEFAULT GEN_RANDOM_UUID(),
    usuario_id UUID NOT NULL REFERENCES usuario(id),
    tipo_documento VARCHAR(30) NOT NULL,
    numero_documento VARCHAR(30) NOT NULL,
    pais_emisor_id UUID NOT NULL REFERENCES pais(id),
    proveedor VARCHAR(100),
    referencia_proveedor VARCHAR(180),
    estado VARCHAR(20) NOT NULL DEFAULT 'pendiente',
    motivo_rechazo TEXT,
    verificado_at TIMESTAMPTZ,
    revisado_por UUID REFERENCES usuario(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (estado IN ('pendiente', 'en_revision', 'aprobado', 'rechazado', 'vencido')),
    CHECK (verificado_at IS NULL OR estado = 'aprobado')
);

CREATE UNIQUE INDEX uq_identidad_documento_aprobado
ON verificacion_identidad(pais_emisor_id, tipo_documento, numero_documento)
WHERE estado = 'aprobado';

CREATE UNIQUE INDEX uq_identidad_usuario_aprobado
ON verificacion_identidad(usuario_id)
WHERE estado = 'aprobado';

CREATE TABLE documento_privado (
    id UUID PRIMARY KEY DEFAULT GEN_RANDOM_UUID(),
    usuario_id UUID NOT NULL REFERENCES usuario(id),
    verificacion_identidad_id UUID REFERENCES verificacion_identidad(id),
    tipo_documento VARCHAR(50) NOT NULL,
    archivo_cifrado TEXT NOT NULL,
    nombre_original VARCHAR(255),
    mime_type VARCHAR(100),
    hash_archivo VARCHAR(128),
    estado VARCHAR(20) NOT NULL DEFAULT 'vigente',
    fecha_vencimiento DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (estado IN ('vigente', 'vencido', 'rechazado', 'eliminado'))
);

CREATE TABLE contacto_emergencia (
    id UUID PRIMARY KEY DEFAULT GEN_RANDOM_UUID(),
    usuario_id UUID NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
    nombre VARCHAR(150) NOT NULL,
    parentesco VARCHAR(60),
    telefono VARCHAR(25) NOT NULL,
    es_principal BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX uq_contacto_emergencia_principal
ON contacto_emergencia(usuario_id)
WHERE es_principal = TRUE;

-- ============================================================
-- 4. PERFIL, VEHÍCULOS Y DOCUMENTACIÓN DEL CONDUCTOR
-- ============================================================

CREATE TABLE perfil_conductor (
    id UUID PRIMARY KEY DEFAULT GEN_RANDOM_UUID(),
    usuario_id UUID NOT NULL UNIQUE REFERENCES usuario(id),
    estado VARCHAR(25) NOT NULL DEFAULT 'no_solicitado',
    calificacion_promedio NUMERIC(3,2) NOT NULL DEFAULT 0,
    total_viajes INTEGER NOT NULL DEFAULT 0,
    acepta_nuevas_reservas BOOLEAN NOT NULL DEFAULT FALSE,
    aprobado_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (estado IN ('no_solicitado', 'pendiente', 'en_revision', 'aprobado', 'rechazado', 'suspendido')),
    CHECK (calificacion_promedio BETWEEN 0 AND 5),
    CHECK (total_viajes >= 0)
);

CREATE TABLE vehiculo (
    id UUID PRIMARY KEY DEFAULT GEN_RANDOM_UUID(),
    perfil_conductor_id UUID NOT NULL REFERENCES perfil_conductor(id),
    placa VARCHAR(15) NOT NULL UNIQUE,
    marca VARCHAR(60) NOT NULL,
    modelo VARCHAR(80) NOT NULL,
    anio SMALLINT,
    color VARCHAR(40),
    capacidad_pasajeros SMALLINT NOT NULL,
    tipo VARCHAR(40),
    comodidades JSONB NOT NULL DEFAULT '[]'::jsonb,
    foto TEXT,
    estado VARCHAR(20) NOT NULL DEFAULT 'pendiente',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (anio IS NULL OR anio BETWEEN 1980 AND 2100),
    CHECK (capacidad_pasajeros BETWEEN 1 AND 20),
    CHECK (estado IN ('pendiente', 'aprobado', 'rechazado', 'inactivo'))
);

CREATE TABLE solicitud_conductor (
    id UUID PRIMARY KEY DEFAULT GEN_RANDOM_UUID(),
    usuario_id UUID NOT NULL REFERENCES usuario(id),
    perfil_conductor_id UUID NOT NULL REFERENCES perfil_conductor(id),
    vehiculo_id UUID REFERENCES vehiculo(id),
    estado VARCHAR(25) NOT NULL DEFAULT 'pendiente',
    observaciones TEXT,
    revisado_por UUID REFERENCES usuario(id),
    presentado_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    revisado_at TIMESTAMPTZ,
    CHECK (estado IN ('pendiente', 'en_revision', 'aprobado', 'rechazado'))
);

CREATE TABLE documento_conductor (
    id UUID PRIMARY KEY DEFAULT GEN_RANDOM_UUID(),
    perfil_conductor_id UUID NOT NULL REFERENCES perfil_conductor(id),
    vehiculo_id UUID REFERENCES vehiculo(id),
    solicitud_conductor_id UUID REFERENCES solicitud_conductor(id),
    tipo VARCHAR(40) NOT NULL,
    numero_documento VARCHAR(60),
    archivo_cifrado TEXT NOT NULL,
    fecha_emision DATE,
    fecha_vencimiento DATE,
    estado VARCHAR(20) NOT NULL DEFAULT 'pendiente',
    revisado_por UUID REFERENCES usuario(id),
    revisado_at TIMESTAMPTZ,
    observaciones TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (tipo IN ('licencia', 'soat', 'tarjeta_propiedad', 'otro')),
    CHECK (estado IN ('pendiente', 'aprobado', 'rechazado', 'vencido')),
    CHECK ((tipo IN ('soat', 'tarjeta_propiedad') AND vehiculo_id IS NOT NULL) OR tipo IN ('licencia', 'otro')),
    CHECK (fecha_vencimiento IS NULL OR fecha_emision IS NULL OR fecha_vencimiento >= fecha_emision)
);

-- ============================================================
-- 5. RUTAS, PARADAS OPERATIVAS Y VIAJES
-- ============================================================

CREATE TABLE ruta (
    id UUID PRIMARY KEY DEFAULT GEN_RANDOM_UUID(),
    nombre VARCHAR(150) NOT NULL,
    descripcion TEXT,
    origen_nombre VARCHAR(180) NOT NULL,
    destino_nombre VARCHAR(180) NOT NULL,
    geometria GEOGRAPHY(LINESTRING, 4326),
    distancia_km NUMERIC(10,2),
    activa BOOLEAN NOT NULL DEFAULT TRUE,
    creado_por UUID REFERENCES usuario(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (distancia_km IS NULL OR distancia_km > 0)
);

CREATE TABLE ruta_punto (
    id UUID PRIMARY KEY DEFAULT GEN_RANDOM_UUID(),
    ruta_id UUID NOT NULL REFERENCES ruta(id) ON DELETE CASCADE,
    orden INTEGER NOT NULL,
    nombre VARCHAR(150),
    ubicacion GEOGRAPHY(POINT, 4326) NOT NULL,
    tipo VARCHAR(20) NOT NULL DEFAULT 'referencia',
    fraccion_ruta NUMERIC(8,6),
    distancia_acumulada_km NUMERIC(10,3),
    UNIQUE (ruta_id, orden),
    CHECK (tipo IN ('origen', 'destino', 'parada', 'referencia')),
    CHECK (fraccion_ruta IS NULL OR (fraccion_ruta >= 0 AND fraccion_ruta <= 1)),
    CHECK (distancia_acumulada_km IS NULL OR distancia_acumulada_km >= 0)
);

CREATE TABLE viaje (
    id UUID PRIMARY KEY DEFAULT GEN_RANDOM_UUID(),
    codigo VARCHAR(20) NOT NULL UNIQUE DEFAULT fn_generar_codigo(),
    conductor_id UUID NOT NULL REFERENCES usuario(id),
    vehiculo_id UUID NOT NULL REFERENCES vehiculo(id),
    ruta_id UUID NOT NULL REFERENCES ruta(id),
    moneda_id UUID NOT NULL REFERENCES moneda(id),
    salida_programada_at TIMESTAMPTZ NOT NULL,
    inicio_real_at TIMESTAMPTZ,
    finalizado_at TIMESTAMPTZ,
    cancelado_at TIMESTAMPTZ,
    cancelado_por UUID REFERENCES usuario(id),
    motivo_cancelacion TEXT,
    estado VARCHAR(20) NOT NULL DEFAULT 'publicado',
    acepta_reservas BOOLEAN NOT NULL DEFAULT TRUE,
    aceptacion_automatica BOOLEAN NOT NULL DEFAULT FALSE,
    gps_confiable BOOLEAN NOT NULL DEFAULT FALSE,
    ultima_ubicacion GEOGRAPHY(POINT, 4326),
    ultima_ubicacion_at TIMESTAMPTZ,
    fraccion_avance_ruta NUMERIC(8,6) NOT NULL DEFAULT 0,
    capacidad_total SMALLINT NOT NULL,
    tipo_tarifa VARCHAR(20) NOT NULL DEFAULT 'por_asiento',
    precio_asiento_completo NUMERIC(10,2),
    -- Tarifa definida por el conductor al publicar el viaje.
    tarifa_km NUMERIC(10,2) NOT NULL,
    equipaje_permitido VARCHAR(60),
    reglas_abordo JSONB NOT NULL DEFAULT '[]'::jsonb,
    desvio_maximo_km NUMERIC(8,2) NOT NULL DEFAULT 0,
    distancia_operativa_km NUMERIC(10,2),
    ruta_operativa GEOGRAPHY(LINESTRING, 4326),
    observaciones TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (estado IN ('publicado', 'en_curso', 'completado', 'cancelado')),
    CHECK (capacidad_total > 0),
    CHECK (tipo_tarifa IN ('por_asiento', 'por_km')),
    CHECK (precio_asiento_completo IS NULL OR precio_asiento_completo >= 0),
    CHECK (tarifa_km >= 0),
    CHECK (desvio_maximo_km >= 0),
    CHECK (fraccion_avance_ruta BETWEEN 0 AND 1),
    CHECK (distancia_operativa_km IS NULL OR distancia_operativa_km > 0)
);

CREATE TABLE viaje_parada (
    id UUID PRIMARY KEY DEFAULT GEN_RANDOM_UUID(),
    viaje_id UUID NOT NULL REFERENCES viaje(id) ON DELETE CASCADE,
    orden INTEGER NOT NULL,
    tipo VARCHAR(20) NOT NULL,
    nombre VARCHAR(180),
    ubicacion GEOGRAPHY(POINT, 4326) NOT NULL,
    fraccion_ruta NUMERIC(8,6) NOT NULL,
    distancia_acumulada_km NUMERIC(10,3) NOT NULL DEFAULT 0,
    desvio_km NUMERIC(8,3) NOT NULL DEFAULT 0,
    eta_estimada_at TIMESTAMPTZ,
    llegada_real_at TIMESTAMPTZ,
    estado VARCHAR(20) NOT NULL DEFAULT 'pendiente',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (viaje_id, orden),
    CHECK (orden >= 0),
    CHECK (tipo IN ('origen', 'destino', 'parada_base', 'recogida', 'descenso')),
    CHECK (fraccion_ruta BETWEEN 0 AND 1),
    CHECK (distancia_acumulada_km >= 0),
    CHECK (desvio_km >= 0),
    CHECK (estado IN ('pendiente', 'en_camino', 'completada', 'omitida'))
);

CREATE TABLE viaje_segmento (
    id UUID PRIMARY KEY DEFAULT GEN_RANDOM_UUID(),
    viaje_id UUID NOT NULL REFERENCES viaje(id) ON DELETE CASCADE,
    orden INTEGER NOT NULL,
    origen GEOGRAPHY(POINT, 4326) NOT NULL,
    destino GEOGRAPHY(POINT, 4326) NOT NULL,
    fraccion_inicio NUMERIC(8,6),
    fraccion_fin NUMERIC(8,6),
    distancia_km NUMERIC(10,3) NOT NULL,
    capacidad SMALLINT NOT NULL,
    precio_por_asiento NUMERIC(10,2) NOT NULL,
    UNIQUE (viaje_id, orden),
    CHECK (distancia_km > 0),
    CHECK (capacidad > 0),
    CHECK (precio_por_asiento >= 0),
    CHECK (
        fraccion_inicio IS NULL
        OR fraccion_fin IS NULL
        OR (fraccion_inicio >= 0 AND fraccion_fin <= 1 AND fraccion_fin > fraccion_inicio)
    )
);

CREATE INDEX idx_viaje_conductor_estado ON viaje(conductor_id, estado);
CREATE INDEX idx_viaje_salida ON viaje(salida_programada_at);
CREATE INDEX idx_viaje_ruta ON viaje(ruta_id);
CREATE INDEX idx_viaje_parada_viaje_fraccion ON viaje_parada(viaje_id, fraccion_ruta);
CREATE INDEX idx_viaje_segmento_viaje ON viaje_segmento(viaje_id);

CREATE UNIQUE INDEX uq_conductor_viaje_activo
ON viaje(conductor_id)
WHERE estado IN ('publicado', 'en_curso');

CREATE OR REPLACE FUNCTION fn_validar_viaje()
RETURNS TRIGGER AS $$
DECLARE
    v_perfil_id UUID;
    v_perfil_estado VARCHAR(25);
    v_vehiculo_perfil_id UUID;
    v_vehiculo_estado VARCHAR(20);
    v_vehiculo_capacidad SMALLINT;
BEGIN
    SELECT id, estado
    INTO v_perfil_id, v_perfil_estado
    FROM perfil_conductor
    WHERE usuario_id = NEW.conductor_id;

    IF v_perfil_id IS NULL THEN
        RAISE EXCEPTION 'El usuario no tiene perfil de conductor';
    END IF;

    IF TG_OP = 'INSERT' AND v_perfil_estado <> 'aprobado' THEN
        RAISE EXCEPTION 'El perfil del conductor no está aprobado para publicar viajes';
    END IF;

    SELECT perfil_conductor_id, estado, capacidad_pasajeros
    INTO v_vehiculo_perfil_id, v_vehiculo_estado, v_vehiculo_capacidad
    FROM vehiculo
    WHERE id = NEW.vehiculo_id;

    IF v_vehiculo_perfil_id IS NULL OR v_vehiculo_perfil_id <> v_perfil_id THEN
        RAISE EXCEPTION 'El vehículo no pertenece al conductor indicado';
    END IF;

    IF v_vehiculo_estado <> 'aprobado' THEN
        RAISE EXCEPTION 'El vehículo seleccionado no está aprobado';
    END IF;

    IF NEW.capacidad_total > v_vehiculo_capacidad THEN
        RAISE EXCEPTION 'La capacidad del viaje (%) supera la capacidad del vehículo (%)', NEW.capacidad_total, v_vehiculo_capacidad;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_validar_viaje
BEFORE INSERT OR UPDATE OF conductor_id, vehiculo_id, capacidad_total
ON viaje
FOR EACH ROW
EXECUTE FUNCTION fn_validar_viaje();

CREATE OR REPLACE FUNCTION fn_validar_viaje_segmento()
RETURNS TRIGGER AS $$
DECLARE
    v_capacidad_viaje SMALLINT;
BEGIN
    SELECT capacidad_total INTO v_capacidad_viaje
    FROM viaje
    WHERE id = NEW.viaje_id;

    IF v_capacidad_viaje IS NULL THEN
        RAISE EXCEPTION 'El viaje del segmento no existe';
    END IF;

    IF NEW.capacidad > v_capacidad_viaje THEN
        RAISE EXCEPTION 'La capacidad del segmento (%) supera la capacidad total del viaje (%)', NEW.capacidad, v_capacidad_viaje;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_validar_viaje_segmento
BEFORE INSERT OR UPDATE OF viaje_id, capacidad
ON viaje_segmento
FOR EACH ROW
EXECUTE FUNCTION fn_validar_viaje_segmento();

-- ============================================================
-- 6. SOLICITUDES Y RESERVAS
-- ============================================================

CREATE TABLE solicitud_reserva (
    id UUID PRIMARY KEY DEFAULT GEN_RANDOM_UUID(),
    viaje_id UUID NOT NULL REFERENCES viaje(id),
    pasajero_id UUID NOT NULL REFERENCES usuario(id),
    punto_recogida GEOGRAPHY(POINT, 4326) NOT NULL,
    punto_destino GEOGRAPHY(POINT, 4326) NOT NULL,
    fraccion_recogida NUMERIC(8,6),
    fraccion_destino NUMERIC(8,6),
    nombre_recogida VARCHAR(180),
    nombre_destino VARCHAR(180),
    asientos_solicitados SMALLINT NOT NULL DEFAULT 1,
    estado VARCHAR(20) NOT NULL DEFAULT 'pendiente',
    intentos_consumidos SMALLINT NOT NULL DEFAULT 0,
    modo_aceptacion VARCHAR(20),
    respuesta_at TIMESTAMPTZ,
    expira_at TIMESTAMPTZ,
    motivo_respuesta TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (viaje_id, pasajero_id),
    CHECK (asientos_solicitados > 0),
    CHECK (intentos_consumidos BETWEEN 0 AND 3),
    CHECK (estado IN ('pendiente', 'aceptada', 'rechazada', 'expirada', 'cancelada')),
    CHECK (modo_aceptacion IS NULL OR modo_aceptacion IN ('manual', 'automatica')),
    CHECK (
        fraccion_recogida IS NULL
        OR fraccion_destino IS NULL
        OR (fraccion_recogida >= 0 AND fraccion_destino <= 1 AND fraccion_destino > fraccion_recogida)
    )
);

CREATE INDEX idx_solicitud_pasajero ON solicitud_reserva(pasajero_id, estado);
CREATE INDEX idx_solicitud_viaje ON solicitud_reserva(viaje_id, estado);

CREATE TABLE solicitud_intento (
    id UUID PRIMARY KEY DEFAULT GEN_RANDOM_UUID(),
    solicitud_id UUID NOT NULL REFERENCES solicitud_reserva(id) ON DELETE CASCADE,
    secuencia SMALLINT NOT NULL,
    cuenta_para_limite BOOLEAN NOT NULL DEFAULT TRUE,
    modo_aceptacion VARCHAR(20),
    resultado VARCHAR(20) NOT NULL,
    motivo TEXT,
    cancelacion_voluntaria_previa BOOLEAN NOT NULL DEFAULT FALSE,
    expira_at TIMESTAMPTZ,
    iniciado_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    finalizado_at TIMESTAMPTZ,
    UNIQUE (solicitud_id, secuencia),
    CHECK (secuencia > 0),
    CHECK (modo_aceptacion IS NULL OR modo_aceptacion IN ('manual', 'automatica')),
    CHECK (resultado IN ('pendiente', 'aceptada', 'rechazada', 'expirada', 'cancelada'))
);

CREATE TABLE reserva (
    id UUID PRIMARY KEY DEFAULT GEN_RANDOM_UUID(),
    codigo VARCHAR(20) NOT NULL UNIQUE DEFAULT fn_generar_codigo(),
    viaje_id UUID NOT NULL REFERENCES viaje(id),
    pasajero_id UUID NOT NULL REFERENCES usuario(id),
    solicitud_id UUID REFERENCES solicitud_reserva(id),
    solicitud_intento_id UUID REFERENCES solicitud_intento(id),
    parada_recogida_id UUID REFERENCES viaje_parada(id),
    parada_destino_id UUID REFERENCES viaje_parada(id),
    fraccion_recogida NUMERIC(8,6) NOT NULL,
    fraccion_destino NUMERIC(8,6) NOT NULL,
    distancia_reservada_km NUMERIC(10,3) NOT NULL,
    distancia_real_km NUMERIC(10,3),
    asientos_reservados SMALLINT NOT NULL,
    estado VARCHAR(25) NOT NULL DEFAULT 'esperando_pasajero',
    precio_original NUMERIC(12,2) NOT NULL DEFAULT 0,
    precio_total NUMERIC(12,2) NOT NULL DEFAULT 0,
    moneda_id UUID NOT NULL REFERENCES moneda(id),
    conductor_acepto_at TIMESTAMPTZ,
    pasajero_confirmo_at TIMESTAMPTZ,
    hold_expira_at TIMESTAMPTZ,
    conductor_llego_recogida_at TIMESTAMPTZ,
    espera_extendida_hasta_at TIMESTAMPTZ,
    recogido_at TIMESTAMPTZ,
    descendido_at TIMESTAMPTZ,
    finalizacion_anticipada BOOLEAN NOT NULL DEFAULT FALSE,
    punto_recogida GEOGRAPHY(POINT, 4326) NOT NULL,
    punto_destino GEOGRAPHY(POINT, 4326) NOT NULL,
    punto_descenso_real GEOGRAPHY(POINT, 4326),
    nombre_recogida VARCHAR(180),
    nombre_destino VARCHAR(180),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (asientos_reservados > 0),
    CHECK (fraccion_recogida >= 0 AND fraccion_recogida < 1),
    CHECK (fraccion_destino > 0 AND fraccion_destino <= 1),
    CHECK (fraccion_destino > fraccion_recogida),
    CHECK (distancia_reservada_km > 0),
    CHECK (distancia_real_km IS NULL OR distancia_real_km >= 0),
    CHECK (precio_original >= 0),
    CHECK (precio_total >= 0),
    CHECK (estado IN (
        'esperando_pasajero', 'confirmada', 'en_curso',
        'completada', 'cancelada', 'no_show', 'expirada'
    ))
);

CREATE TABLE reserva_segmento (
    id UUID PRIMARY KEY DEFAULT GEN_RANDOM_UUID(),
    reserva_id UUID NOT NULL REFERENCES reserva(id) ON DELETE CASCADE,
    segmento_id UUID NOT NULL REFERENCES viaje_segmento(id),
    asientos SMALLINT NOT NULL,
    precio_unitario NUMERIC(10,2) NOT NULL,
    UNIQUE (reserva_id, segmento_id),
    CHECK (asientos > 0),
    CHECK (precio_unitario >= 0)
);

CREATE TABLE reserva_evento (
    id UUID PRIMARY KEY DEFAULT GEN_RANDOM_UUID(),
    reserva_id UUID NOT NULL REFERENCES reserva(id),
    actor_id UUID REFERENCES usuario(id),
    tipo VARCHAR(50) NOT NULL,
    estado_anterior VARCHAR(30),
    estado_nuevo VARCHAR(30),
    descripcion TEXT,
    datos JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE reserva_modificacion (
    id UUID PRIMARY KEY DEFAULT GEN_RANDOM_UUID(),
    reserva_id UUID NOT NULL REFERENCES reserva(id),
    usuario_id UUID NOT NULL REFERENCES usuario(id),
    tipo VARCHAR(30) NOT NULL,
    valor_anterior JSONB NOT NULL,
    valor_nuevo JSONB NOT NULL,
    motivo TEXT,
    estado VARCHAR(20) NOT NULL DEFAULT 'pendiente',
    requiere_aprobacion_conductor BOOLEAN NOT NULL DEFAULT FALSE,
    requiere_aprobacion_pasajero BOOLEAN NOT NULL DEFAULT FALSE,
    diferencia_precio NUMERIC(12,2) NOT NULL DEFAULT 0,
    aceptado_pasajero_at TIMESTAMPTZ,
    aprobado_por UUID REFERENCES usuario(id),
    aprobado_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (tipo IN ('recogida', 'destino', 'asientos', 'finalizacion_anticipada')),
    CHECK (estado IN ('pendiente', 'aprobada', 'rechazada', 'cancelada'))
);

CREATE UNIQUE INDEX uq_reserva_cambio_recogida_unico
ON reserva_modificacion(reserva_id)
WHERE tipo = 'recogida' AND estado = 'aprobada';

CREATE UNIQUE INDEX uq_reserva_cambio_destino_unico
ON reserva_modificacion(reserva_id)
WHERE tipo = 'destino' AND estado = 'aprobada';

CREATE TABLE reserva_cancelacion (
    id UUID PRIMARY KEY DEFAULT GEN_RANDOM_UUID(),
    reserva_id UUID NOT NULL UNIQUE REFERENCES reserva(id),
    actor_id UUID NOT NULL REFERENCES usuario(id),
    actor_tipo VARCHAR(20) NOT NULL,
    motivo VARCHAR(100) NOT NULL,
    descripcion TEXT,
    distancia_conductor_km NUMERIC(8,2),
    porcentaje_reembolso NUMERIC(5,2) NOT NULL DEFAULT 0,
    importe_reembolso NUMERIC(12,2) NOT NULL DEFAULT 0,
    justificacion TEXT,
    estado_revision VARCHAR(20) NOT NULL DEFAULT 'no_requerida',
    revisado_por UUID REFERENCES usuario(id),
    revisado_at TIMESTAMPTZ,
    resolucion TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (actor_tipo IN ('pasajero', 'conductor', 'plataforma')),
    CHECK (porcentaje_reembolso BETWEEN 0 AND 100),
    CHECK (importe_reembolso >= 0),
    CHECK (distancia_conductor_km IS NULL OR distancia_conductor_km >= 0),
    CHECK (estado_revision IN ('no_requerida', 'pendiente', 'en_revision', 'aprobada', 'rechazada'))
);

CREATE TABLE reserva_no_show (
    id UUID PRIMARY KEY DEFAULT GEN_RANDOM_UUID(),
    reserva_id UUID NOT NULL UNIQUE REFERENCES reserva(id),
    llegada_conductor_at TIMESTAMPTZ,
    inicio_espera_at TIMESTAMPTZ,
    extension_hasta_at TIMESTAMPTZ,
    vencimiento_espera_at TIMESTAMPTZ,
    marcado_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    marcado_por UUID NOT NULL REFERENCES usuario(id),
    observaciones TEXT
);

-- Un pasajero solo puede tener una reserva confirmada/en_curso activa, pero sí retenciones simultáneas antes de confirmar.
CREATE UNIQUE INDEX uq_pasajero_reserva_activa
ON reserva(pasajero_id)
WHERE estado IN ('confirmada', 'en_curso');

CREATE UNIQUE INDEX uq_reserva_viaje_pasajero_activa
ON reserva(viaje_id, pasajero_id)
WHERE estado IN ('esperando_pasajero', 'confirmada', 'en_curso');

CREATE INDEX idx_reserva_viaje_estado ON reserva(viaje_id, estado);
CREATE INDEX idx_reserva_pasajero_estado ON reserva(pasajero_id, estado);

-- Validación transaccional de capacidad por superposición de tramos en el recorrido del viaje.
CREATE OR REPLACE FUNCTION fn_validar_capacidad_reserva()
RETURNS TRIGGER AS $$
DECLARE
    v_capacidad_total INTEGER;
    v_viaje_estado VARCHAR(20);
    v_pico_otros INTEGER;
BEGIN
    IF NEW.estado NOT IN ('esperando_pasajero', 'confirmada', 'en_curso') THEN
        RETURN NEW;
    END IF;

    SELECT capacidad_total, estado
    INTO v_capacidad_total, v_viaje_estado
    FROM viaje
    WHERE id = NEW.viaje_id
    FOR UPDATE;

    IF v_capacidad_total IS NULL THEN
        RAISE EXCEPTION 'El viaje de la reserva no existe';
    END IF;

    IF v_viaje_estado NOT IN ('publicado', 'en_curso') THEN
        RAISE EXCEPTION 'El viaje no admite reservas activas en estado %', v_viaje_estado;
    END IF;

    WITH puntos_evaluacion AS (
        SELECT NEW.fraccion_recogida AS punto
        UNION
        SELECT r.fraccion_recogida AS punto
        FROM reserva r
        WHERE r.viaje_id = NEW.viaje_id
          AND r.id IS DISTINCT FROM NEW.id
          AND r.estado IN ('esperando_pasajero', 'confirmada', 'en_curso')
          AND r.fraccion_recogida >= NEW.fraccion_recogida
          AND r.fraccion_recogida < NEW.fraccion_destino
    )
    SELECT COALESCE(MAX(ocupacion), 0)
    INTO v_pico_otros
    FROM (
        SELECT COALESCE(SUM(r2.asientos_reservados), 0) AS ocupacion
        FROM puntos_evaluacion p
        LEFT JOIN reserva r2
          ON r2.viaje_id = NEW.viaje_id
         AND r2.id IS DISTINCT FROM NEW.id
         AND r2.estado IN ('esperando_pasajero', 'confirmada', 'en_curso')
         AND r2.fraccion_recogida <= p.punto
         AND r2.fraccion_destino > p.punto
        GROUP BY p.punto
    ) t;

    IF v_pico_otros + NEW.asientos_reservados > v_capacidad_total THEN
        RAISE EXCEPTION 'Capacidad insuficiente en el tramo solicitado (ocupados: %, solicitados: %, capacidad: %)',
            v_pico_otros, NEW.asientos_reservados, v_capacidad_total;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_validar_capacidad_reserva
BEFORE INSERT OR UPDATE OF estado, asientos_reservados, fraccion_recogida, fraccion_destino, viaje_id
ON reserva
FOR EACH ROW
EXECUTE FUNCTION fn_validar_capacidad_reserva();

-- Validación adicional cuando se asignan segmentos precalculados en reserva_segmento.
CREATE OR REPLACE FUNCTION fn_validar_capacidad_segmento()
RETURNS TRIGGER AS $$
DECLARE
    v_seg_viaje_id UUID;
    v_capacidad INTEGER;
    v_res_viaje_id UUID;
    v_res_asientos SMALLINT;
    v_estado VARCHAR(25);
    v_ocupados INTEGER;
BEGIN
    SELECT vs.viaje_id, vs.capacidad
    INTO v_seg_viaje_id, v_capacidad
    FROM viaje_segmento vs
    WHERE vs.id = NEW.segmento_id;

    IF v_seg_viaje_id IS NULL THEN
        RAISE EXCEPTION 'El segmento indicado no existe';
    END IF;

    SELECT r.viaje_id, r.asientos_reservados, r.estado
    INTO v_res_viaje_id, v_res_asientos, v_estado
    FROM reserva r
    WHERE r.id = NEW.reserva_id;

    IF v_res_viaje_id IS NULL THEN
        RAISE EXCEPTION 'La reserva indicada no existe';
    END IF;

    IF v_seg_viaje_id <> v_res_viaje_id THEN
        RAISE EXCEPTION 'El segmento no pertenece al mismo viaje de la reserva';
    END IF;

    IF NEW.asientos <> v_res_asientos THEN
        RAISE EXCEPTION 'Los asientos del segmento (%) deben coincidir con los de la reserva (%)', NEW.asientos, v_res_asientos;
    END IF;

    PERFORM 1 FROM viaje WHERE id = v_seg_viaje_id FOR UPDATE;

    IF v_estado NOT IN ('esperando_pasajero', 'confirmada', 'en_curso') THEN
        RETURN NEW;
    END IF;

    SELECT COALESCE(SUM(rs.asientos), 0)
    INTO v_ocupados
    FROM reserva_segmento rs
    JOIN reserva r ON r.id = rs.reserva_id
    WHERE rs.segmento_id = NEW.segmento_id
      AND r.estado IN ('esperando_pasajero', 'confirmada', 'en_curso')
      AND r.id <> NEW.reserva_id;

    IF v_ocupados + NEW.asientos > v_capacidad THEN
        RAISE EXCEPTION 'Capacidad insuficiente en el tramo solicitado';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_validar_capacidad_segmento
BEFORE INSERT OR UPDATE OF asientos, segmento_id, reserva_id
ON reserva_segmento
FOR EACH ROW
EXECUTE FUNCTION fn_validar_capacidad_segmento();

-- ============================================================
-- 7. PAGOS, INTENTOS Y REEMBOLSOS
-- ============================================================

CREATE TABLE pago (
    id UUID PRIMARY KEY DEFAULT GEN_RANDOM_UUID(),
    reserva_id UUID NOT NULL REFERENCES reserva(id),
    reserva_modificacion_id UUID REFERENCES reserva_modificacion(id),
    pasajero_id UUID NOT NULL REFERENCES usuario(id),
    moneda_id UUID NOT NULL REFERENCES moneda(id),
    tipo_concepto VARCHAR(30) NOT NULL DEFAULT 'reserva_inicial',
    metodo_original VARCHAR(30) NOT NULL REFERENCES metodo_pago(codigo),
    metodo_final VARCHAR(30) REFERENCES metodo_pago(codigo),
    monto_bruto NUMERIC(12,2) NOT NULL DEFAULT 0,
    monto_descuento NUMERIC(12,2) NOT NULL DEFAULT 0,
    monto_total NUMERIC(12,2) NOT NULL,
    monto_billetera NUMERIC(12,2) NOT NULL DEFAULT 0,
    monto_digital NUMERIC(12,2) NOT NULL DEFAULT 0,
    monto_efectivo NUMERIC(12,2) NOT NULL DEFAULT 0,
    estado VARCHAR(25) NOT NULL DEFAULT 'pendiente',
    confirmado_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (tipo_concepto IN ('reserva_inicial', 'asientos_adicionales', 'ajuste_tarifa', 'propina')),
    CHECK (metodo_original IN ('digital', 'efectivo', 'billetera', 'mixto')),
    CHECK (metodo_final IS NULL OR metodo_final IN ('digital', 'efectivo', 'billetera', 'mixto')),
    CHECK (estado IN ('pendiente', 'procesando', 'pagado', 'fallido', 'reembolsado', 'parcialmente_reembolsado')),
    CHECK (monto_bruto >= 0 AND monto_descuento >= 0 AND monto_total >= 0),
    CHECK (monto_bruto = monto_total + monto_descuento),
    CHECK (monto_billetera >= 0 AND monto_digital >= 0 AND monto_efectivo >= 0),
    CHECK (monto_total = monto_billetera + monto_digital + monto_efectivo)
);

CREATE TABLE pago_intento (
    id UUID PRIMARY KEY DEFAULT GEN_RANDOM_UUID(),
    pago_id UUID NOT NULL REFERENCES pago(id),
    numero_intento SMALLINT NOT NULL,
    metodo VARCHAR(30) NOT NULL REFERENCES metodo_pago(codigo),
    proveedor VARCHAR(80),
    referencia_externa VARCHAR(180),
    client_event_id UUID UNIQUE,
    monto NUMERIC(12,2) NOT NULL,
    estado VARCHAR(20) NOT NULL,
    motivo_fallo TEXT,
    iniciado_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    finalizado_at TIMESTAMPTZ,
    autorizado_por UUID REFERENCES usuario(id),
    UNIQUE (pago_id, numero_intento),
    CHECK (numero_intento > 0),
    CHECK (monto > 0),
    CHECK (metodo IN ('digital', 'efectivo', 'billetera')),
    CHECK (estado IN ('pendiente', 'procesando', 'exitoso', 'fallido', 'cancelado'))
);

CREATE TABLE reembolso (
    id UUID PRIMARY KEY DEFAULT GEN_RANDOM_UUID(),
    pago_id UUID NOT NULL REFERENCES pago(id),
    reserva_id UUID NOT NULL REFERENCES reserva(id),
    reserva_modificacion_id UUID REFERENCES reserva_modificacion(id),
    moneda_id UUID NOT NULL REFERENCES moneda(id),
    monto NUMERIC(12,2) NOT NULL,
    destino VARCHAR(25) NOT NULL,
    estado VARCHAR(20) NOT NULL DEFAULT 'pendiente',
    motivo TEXT NOT NULL,
    referencia_externa VARCHAR(180),
    disponible_at TIMESTAMPTZ,
    procesado_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (monto >= 0),
    CHECK (destino IN ('billetera', 'medio_original', 'efectivo')),
    CHECK (estado IN ('pendiente', 'programado', 'procesando', 'completado', 'fallido', 'cancelado'))
);

-- ============================================================
-- 8. BILLETERAS, RECARGAS, RETIROS Y COMISIONES
-- ============================================================

CREATE TABLE billetera (
    id UUID PRIMARY KEY DEFAULT GEN_RANDOM_UUID(),
    usuario_id UUID NOT NULL REFERENCES usuario(id),
    tipo VARCHAR(25) NOT NULL,
    moneda_id UUID NOT NULL REFERENCES moneda(id),
    saldo NUMERIC(14,2) NOT NULL DEFAULT 0,
    permite_saldo_negativo BOOLEAN NOT NULL DEFAULT FALSE,
    activa BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (usuario_id, tipo, moneda_id),
    CHECK (tipo IN ('pasajero', 'ganancias', 'comisiones')),
    CHECK (permite_saldo_negativo OR saldo >= 0),
    CHECK (NOT (tipo IN ('pasajero', 'ganancias') AND permite_saldo_negativo))
);

CREATE TABLE movimiento_billetera (
    id UUID PRIMARY KEY DEFAULT GEN_RANDOM_UUID(),
    billetera_id UUID NOT NULL REFERENCES billetera(id),
    tipo VARCHAR(30) NOT NULL,
    direccion VARCHAR(10) NOT NULL,
    monto NUMERIC(14,2) NOT NULL,
    saldo_anterior NUMERIC(14,2) NOT NULL,
    saldo_nuevo NUMERIC(14,2) NOT NULL,
    referencia_tipo VARCHAR(40),
    referencia_id UUID,
    descripcion TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (direccion IN ('entrada', 'salida')),
    CHECK (monto > 0),
    CHECK (
        (direccion = 'entrada' AND saldo_nuevo = saldo_anterior + monto)
        OR (direccion = 'salida' AND saldo_nuevo = saldo_anterior - monto)
    ),
    CHECK (tipo IN (
        'pago_viaje', 'ganancia_viaje', 'comision',
        'recarga', 'reembolso', 'ajuste', 'retiro',
        'promocion', 'reduccion_tarifa', 'propina'
    ))
);

CREATE TABLE recarga_billetera (
    id UUID PRIMARY KEY DEFAULT GEN_RANDOM_UUID(),
    billetera_id UUID NOT NULL REFERENCES billetera(id),
    usuario_id UUID NOT NULL REFERENCES usuario(id),
    moneda_id UUID NOT NULL REFERENCES moneda(id),
    monto NUMERIC(14,2) NOT NULL,
    proveedor VARCHAR(80),
    referencia_externa VARCHAR(180),
    estado VARCHAR(20) NOT NULL DEFAULT 'pendiente',
    movimiento_billetera_id UUID REFERENCES movimiento_billetera(id),
    client_event_id UUID UNIQUE,
    solicitado_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completado_at TIMESTAMPTZ,
    CHECK (monto > 0),
    CHECK (estado IN ('pendiente', 'procesando', 'completado', 'fallido', 'cancelado'))
);

CREATE OR REPLACE FUNCTION fn_validar_recarga_billetera()
RETURNS TRIGGER AS $$
DECLARE
    v_usuario_id UUID;
    v_tipo VARCHAR(25);
    v_moneda_id UUID;
BEGIN
    SELECT usuario_id, tipo, moneda_id
    INTO v_usuario_id, v_tipo, v_moneda_id
    FROM billetera
    WHERE id = NEW.billetera_id;

    IF v_usuario_id IS NULL THEN
        RAISE EXCEPTION 'La billetera indicada no existe';
    END IF;

    IF v_usuario_id <> NEW.usuario_id THEN
        RAISE EXCEPTION 'La billetera no pertenece al usuario solicitante';
    END IF;

    IF v_tipo = 'ganancias' THEN
        RAISE EXCEPTION 'La billetera de ganancias no admite recargas';
    END IF;

    IF v_moneda_id <> NEW.moneda_id THEN
        RAISE EXCEPTION 'La moneda de la recarga no coincide con la de la billetera';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_validar_recarga_billetera
BEFORE INSERT ON recarga_billetera
FOR EACH ROW
EXECUTE FUNCTION fn_validar_recarga_billetera();

CREATE TABLE retiro (
    id UUID PRIMARY KEY DEFAULT GEN_RANDOM_UUID(),
    billetera_id UUID NOT NULL REFERENCES billetera(id),
    usuario_id UUID NOT NULL REFERENCES usuario(id),
    monto NUMERIC(14,2) NOT NULL,
    moneda_id UUID NOT NULL REFERENCES moneda(id),
    metodo_retiro VARCHAR(30) NOT NULL DEFAULT 'transferencia_bancaria',
    entidad_financiera VARCHAR(100),
    cuenta_enmascarada VARCHAR(100),
    referencia_externa VARCHAR(180),
    estado VARCHAR(20) NOT NULL DEFAULT 'pendiente',
    motivo_rechazo TEXT,
    revisado_por UUID REFERENCES usuario(id),
    movimiento_billetera_id UUID REFERENCES movimiento_billetera(id),
    solicitado_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    procesado_at TIMESTAMPTZ,
    CHECK (monto > 0),
    CHECK (metodo_retiro IN ('transferencia_bancaria', 'yape', 'plin', 'cci')),
    CHECK (estado IN ('pendiente', 'procesando', 'completado', 'rechazado', 'cancelado'))
);

CREATE OR REPLACE FUNCTION fn_validar_retiro_billetera()
RETURNS TRIGGER AS $$
DECLARE
    v_usuario_id UUID;
    v_tipo VARCHAR(25);
    v_moneda_id UUID;
BEGIN
    SELECT usuario_id, tipo, moneda_id
    INTO v_usuario_id, v_tipo, v_moneda_id
    FROM billetera
    WHERE id = NEW.billetera_id;

    IF v_usuario_id IS NULL THEN
        RAISE EXCEPTION 'La billetera indicada no existe';
    END IF;

    IF v_usuario_id <> NEW.usuario_id THEN
        RAISE EXCEPTION 'La billetera no pertenece al usuario solicitante';
    END IF;

    IF v_tipo <> 'ganancias' THEN
        RAISE EXCEPTION 'Solo se permiten retiros desde la billetera de ganancias';
    END IF;

    IF v_moneda_id <> NEW.moneda_id THEN
        RAISE EXCEPTION 'La moneda del retiro no coincide con la de la billetera';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_validar_retiro_billetera
BEFORE INSERT ON retiro
FOR EACH ROW
EXECUTE FUNCTION fn_validar_retiro_billetera();

CREATE TABLE tarifa_comision (
    id UUID PRIMARY KEY DEFAULT GEN_RANDOM_UUID(),
    porcentaje NUMERIC(5,2) NOT NULL,
    vigente_desde TIMESTAMPTZ NOT NULL,
    vigente_hasta TIMESTAMPTZ,
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    CHECK (porcentaje BETWEEN 0 AND 100),
    CHECK (vigente_hasta IS NULL OR vigente_hasta > vigente_desde)
);

CREATE TABLE comision_viaje (
    id UUID PRIMARY KEY DEFAULT GEN_RANDOM_UUID(),
    viaje_id UUID NOT NULL REFERENCES viaje(id),
    reserva_id UUID NOT NULL REFERENCES reserva(id),
    pago_id UUID NOT NULL REFERENCES pago(id),
    conductor_id UUID NOT NULL REFERENCES usuario(id),
    porcentaje NUMERIC(5,2) NOT NULL,
    monto_base NUMERIC(12,2) NOT NULL,
    monto_comision NUMERIC(12,2) NOT NULL,
    monto_descontado_digital NUMERIC(12,2) NOT NULL DEFAULT 0,
    monto_descontado_billetera_comision NUMERIC(12,2) NOT NULL DEFAULT 0,
    origen_pago VARCHAR(20) NOT NULL,
    estado VARCHAR(20) NOT NULL DEFAULT 'pendiente',
    movimiento_billetera_id UUID REFERENCES movimiento_billetera(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (porcentaje BETWEEN 0 AND 100),
    CHECK (monto_base >= 0 AND monto_comision >= 0),
    CHECK (monto_descontado_digital >= 0 AND monto_descontado_billetera_comision >= 0),
    CHECK (monto_comision = monto_descontado_digital + monto_descontado_billetera_comision),
    CHECK (origen_pago IN ('digital', 'efectivo', 'billetera', 'mixto')),
    CHECK (estado IN ('pendiente', 'pagada', 'fallida', 'anulada'))
);

-- ============================================================
-- 9. PROMOCIONES
-- ============================================================

CREATE TABLE promocion (
    id UUID PRIMARY KEY DEFAULT GEN_RANDOM_UUID(),
    codigo VARCHAR(40) UNIQUE,
    nombre VARCHAR(120) NOT NULL,
    descripcion TEXT,
    tipo VARCHAR(25) NOT NULL,
    valor NUMERIC(12,2) NOT NULL,
    moneda_id UUID REFERENCES moneda(id),
    monto_maximo NUMERIC(12,2),
    usos_maximos INTEGER,
    usos_maximos_por_usuario INTEGER DEFAULT 1,
    usos_actuales INTEGER NOT NULL DEFAULT 0,
    vigente_desde TIMESTAMPTZ NOT NULL,
    vigente_hasta TIMESTAMPTZ,
    activa BOOLEAN NOT NULL DEFAULT TRUE,
    CHECK (tipo IN ('porcentaje', 'monto_fijo')),
    CHECK (valor > 0),
    CHECK (tipo <> 'porcentaje' OR valor <= 100),
    CHECK (tipo <> 'monto_fijo' OR moneda_id IS NOT NULL),
    CHECK (monto_maximo IS NULL OR monto_maximo > 0),
    CHECK (usos_maximos IS NULL OR usos_maximos > 0),
    CHECK (usos_maximos_por_usuario IS NULL OR usos_maximos_por_usuario > 0),
    CHECK (usos_actuales >= 0 AND (usos_maximos IS NULL OR usos_actuales <= usos_maximos)),
    CHECK (vigente_hasta IS NULL OR vigente_hasta > vigente_desde)
);

CREATE TABLE promocion_aplicacion (
    id UUID PRIMARY KEY DEFAULT GEN_RANDOM_UUID(),
    promocion_id UUID NOT NULL REFERENCES promocion(id),
    reserva_id UUID NOT NULL REFERENCES reserva(id),
    usuario_id UUID NOT NULL REFERENCES usuario(id),
    descuento_total NUMERIC(12,2) NOT NULL,
    aporte_plataforma NUMERIC(12,2) NOT NULL DEFAULT 0,
    aporte_conductor NUMERIC(12,2) NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (promocion_id, reserva_id),
    CHECK (descuento_total >= 0),
    CHECK (aporte_plataforma >= 0 AND aporte_conductor >= 0),
    CHECK (descuento_total = aporte_plataforma + aporte_conductor)
);

-- ============================================================
-- 10. GPS Y SEGUIMIENTO
-- ============================================================

CREATE TABLE viaje_ubicacion_gps (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    viaje_id UUID NOT NULL REFERENCES viaje(id),
    conductor_id UUID NOT NULL REFERENCES usuario(id),
    client_event_id UUID UNIQUE,
    ubicacion GEOGRAPHY(POINT, 4326) NOT NULL,
    precision_metros NUMERIC(8,2),
    velocidad_kmh NUMERIC(8,2),
    rumbo NUMERIC(6,2),
    nivel_bateria SMALLINT,
    es_sincronizacion_offline BOOLEAN NOT NULL DEFAULT FALSE,
    registrado_at TIMESTAMPTZ NOT NULL,
    recibido_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (precision_metros IS NULL OR precision_metros >= 0),
    CHECK (velocidad_kmh IS NULL OR velocidad_kmh >= 0),
    CHECK (rumbo IS NULL OR (rumbo >= 0 AND rumbo <= 360)),
    CHECK (nivel_bateria IS NULL OR nivel_bateria BETWEEN 0 AND 100)
);

CREATE INDEX idx_gps_viaje_fecha
ON viaje_ubicacion_gps(viaje_id, registrado_at DESC);

CREATE TABLE enlace_seguimiento (
    id UUID PRIMARY KEY DEFAULT GEN_RANDOM_UUID(),
    viaje_id UUID NOT NULL REFERENCES viaje(id),
    reserva_id UUID REFERENCES reserva(id),
    creado_por UUID NOT NULL REFERENCES usuario(id),
    token_hash VARCHAR(128) NOT NULL UNIQUE,
    tipo_creador VARCHAR(20) NOT NULL,
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    expira_at TIMESTAMPTZ NOT NULL,
    revocado_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (tipo_creador IN ('conductor', 'pasajero')),
    CHECK (tipo_creador <> 'pasajero' OR reserva_id IS NOT NULL)
);

-- ============================================================
-- 11. SEGURIDAD Y EMERGENCIAS
-- ============================================================

CREATE TABLE alerta_sos (
    id UUID PRIMARY KEY DEFAULT GEN_RANDOM_UUID(),
    viaje_id UUID NOT NULL REFERENCES viaje(id),
    reserva_id UUID REFERENCES reserva(id),
    usuario_id UUID NOT NULL REFERENCES usuario(id),
    client_event_id UUID UNIQUE,
    ubicacion GEOGRAPHY(POINT, 4326),
    descripcion TEXT,
    estado VARCHAR(25) NOT NULL DEFAULT 'generada',
    llamada_emergencia_realizada BOOLEAN NOT NULL DEFAULT FALSE,
    atendido_por UUID REFERENCES usuario(id),
    resolucion TEXT,
    generado_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    atendido_at TIMESTAMPTZ,
    cerrado_at TIMESTAMPTZ,
    CHECK (estado IN ('generada', 'notificando', 'notificada', 'en_atencion', 'cerrada', 'fallida'))
);

CREATE TABLE alerta_sos_destinatario (
    id UUID PRIMARY KEY DEFAULT GEN_RANDOM_UUID(),
    alerta_sos_id UUID NOT NULL REFERENCES alerta_sos(id),
    contacto_emergencia_id UUID REFERENCES contacto_emergencia(id),
    tipo_destinatario VARCHAR(25) NOT NULL,
    destino VARCHAR(180),
    estado_envio VARCHAR(20) NOT NULL DEFAULT 'pendiente',
    enviado_at TIMESTAMPTZ,
    respuesta_proveedor TEXT,
    CHECK (tipo_destinatario IN ('contacto', 'plataforma')),
    CHECK (estado_envio IN ('pendiente', 'enviado', 'fallido'))
);

CREATE TABLE incidente (
    id UUID PRIMARY KEY DEFAULT GEN_RANDOM_UUID(),
    viaje_id UUID NOT NULL REFERENCES viaje(id),
    reserva_id UUID REFERENCES reserva(id),
    reportado_por UUID NOT NULL REFERENCES usuario(id),
    categoria_id UUID REFERENCES categoria_incidente(id),
    client_event_id UUID UNIQUE,
    descripcion TEXT NOT NULL,
    ubicacion GEOGRAPHY(POINT, 4326),
    estado VARCHAR(20) NOT NULL DEFAULT 'pendiente',
    prioridad VARCHAR(20) NOT NULL DEFAULT 'normal',
    atendido_por UUID REFERENCES usuario(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    atendido_at TIMESTAMPTZ,
    cerrado_at TIMESTAMPTZ,
    CHECK (estado IN ('pendiente', 'en_revision', 'atendido', 'cerrado')),
    CHECK (prioridad IN ('baja', 'normal', 'alta', 'critica'))
);

CREATE TABLE incidente_evidencia (
    id UUID PRIMARY KEY DEFAULT GEN_RANDOM_UUID(),
    incidente_id UUID NOT NULL REFERENCES incidente(id),
    archivo TEXT NOT NULL,
    tipo_archivo VARCHAR(80),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ============================================================
-- 12. REPORTES, INVESTIGACIONES Y SANCIONES
-- ============================================================

CREATE TABLE reporte (
    id UUID PRIMARY KEY DEFAULT GEN_RANDOM_UUID(),
    viaje_id UUID NOT NULL REFERENCES viaje(id),
    reserva_id UUID NOT NULL REFERENCES reserva(id),
    reportante_id UUID NOT NULL REFERENCES usuario(id),
    reportado_id UUID NOT NULL REFERENCES usuario(id),
    categoria_principal_id UUID NOT NULL REFERENCES categoria_reporte(id),
    descripcion TEXT NOT NULL,
    estado VARCHAR(25) NOT NULL DEFAULT 'recibido',
    notificado_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (reportante_id <> reportado_id),
    CHECK (estado IN ('recibido', 'en_revision', 'esperando_descargo', 'resuelto', 'archivado'))
);

CREATE UNIQUE INDEX uq_reporte_usuario_viaje
ON reporte(viaje_id, reportante_id, reportado_id);

CREATE TABLE reporte_categoria (
    reporte_id UUID NOT NULL REFERENCES reporte(id),
    categoria_id UUID NOT NULL REFERENCES categoria_reporte(id),
    PRIMARY KEY (reporte_id, categoria_id)
);

CREATE TABLE reporte_comunicacion (
    id UUID PRIMARY KEY DEFAULT GEN_RANDOM_UUID(),
    reporte_id UUID NOT NULL REFERENCES reporte(id),
    autor_id UUID NOT NULL REFERENCES usuario(id),
    tipo VARCHAR(25) NOT NULL,
    mensaje TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (tipo IN ('solicitud_informacion', 'respuesta', 'notificacion', 'nota_interna'))
);

CREATE TABLE reporte_evidencia (
    id UUID PRIMARY KEY DEFAULT GEN_RANDOM_UUID(),
    reporte_id UUID NOT NULL REFERENCES reporte(id),
    comunicacion_id UUID REFERENCES reporte_comunicacion(id),
    subido_por UUID NOT NULL REFERENCES usuario(id),
    archivo TEXT NOT NULL,
    tipo_archivo VARCHAR(80),
    descripcion TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE investigacion (
    id UUID PRIMARY KEY DEFAULT GEN_RANDOM_UUID(),
    reporte_id UUID NOT NULL UNIQUE REFERENCES reporte(id),
    responsable_id UUID REFERENCES usuario(id),
    estado VARCHAR(20) NOT NULL DEFAULT 'abierta',
    conclusion TEXT,
    iniciada_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    finalizada_at TIMESTAMPTZ,
    finalizada_manualmente BOOLEAN NOT NULL DEFAULT FALSE,
    CHECK (estado IN ('abierta', 'en_proceso', 'resuelta', 'archivada'))
);

CREATE TABLE descargo (
    id UUID PRIMARY KEY DEFAULT GEN_RANDOM_UUID(),
    reporte_id UUID NOT NULL REFERENCES reporte(id),
    usuario_id UUID NOT NULL REFERENCES usuario(id),
    mensaje TEXT NOT NULL,
    presentado_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE descargo_evidencia (
    id UUID PRIMARY KEY DEFAULT GEN_RANDOM_UUID(),
    descargo_id UUID NOT NULL REFERENCES descargo(id),
    archivo TEXT NOT NULL,
    tipo_archivo VARCHAR(80),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE restriccion_preventiva (
    id UUID PRIMARY KEY DEFAULT GEN_RANDOM_UUID(),
    usuario_id UUID NOT NULL REFERENCES usuario(id),
    usuario_objetivo_id UUID REFERENCES usuario(id),
    investigacion_id UUID NOT NULL REFERENCES investigacion(id),
    alcance VARCHAR(25) NOT NULL,
    gravedad VARCHAR(20) NOT NULL DEFAULT 'alta',
    motivo TEXT NOT NULL,
    autorizada_por UUID NOT NULL REFERENCES usuario(id),
    notificada BOOLEAN NOT NULL DEFAULT FALSE,
    notificada_at TIMESTAMPTZ,
    motivo_no_notificacion TEXT,
    inicia_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    termina_at TIMESTAMPTZ,
    activa BOOLEAN NOT NULL DEFAULT TRUE,
    CHECK (alcance IN ('pasajero', 'conductor', 'interaccion', 'total')),
    CHECK (gravedad IN ('media', 'alta', 'critica')),
    CHECK (alcance <> 'interaccion' OR usuario_objetivo_id IS NOT NULL),
    CHECK (usuario_objetivo_id IS NULL OR usuario_objetivo_id <> usuario_id),
    CHECK (termina_at IS NULL OR termina_at >= inicia_at)
);

CREATE TABLE sancion (
    id UUID PRIMARY KEY DEFAULT GEN_RANDOM_UUID(),
    usuario_id UUID NOT NULL REFERENCES usuario(id),
    investigacion_id UUID REFERENCES investigacion(id),
    tipo VARCHAR(30) NOT NULL,
    motivo TEXT NOT NULL,
    fecha_inicio TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    fecha_fin TIMESTAMPTZ,
    notificada_at TIMESTAMPTZ,
    aplicada_por UUID NOT NULL REFERENCES usuario(id),
    estado VARCHAR(20) NOT NULL DEFAULT 'activa',
    CHECK (tipo IN ('advertencia', 'suspension_temporal', 'suspension_larga', 'expulsion')),
    CHECK (estado IN ('activa', 'cumplida', 'revocada', 'apelada')),
    CHECK (fecha_fin IS NULL OR fecha_fin >= fecha_inicio)
);

CREATE TABLE apelacion_sancion (
    id UUID PRIMARY KEY DEFAULT GEN_RANDOM_UUID(),
    sancion_id UUID NOT NULL UNIQUE REFERENCES sancion(id),
    usuario_id UUID NOT NULL REFERENCES usuario(id),
    motivo TEXT NOT NULL,
    estado VARCHAR(20) NOT NULL DEFAULT 'presentada',
    presentada_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    resuelta_at TIMESTAMPTZ,
    resuelta_por UUID REFERENCES usuario(id),
    resolucion TEXT,
    CHECK (estado IN ('presentada', 'en_revision', 'aceptada', 'rechazada'))
);

CREATE TABLE historial_no_show (
    id UUID PRIMARY KEY DEFAULT GEN_RANDOM_UUID(),
    reserva_id UUID NOT NULL UNIQUE REFERENCES reserva(id),
    reserva_no_show_id UUID REFERENCES reserva_no_show(id),
    usuario_id UUID NOT NULL REFERENCES usuario(id),
    fecha_evento TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    estado_revision VARCHAR(20) NOT NULL DEFAULT 'pendiente',
    justificacion TEXT,
    resolucion TEXT,
    revisado_por UUID REFERENCES usuario(id),
    revisado_at TIMESTAMPTZ,
    CHECK (estado_revision IN ('pendiente', 'confirmado', 'justificado', 'anulado'))
);

-- ============================================================
-- 13. CHAT Y CALIFICACIONES
-- ============================================================

CREATE TABLE chat_reserva (
    id UUID PRIMARY KEY DEFAULT GEN_RANDOM_UUID(),
    reserva_id UUID NOT NULL UNIQUE REFERENCES reserva(id),
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    cerrado_at TIMESTAMPTZ
);

CREATE TABLE mensaje_chat (
    id UUID PRIMARY KEY DEFAULT GEN_RANDOM_UUID(),
    chat_id UUID NOT NULL REFERENCES chat_reserva(id),
    remitente_id UUID NOT NULL REFERENCES usuario(id),
    client_event_id UUID UNIQUE,
    tipo VARCHAR(20) NOT NULL DEFAULT 'texto',
    contenido TEXT,
    archivo TEXT,
    ubicacion GEOGRAPHY(POINT, 4326),
    enviado_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    leido_at TIMESTAMPTZ,
    CHECK (tipo IN ('texto', 'imagen', 'ubicacion', 'archivo')),
    CHECK (tipo <> 'texto' OR contenido IS NOT NULL),
    CHECK (tipo NOT IN ('imagen', 'archivo') OR archivo IS NOT NULL),
    CHECK (tipo <> 'ubicacion' OR ubicacion IS NOT NULL)
);

CREATE TABLE mensaje_reporte (
    id UUID PRIMARY KEY DEFAULT GEN_RANDOM_UUID(),
    mensaje_id UUID NOT NULL REFERENCES mensaje_chat(id),
    reportado_por UUID NOT NULL REFERENCES usuario(id),
    motivo TEXT NOT NULL,
    estado VARCHAR(20) NOT NULL DEFAULT 'pendiente',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (estado IN ('pendiente', 'revisado', 'resuelto'))
);

CREATE TABLE calificacion (
    id UUID PRIMARY KEY DEFAULT GEN_RANDOM_UUID(),
    reserva_id UUID NOT NULL REFERENCES reserva(id),
    evaluador_id UUID NOT NULL REFERENCES usuario(id),
    evaluado_id UUID NOT NULL REFERENCES usuario(id),
    rol_evaluado VARCHAR(20) NOT NULL,
    puntuacion SMALLINT NOT NULL,
    etiquetas JSONB NOT NULL DEFAULT '[]'::jsonb,
    comentario TEXT,
    es_anonima BOOLEAN NOT NULL DEFAULT FALSE,
    propina_monto NUMERIC(10,2) NOT NULL DEFAULT 0,
    propina_pago_id UUID REFERENCES pago(id),
    publicada BOOLEAN NOT NULL DEFAULT FALSE,
    publicar_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (rol_evaluado IN ('pasajero', 'conductor')),
    CHECK (puntuacion BETWEEN 1 AND 5),
    CHECK (propina_monto >= 0),
    CHECK (evaluador_id <> evaluado_id),
    UNIQUE (reserva_id, evaluador_id)
);

CREATE OR REPLACE FUNCTION fn_validar_calificacion()
RETURNS TRIGGER AS $$
DECLARE
    v_pasajero_id UUID;
    v_conductor_id UUID;
BEGIN
    SELECT r.pasajero_id, v.conductor_id
    INTO v_pasajero_id, v_conductor_id
    FROM reserva r
    JOIN viaje v ON v.id = r.viaje_id
    WHERE r.id = NEW.reserva_id;

    IF v_pasajero_id IS NULL THEN
        RAISE EXCEPTION 'La reserva a calificar no existe';
    END IF;

    IF NEW.rol_evaluado = 'conductor' THEN
        IF NEW.evaluador_id <> v_pasajero_id OR NEW.evaluado_id <> v_conductor_id THEN
            RAISE EXCEPTION 'La calificación al conductor debe ser emitida por el pasajero de la reserva';
        END IF;
    ELSIF NEW.rol_evaluado = 'pasajero' THEN
        IF NEW.evaluador_id <> v_conductor_id OR NEW.evaluado_id <> v_pasajero_id THEN
            RAISE EXCEPTION 'La calificación al pasajero debe ser emitida por el conductor del viaje';
        END IF;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_validar_calificacion
BEFORE INSERT OR UPDATE OF reserva_id, evaluador_id, evaluado_id, rol_evaluado
ON calificacion
FOR EACH ROW
EXECUTE FUNCTION fn_validar_calificacion();

-- ============================================================
-- 14. NOTIFICACIONES Y PREFERENCIAS
-- ============================================================

CREATE TABLE notificacion (
    id UUID PRIMARY KEY DEFAULT GEN_RANDOM_UUID(),
    usuario_id UUID NOT NULL REFERENCES usuario(id),
    categoria VARCHAR(40) NOT NULL REFERENCES categoria_notificacion(codigo),
    titulo VARCHAR(180) NOT NULL,
    mensaje TEXT NOT NULL,
    datos JSONB,
    prioridad VARCHAR(20) NOT NULL DEFAULT 'normal',
    leida_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (prioridad IN ('baja', 'normal', 'alta', 'critica'))
);

CREATE INDEX idx_notificacion_usuario_fecha
ON notificacion(usuario_id, created_at DESC);

CREATE TABLE preferencia_notificacion (
    id UUID PRIMARY KEY DEFAULT GEN_RANDOM_UUID(),
    usuario_id UUID NOT NULL REFERENCES usuario(id),
    categoria VARCHAR(40) NOT NULL REFERENCES categoria_notificacion(codigo),
    push_habilitado BOOLEAN NOT NULL DEFAULT TRUE,
    in_app_habilitado BOOLEAN NOT NULL DEFAULT TRUE,
    UNIQUE (usuario_id, categoria)
);

CREATE OR REPLACE FUNCTION fn_validar_preferencia_notificacion()
RETURNS TRIGGER AS $$
DECLARE
    v_desactivable BOOLEAN;
BEGIN
    SELECT desactivable INTO v_desactivable
    FROM categoria_notificacion
    WHERE codigo = NEW.categoria;

    IF v_desactivable = FALSE AND (NOT NEW.push_habilitado OR NOT NEW.in_app_habilitado) THEN
        RAISE EXCEPTION 'Las alertas críticas de categoría % no pueden desactivarse', NEW.categoria;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_validar_preferencia_notificacion
BEFORE INSERT OR UPDATE OF categoria, push_habilitado, in_app_habilitado
ON preferencia_notificacion
FOR EACH ROW
EXECUTE FUNCTION fn_validar_preferencia_notificacion();

-- ============================================================
-- 15. CENTRO DE SOPORTE
-- ============================================================

CREATE TABLE ticket_soporte (
    id UUID PRIMARY KEY DEFAULT GEN_RANDOM_UUID(),
    codigo VARCHAR(20) NOT NULL UNIQUE DEFAULT fn_generar_codigo(),
    usuario_id UUID NOT NULL REFERENCES usuario(id),
    viaje_id UUID REFERENCES viaje(id),
    reserva_id UUID REFERENCES reserva(id),
    pago_id UUID REFERENCES pago(id),
    categoria VARCHAR(40) NOT NULL,
    asunto VARCHAR(180) NOT NULL,
    descripcion TEXT NOT NULL,
    prioridad VARCHAR(20) NOT NULL DEFAULT 'normal',
    estado VARCHAR(25) NOT NULL DEFAULT 'abierto',
    asignado_a UUID REFERENCES usuario(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    cerrado_at TIMESTAMPTZ,
    CHECK (categoria IN ('viajes', 'reservas', 'pagos', 'seguridad', 'cuenta', 'aplicacion', 'otro')),
    CHECK (prioridad IN ('baja', 'normal', 'alta', 'critica')),
    CHECK (estado IN ('abierto', 'en_proceso', 'esperando_usuario', 'resuelto', 'cerrado'))
);

CREATE TABLE ticket_mensaje (
    id UUID PRIMARY KEY DEFAULT GEN_RANDOM_UUID(),
    ticket_id UUID NOT NULL REFERENCES ticket_soporte(id),
    autor_id UUID NOT NULL REFERENCES usuario(id),
    mensaje TEXT NOT NULL,
    interno BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE ticket_adjunto (
    id UUID PRIMARY KEY DEFAULT GEN_RANDOM_UUID(),
    ticket_id UUID NOT NULL REFERENCES ticket_soporte(id),
    mensaje_id UUID REFERENCES ticket_mensaje(id),
    archivo TEXT NOT NULL,
    nombre_original VARCHAR(255),
    mime_type VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ============================================================
-- 16. CONSENTIMIENTOS Y AUDITORÍA
-- ============================================================

CREATE TABLE consentimiento_usuario (
    id UUID PRIMARY KEY DEFAULT GEN_RANDOM_UUID(),
    usuario_id UUID NOT NULL REFERENCES usuario(id),
    tipo VARCHAR(60) NOT NULL,
    version_documento VARCHAR(30) NOT NULL,
    aceptado BOOLEAN NOT NULL,
    aceptado_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ip_origen INET,
    dispositivo_id UUID REFERENCES dispositivo(id)
);

CREATE TABLE auditoria (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    usuario_id UUID REFERENCES usuario(id),
    entidad VARCHAR(100) NOT NULL,
    entidad_id UUID,
    accion VARCHAR(40) NOT NULL,
    valores_anteriores JSONB,
    valores_nuevos JSONB,
    ip_origen INET,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_auditoria_entidad
ON auditoria(entidad, entidad_id, created_at DESC);

-- ============================================================
-- 17. ÍNDICES ESPACIALES (GIST) Y COMPLEMENTARIOS
-- ============================================================

-- Índices espaciales PostGIS
CREATE INDEX idx_usuario_direccion_ubicacion_gist ON usuario_direccion USING GIST (ubicacion);
CREATE INDEX idx_ruta_geometria_gist ON ruta USING GIST (geometria);
CREATE INDEX idx_ruta_punto_ubicacion_gist ON ruta_punto USING GIST (ubicacion);
CREATE INDEX idx_viaje_ruta_operativa_gist ON viaje USING GIST (ruta_operativa);
CREATE INDEX idx_viaje_ultima_ubicacion_gist ON viaje USING GIST (ultima_ubicacion);
CREATE INDEX idx_viaje_parada_ubicacion_gist ON viaje_parada USING GIST (ubicacion);
CREATE INDEX idx_viaje_segmento_origen_gist ON viaje_segmento USING GIST (origen);
CREATE INDEX idx_viaje_segmento_destino_gist ON viaje_segmento USING GIST (destino);
CREATE INDEX idx_solicitud_recogida_gist ON solicitud_reserva USING GIST (punto_recogida);
CREATE INDEX idx_solicitud_destino_gist ON solicitud_reserva USING GIST (punto_destino);
CREATE INDEX idx_reserva_recogida_gist ON reserva USING GIST (punto_recogida);
CREATE INDEX idx_reserva_destino_gist ON reserva USING GIST (punto_destino);
CREATE INDEX idx_reserva_descenso_real_gist ON reserva USING GIST (punto_descenso_real);
CREATE INDEX idx_gps_ubicacion_gist ON viaje_ubicacion_gps USING GIST (ubicacion);
CREATE INDEX idx_alerta_sos_ubicacion_gist ON alerta_sos USING GIST (ubicacion);
CREATE INDEX idx_incidente_ubicacion_gist ON incidente USING GIST (ubicacion);
CREATE INDEX idx_mensaje_chat_ubicacion_gist ON mensaje_chat USING GIST (ubicacion);

-- Índices B-tree complementarios
CREATE INDEX idx_usuario_estado ON usuario(estado);
CREATE INDEX idx_usuario_rol_usuario ON usuario_rol(usuario_id) WHERE activo = TRUE;
CREATE INDEX idx_usuario_sesion_usuario ON usuario_sesion(usuario_id, expira_at DESC) WHERE revocado_at IS NULL;
CREATE INDEX idx_codigo_verificacion_usuario ON codigo_verificacion(usuario_id, proposito, created_at DESC);
CREATE INDEX idx_vehiculo_conductor ON vehiculo(perfil_conductor_id);
CREATE INDEX idx_documento_conductor_vehiculo ON documento_conductor(vehiculo_id);
CREATE INDEX idx_reserva_segmento_reserva ON reserva_segmento(reserva_id);
CREATE INDEX idx_pago_reserva ON pago(reserva_id);
CREATE INDEX idx_pago_estado ON pago(estado);
CREATE INDEX idx_movimiento_billetera ON movimiento_billetera(billetera_id, created_at DESC);
CREATE INDEX idx_recarga_billetera_usuario ON recarga_billetera(usuario_id, estado);
CREATE INDEX idx_comision_viaje_conductor ON comision_viaje(conductor_id, estado);
CREATE INDEX idx_incidente_viaje ON incidente(viaje_id, created_at DESC);
CREATE INDEX idx_reporte_reportado ON reporte(reportado_id, created_at DESC);
CREATE INDEX idx_restriccion_preventiva_activa ON restriccion_preventiva(usuario_id) WHERE activa = TRUE;
CREATE INDEX idx_sancion_usuario ON sancion(usuario_id, fecha_inicio DESC);
CREATE INDEX idx_chat_reserva ON chat_reserva(reserva_id);
CREATE INDEX idx_mensaje_chat_fecha ON mensaje_chat(chat_id, enviado_at);
CREATE INDEX idx_calificacion_evaluado ON calificacion(evaluado_id, rol_evaluado);
CREATE INDEX idx_ticket_usuario ON ticket_soporte(usuario_id, created_at DESC);

-- ============================================================
-- 18. TRIGGERS DE ACTUALIZACIÓN
-- ============================================================

CREATE TRIGGER trg_configuracion_updated
BEFORE UPDATE ON configuracion
FOR EACH ROW EXECUTE FUNCTION fn_actualizar_updated_at();

CREATE TRIGGER trg_usuario_updated
BEFORE UPDATE ON usuario
FOR EACH ROW EXECUTE FUNCTION fn_actualizar_updated_at();

CREATE TRIGGER trg_identidad_updated
BEFORE UPDATE ON verificacion_identidad
FOR EACH ROW EXECUTE FUNCTION fn_actualizar_updated_at();

CREATE TRIGGER trg_perfil_conductor_updated
BEFORE UPDATE ON perfil_conductor
FOR EACH ROW EXECUTE FUNCTION fn_actualizar_updated_at();

CREATE TRIGGER trg_vehiculo_updated
BEFORE UPDATE ON vehiculo
FOR EACH ROW EXECUTE FUNCTION fn_actualizar_updated_at();

CREATE TRIGGER trg_viaje_updated
BEFORE UPDATE ON viaje
FOR EACH ROW EXECUTE FUNCTION fn_actualizar_updated_at();

CREATE TRIGGER trg_reserva_updated
BEFORE UPDATE ON reserva
FOR EACH ROW EXECUTE FUNCTION fn_actualizar_updated_at();

CREATE TRIGGER trg_pago_updated
BEFORE UPDATE ON pago
FOR EACH ROW EXECUTE FUNCTION fn_actualizar_updated_at();

CREATE TRIGGER trg_billetera_updated
BEFORE UPDATE ON billetera
FOR EACH ROW EXECUTE FUNCTION fn_actualizar_updated_at();

CREATE TRIGGER trg_ticket_updated
BEFORE UPDATE ON ticket_soporte
FOR EACH ROW EXECUTE FUNCTION fn_actualizar_updated_at();

-- ============================================================
-- 19. REGLAS DE NEGOCIO ADICIONALES
-- ============================================================

-- Impide eliminar o alterar los datos originales de un reporte; solo permite actualizar estado y fecha de notificación.
CREATE OR REPLACE FUNCTION fn_proteger_reporte_original()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'Un reporte original no puede eliminarse';
    END IF;

    IF OLD.id IS DISTINCT FROM NEW.id
       OR OLD.viaje_id IS DISTINCT FROM NEW.viaje_id
       OR OLD.reserva_id IS DISTINCT FROM NEW.reserva_id
       OR OLD.reportante_id IS DISTINCT FROM NEW.reportante_id
       OR OLD.reportado_id IS DISTINCT FROM NEW.reportado_id
       OR OLD.categoria_principal_id IS DISTINCT FROM NEW.categoria_principal_id
       OR OLD.descripcion IS DISTINCT FROM NEW.descripcion
       OR OLD.created_at IS DISTINCT FROM NEW.created_at THEN
        RAISE EXCEPTION 'El contenido original de un reporte es inmutable';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_reporte_inmutable
BEFORE UPDATE OR DELETE ON reporte
FOR EACH ROW
EXECUTE FUNCTION fn_proteger_reporte_original();

-- Impide modificar o eliminar evidencias originales de reportes.
CREATE OR REPLACE FUNCTION fn_proteger_evidencia_reporte()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'La evidencia original de un reporte es inmutable';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_evidencia_reporte_inmutable
BEFORE UPDATE OR DELETE ON reporte_evidencia
FOR EACH ROW
EXECUTE FUNCTION fn_proteger_evidencia_reporte();

-- Los descargos deben presentarse dentro de los 7 días calendario desde la notificación formal del reporte.
CREATE OR REPLACE FUNCTION fn_validar_plazo_descargo()
RETURNS TRIGGER AS $$
DECLARE
    v_notificado_at TIMESTAMPTZ;
    v_plazo_dias INTEGER;
BEGIN
    SELECT notificado_at
    INTO v_notificado_at
    FROM reporte
    WHERE id = NEW.reporte_id;

    IF v_notificado_at IS NULL THEN
        RAISE EXCEPTION 'El reporte todavía no ha sido notificado formalmente';
    END IF;

    SELECT COALESCE((valor #>> '{}')::INTEGER, 7)
    INTO v_plazo_dias
    FROM configuracion
    WHERE clave = 'reporte.plazo_descargo_dias';

    v_plazo_dias := COALESCE(v_plazo_dias, 7);

    IF fn_excede_dias_calendario(v_notificado_at, NEW.presentado_at, v_plazo_dias) THEN
        RAISE EXCEPTION 'El plazo de % días calendario para presentar descargos ha vencido', v_plazo_dias;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_validar_plazo_descargo
BEFORE INSERT ON descargo
FOR EACH ROW
EXECUTE FUNCTION fn_validar_plazo_descargo();

-- Las apelaciones deben presentarse dentro de los 7 días calendario posteriores a la notificación de la sanción.
CREATE OR REPLACE FUNCTION fn_validar_plazo_apelacion()
RETURNS TRIGGER AS $$
DECLARE
    v_notificada_at TIMESTAMPTZ;
    v_plazo_dias INTEGER;
BEGIN
    SELECT notificada_at
    INTO v_notificada_at
    FROM sancion
    WHERE id = NEW.sancion_id;

    IF v_notificada_at IS NULL THEN
        RAISE EXCEPTION 'La sanción todavía no ha sido notificada';
    END IF;

    SELECT COALESCE((valor #>> '{}')::INTEGER, 7)
    INTO v_plazo_dias
    FROM configuracion
    WHERE clave = 'sancion.plazo_apelacion_dias';

    v_plazo_dias := COALESCE(v_plazo_dias, 7);

    IF fn_excede_dias_calendario(v_notificada_at, NEW.presentada_at, v_plazo_dias) THEN
        RAISE EXCEPTION 'El plazo de apelación de % días calendario ha vencido', v_plazo_dias;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_validar_plazo_apelacion
BEFORE INSERT ON apelacion_sancion
FOR EACH ROW
EXECUTE FUNCTION fn_validar_plazo_apelacion();

-- Impide modificar o eliminar movimientos financieros.
CREATE OR REPLACE FUNCTION fn_proteger_movimiento_billetera()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'Los movimientos de billetera no pueden modificarse ni eliminarse';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_movimiento_billetera_inmutable
BEFORE UPDATE OR DELETE ON movimiento_billetera
FOR EACH ROW
EXECUTE FUNCTION fn_proteger_movimiento_billetera();

-- ============================================================
-- 20. VISTAS DE CONSULTA
-- ============================================================

CREATE OR REPLACE VIEW vw_viajes_publicados AS
SELECT
    v.id AS viaje_id,
    v.codigo,
    v.conductor_id,
    u.nombres AS conductor_nombres,
    u.apellidos AS conductor_apellidos,
    v.ruta_id,
    r.nombre AS ruta_nombre,
    v.salida_programada_at,
    v.capacidad_total,
    v.tipo_tarifa,
    v.precio_asiento_completo,
    v.tarifa_km,
    v.acepta_reservas,
    v.aceptacion_automatica,
    v.estado
FROM viaje v
JOIN usuario u ON u.id = v.conductor_id
JOIN ruta r ON r.id = v.ruta_id
WHERE v.estado = 'publicado';

CREATE OR REPLACE VIEW vw_reservas_activas AS
SELECT
    r.id AS reserva_id,
    r.codigo,
    r.viaje_id,
    r.pasajero_id,
    u.nombres AS pasajero_nombres,
    u.apellidos AS pasajero_apellidos,
    r.asientos_reservados,
    r.fraccion_recogida,
    r.fraccion_destino,
    r.distancia_reservada_km,
    r.precio_total,
    r.estado,
    r.hold_expira_at
FROM reserva r
JOIN usuario u ON u.id = r.pasajero_id
WHERE r.estado IN ('esperando_pasajero', 'confirmada', 'en_curso');

CREATE OR REPLACE VIEW vw_billeteras_usuario AS
SELECT
    b.id,
    b.usuario_id,
    b.tipo,
    b.saldo,
    b.permite_saldo_negativo,
    m.codigo AS moneda,
    b.activa
FROM billetera b
JOIN moneda m ON m.id = b.moneda_id;

CREATE OR REPLACE VIEW vw_resumen_viaje AS
SELECT
    v.id AS viaje_id,
    v.codigo,
    v.conductor_id,
    v.estado,
    v.capacidad_total,
    COUNT(r.id) FILTER (
        WHERE r.estado IN ('esperando_pasajero', 'confirmada', 'en_curso')
    ) AS reservas_activas,
    COALESCE(SUM(r.asientos_reservados) FILTER (
        WHERE r.estado IN ('esperando_pasajero', 'confirmada', 'en_curso')
    ), 0) AS asientos_acumulados_tramos,
    COALESCE((
        SELECT MAX(p.ocupacion)
        FROM (
            SELECT (
                SELECT COALESCE(SUM(r2.asientos_reservados), 0)
                FROM reserva r2
                WHERE r2.viaje_id = v.id
                  AND r2.estado IN ('esperando_pasajero', 'confirmada', 'en_curso')
                  AND r2.fraccion_recogida <= r1.fraccion_recogida
                  AND r2.fraccion_destino > r1.fraccion_recogida
            ) AS ocupacion
            FROM reserva r1
            WHERE r1.viaje_id = v.id
              AND r1.estado IN ('esperando_pasajero', 'confirmada', 'en_curso')
        ) p
    ), 0) AS ocupacion_pico_asientos
FROM viaje v
LEFT JOIN reserva r ON r.viaje_id = v.id
GROUP BY v.id, v.codigo, v.conductor_id, v.estado, v.capacidad_total;

COMMIT;

-- ============================================================
-- FIN DEL ESQUEMA RUTACOMPARTIDA
-- ============================================================
