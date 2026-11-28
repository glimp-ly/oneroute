//go:build integration

package scheduler_test

import (
	"context"
	"errors"
	"fmt"
	"os"
	"sync"
	"testing"
	"time"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"

	"github.com/glimp-ly/oneroute/backend/internal/shared/db"
	"github.com/glimp-ly/oneroute/backend/internal/shared/realtime"
	"github.com/glimp-ly/oneroute/backend/internal/shared/scheduler"
)

func testDatabaseURL() string {
	if v := os.Getenv("TEST_DATABASE_URL"); v != "" {
		return v
	}
	if v := os.Getenv("DATABASE_URL"); v != "" {
		return v
	}
	return "postgres://rutacompartida:rutacompartida_dev_pass@127.0.0.1:5432/rutacompartida?sslmode=disable"
}

type integrationFixture struct {
	pool           *pgxpool.Pool
	paisID         uuid.UUID
	monedaPENID    uuid.UUID
	driverID       uuid.UUID
	passenger1ID   uuid.UUID
	passenger2ID   uuid.UUID
	passenger3ID   uuid.UUID
	perfilID       uuid.UUID
	vehiculoID     uuid.UUID
	rutaID         uuid.UUID
	viajeActiveID  uuid.UUID
	viajeDoneID    uuid.UUID
	viajeCancelID  uuid.UUID
	cleanupMu      sync.Mutex
	solicitudIDs   []uuid.UUID
	reservaIDs     []uuid.UUID
	pagoIDs        []uuid.UUID
	reembolsoIDs   []uuid.UUID
	califIDs       []uuid.UUID
	trackingIDs    []uuid.UUID
	gpsViajeIDs    []uuid.UUID
}

func setupIntegrationFixture(t *testing.T, ctx context.Context) *integrationFixture {
	t.Helper()

	pool, err := db.NewPool(ctx, testDatabaseURL(), "America/Lima")
	if err != nil {
		t.Fatalf("db.NewPool failed: %v", err)
	}

	f := &integrationFixture{
		pool:          pool,
		driverID:      uuid.New(),
		passenger1ID:  uuid.New(),
		passenger2ID:  uuid.New(),
		passenger3ID:  uuid.New(),
		perfilID:      uuid.New(),
		vehiculoID:    uuid.New(),
		rutaID:        uuid.New(),
		viajeActiveID: uuid.New(),
		viajeDoneID:   uuid.New(),
		viajeCancelID: uuid.New(),
	}

	t.Cleanup(func() {
		cleanCtx, cancel := context.WithTimeout(context.Background(), 15*time.Second)
		defer cancel()
		f.cleanup(cleanCtx)
		pool.Close()
	})

	if err := pool.QueryRow(ctx, "SELECT id FROM pais WHERE codigo_iso2 = 'PE'").Scan(&f.paisID); err != nil {
		t.Fatalf("query pais PE failed: %v", err)
	}
	if err := pool.QueryRow(ctx, "SELECT id FROM moneda WHERE codigo = 'PEN'").Scan(&f.monedaPENID); err != nil {
		t.Fatalf("query moneda PEN failed: %v", err)
	}

	users := []struct {
		id     uuid.UUID
		label  string
		suffix string
	}{
		{f.driverID, "Conductor", "drv"},
		{f.passenger1ID, "PasajeroUno", "p1"},
		{f.passenger2ID, "PasajeroDos", "p2"},
		{f.passenger3ID, "PasajeroTres", "p3"},
	}
	for idx, u := range users {
		short := u.id.String()[:8]
		if _, err := pool.Exec(ctx, `
			INSERT INTO usuario (
				id, nombres, apellidos, correo, telefono, password_hash,
				pais_id, estado, correo_verificado, telefono_verificado, identidad_verificada
			)
			VALUES ($1, $2, 'SchedulerTest', $3, $4, 'hash_placeholder', $5, 'activo', TRUE, TRUE, TRUE)
		`,
			u.id,
			u.label,
			fmt.Sprintf("sched_%s_%s@oneroute.test", u.suffix, short),
			fmt.Sprintf("+519%02d%s", idx, short[:6]),
			f.paisID,
		); err != nil {
			t.Fatalf("insert usuario %s failed: %v", u.label, err)
		}
	}

	if _, err := pool.Exec(ctx, `
		INSERT INTO perfil_conductor (id, usuario_id, estado, aprobado_at)
		VALUES ($1, $2, 'aprobado', NOW())
	`, f.perfilID, f.driverID); err != nil {
		t.Fatalf("insert perfil_conductor failed: %v", err)
	}

	plate := "TS-" + f.vehiculoID.String()[:6]
	if _, err := pool.Exec(ctx, `
		INSERT INTO vehiculo (id, perfil_conductor_id, placa, marca, modelo, anio, color, capacidad_pasajeros, tipo, estado)
		VALUES ($1, $2, $3, 'Toyota', 'Corolla', 2023, 'Gris', 4, 'sedan', 'aprobado')
	`, f.vehiculoID, f.perfilID, plate); err != nil {
		t.Fatalf("insert vehiculo failed: %v", err)
	}

	if _, err := pool.Exec(ctx, `
		INSERT INTO ruta (id, nombre, origen_nombre, destino_nombre, geometria, distancia_km, creado_por)
		VALUES (
			$1,
			$2,
			'Miraflores',
			'San Isidro',
			ST_GeogFromText('SRID=4326;LINESTRING(-77.0290 -12.1191, -77.0365 -12.0942)'),
			5.80,
			$3
		)
	`, f.rutaID, "Ruta Scheduler "+f.rutaID.String()[:8], f.driverID); err != nil {
		t.Fatalf("insert ruta failed: %v", err)
	}

	trips := []struct {
		id     uuid.UUID
		estado string
	}{
		{f.viajeActiveID, "publicado"},
		{f.viajeDoneID, "completado"},
		{f.viajeCancelID, "cancelado"},
	}
	for _, tr := range trips {
		code := "SC" + tr.id.String()[:8]
		if _, err := pool.Exec(ctx, `
			INSERT INTO viaje (
				id, codigo, conductor_id, vehiculo_id, ruta_id, moneda_id,
				salida_programada_at, estado, capacidad_total, tipo_tarifa, tarifa_km,
				distancia_operativa_km, ruta_operativa
			)
			VALUES (
				$1, $2, $3, $4, $5, $6,
				NOW() + INTERVAL '1 hour', $7, 4, 'por_asiento', 2.00,
				5.80, ST_GeogFromText('SRID=4326;LINESTRING(-77.0290 -12.1191, -77.0365 -12.0942)')
			)
		`, tr.id, code, f.driverID, f.vehiculoID, f.rutaID, f.monedaPENID, tr.estado); err != nil {
			t.Fatalf("insert viaje (%s) failed: %v", tr.estado, err)
		}
	}
	f.gpsViajeIDs = []uuid.UUID{f.viajeActiveID, f.viajeDoneID, f.viajeCancelID}

	return f
}

func (f *integrationFixture) cleanup(ctx context.Context) {
	f.cleanupMu.Lock()
	defer f.cleanupMu.Unlock()

	if len(f.gpsViajeIDs) > 0 {
		_, _ = f.pool.Exec(ctx, "DELETE FROM viaje_ubicacion_gps WHERE viaje_id = ANY($1)", f.gpsViajeIDs)
	}
	if len(f.trackingIDs) > 0 {
		_, _ = f.pool.Exec(ctx, "DELETE FROM enlace_seguimiento WHERE id = ANY($1)", f.trackingIDs)
	}
	if len(f.califIDs) > 0 {
		_, _ = f.pool.Exec(ctx, "DELETE FROM calificacion WHERE id = ANY($1)", f.califIDs)
	}
	if len(f.reembolsoIDs) > 0 {
		_, _ = f.pool.Exec(ctx, "DELETE FROM reembolso WHERE id = ANY($1)", f.reembolsoIDs)
	}
	if len(f.pagoIDs) > 0 {
		_, _ = f.pool.Exec(ctx, "DELETE FROM pago WHERE id = ANY($1)", f.pagoIDs)
	}
	if len(f.reservaIDs) > 0 {
		_, _ = f.pool.Exec(ctx, "DELETE FROM reserva_evento WHERE reserva_id = ANY($1)", f.reservaIDs)
		_, _ = f.pool.Exec(ctx, "DELETE FROM reserva WHERE id = ANY($1)", f.reservaIDs)
	}
	if len(f.solicitudIDs) > 0 {
		_, _ = f.pool.Exec(ctx, "DELETE FROM solicitud_intento WHERE solicitud_id = ANY($1)", f.solicitudIDs)
		_, _ = f.pool.Exec(ctx, "DELETE FROM solicitud_reserva WHERE id = ANY($1)", f.solicitudIDs)
	}

	_, _ = f.pool.Exec(ctx, "DELETE FROM viaje WHERE id = ANY($1)", []uuid.UUID{f.viajeActiveID, f.viajeDoneID, f.viajeCancelID})
	_, _ = f.pool.Exec(ctx, "DELETE FROM ruta WHERE id = $1", f.rutaID)
	_, _ = f.pool.Exec(ctx, "DELETE FROM vehiculo WHERE id = $1", f.vehiculoID)
	_, _ = f.pool.Exec(ctx, "DELETE FROM perfil_conductor WHERE id = $1", f.perfilID)
	_, _ = f.pool.Exec(ctx, "DELETE FROM usuario WHERE id = ANY($1)", []uuid.UUID{
		f.driverID, f.passenger1ID, f.passenger2ID, f.passenger3ID,
	})
}

func TestExpireRequestsAndHoldsOnceIntegration(t *testing.T) {
	ctx, cancel := context.WithTimeout(context.Background(), 20*time.Second)
	defer cancel()

	f := setupIntegrationFixture(t, ctx)

	expiredReqID := uuid.New()
	activeReqID := uuid.New()
	expiredIntentoID := uuid.New()
	activeIntentoID := uuid.New()
	expiredHoldID := uuid.New()
	activeHoldID := uuid.New()

	f.solicitudIDs = append(f.solicitudIDs, expiredReqID, activeReqID)
	f.reservaIDs = append(f.reservaIDs, expiredHoldID, activeHoldID)

	// Expired solicitud_reserva with intentos_consumidos = 2 -> should become 3 and expirada.
	if _, err := f.pool.Exec(ctx, `
		INSERT INTO solicitud_reserva (
			id, viaje_id, pasajero_id, punto_recogida, punto_destino,
			fraccion_recogida, fraccion_destino, asientos_solicitados,
			estado, intentos_consumidos, expira_at
		)
		VALUES (
			$1, $2, $3,
			ST_GeogFromText('SRID=4326;POINT(-77.0290 -12.1191)'),
			ST_GeogFromText('SRID=4326;POINT(-77.0365 -12.0942)'),
			0.0, 0.5, 1, 'pendiente', 2, NOW() - INTERVAL '10 seconds'
		)
	`, expiredReqID, f.viajeActiveID, f.passenger1ID); err != nil {
		t.Fatalf("insert expired solicitud_reserva failed: %v", err)
	}
	if _, err := f.pool.Exec(ctx, `
		INSERT INTO solicitud_intento (id, solicitud_id, secuencia, cuenta_para_limite, resultado, expira_at)
		VALUES ($1, $2, 3, TRUE, 'pendiente', NOW() - INTERVAL '10 seconds')
	`, expiredIntentoID, expiredReqID); err != nil {
		t.Fatalf("insert expired solicitud_intento failed: %v", err)
	}

	// Non-expired solicitud_reserva -> must remain pendiente.
	if _, err := f.pool.Exec(ctx, `
		INSERT INTO solicitud_reserva (
			id, viaje_id, pasajero_id, punto_recogida, punto_destino,
			fraccion_recogida, fraccion_destino, asientos_solicitados,
			estado, intentos_consumidos, expira_at
		)
		VALUES (
			$1, $2, $3,
			ST_GeogFromText('SRID=4326;POINT(-77.0290 -12.1191)'),
			ST_GeogFromText('SRID=4326;POINT(-77.0365 -12.0942)'),
			0.5, 1.0, 1, 'pendiente', 0, NOW() + INTERVAL '5 minutes'
		)
	`, activeReqID, f.viajeActiveID, f.passenger2ID); err != nil {
		t.Fatalf("insert active solicitud_reserva failed: %v", err)
	}
	if _, err := f.pool.Exec(ctx, `
		INSERT INTO solicitud_intento (id, solicitud_id, secuencia, cuenta_para_limite, resultado, expira_at)
		VALUES ($1, $2, 1, TRUE, 'pendiente', NOW() + INTERVAL '5 minutes')
	`, activeIntentoID, activeReqID); err != nil {
		t.Fatalf("insert active solicitud_intento failed: %v", err)
	}

	// Expired held reserva -> should become expirada and record reserva_evento.
	if _, err := f.pool.Exec(ctx, `
		INSERT INTO reserva (
			id, codigo, viaje_id, pasajero_id, fraccion_recogida, fraccion_destino,
			distancia_reservada_km, asientos_reservados, estado, precio_original, precio_total,
			moneda_id, hold_expira_at, punto_recogida, punto_destino
		)
		VALUES (
			$1, $2, $3, $4, 0.0, 0.5, 2.9, 1, 'esperando_pasajero', 10.00, 10.00,
			$5, NOW() - INTERVAL '10 seconds',
			ST_GeogFromText('SRID=4326;POINT(-77.0290 -12.1191)'),
			ST_GeogFromText('SRID=4326;POINT(-77.0365 -12.0942)')
		)
	`, expiredHoldID, "EH"+expiredHoldID.String()[:8], f.viajeActiveID, f.passenger1ID, f.monedaPENID); err != nil {
		t.Fatalf("insert expired hold reserva failed: %v", err)
	}

	// Non-expired held reserva -> must stay esperando_pasajero.
	if _, err := f.pool.Exec(ctx, `
		INSERT INTO reserva (
			id, codigo, viaje_id, pasajero_id, fraccion_recogida, fraccion_destino,
			distancia_reservada_km, asientos_reservados, estado, precio_original, precio_total,
			moneda_id, hold_expira_at, punto_recogida, punto_destino
		)
		VALUES (
			$1, $2, $3, $4, 0.5, 1.0, 2.9, 1, 'esperando_pasajero', 10.00, 10.00,
			$5, NOW() + INTERVAL '5 minutes',
			ST_GeogFromText('SRID=4326;POINT(-77.0290 -12.1191)'),
			ST_GeogFromText('SRID=4326;POINT(-77.0365 -12.0942)')
		)
	`, activeHoldID, "AH"+activeHoldID.String()[:8], f.viajeActiveID, f.passenger3ID, f.monedaPENID); err != nil {
		t.Fatalf("insert active hold reserva failed: %v", err)
	}

	pub := &mockPublisher{}
	r := scheduler.NewRunner(f.pool, scheduler.Options{
		Publisher: pub,
	})

	expReqs, expHolds, err := r.ExpireRequestsAndHoldsOnce(ctx)
	if err != nil {
		t.Fatalf("ExpireRequestsAndHoldsOnce failed: %v", err)
	}
	if expReqs < 1 {
		t.Errorf("expiredRequests = %d, want >= 1", expReqs)
	}
	if expHolds < 1 {
		t.Errorf("expiredHolds = %d, want >= 1", expHolds)
	}

	var reqEstado string
	var reqIntentos int
	if err := f.pool.QueryRow(ctx, "SELECT estado, intentos_consumidos FROM solicitud_reserva WHERE id = $1", expiredReqID).Scan(&reqEstado, &reqIntentos); err != nil {
		t.Fatalf("query expired solicitud_reserva failed: %v", err)
	}
	if reqEstado != "expirada" || reqIntentos != 3 {
		t.Errorf("expired solicitud_reserva = (%q, %d), want (expirada, 3)", reqEstado, reqIntentos)
	}

	var intentoEstado string
	if err := f.pool.QueryRow(ctx, "SELECT resultado FROM solicitud_intento WHERE id = $1", expiredIntentoID).Scan(&intentoEstado); err != nil {
		t.Fatalf("query expired solicitud_intento failed: %v", err)
	}
	if intentoEstado != "expirada" {
		t.Errorf("expired solicitud_intento.resultado = %q, want expirada", intentoEstado)
	}

	var activeReqEstado string
	if err := f.pool.QueryRow(ctx, "SELECT estado FROM solicitud_reserva WHERE id = $1", activeReqID).Scan(&activeReqEstado); err != nil {
		t.Fatalf("query active solicitud_reserva failed: %v", err)
	}
	if activeReqEstado != "pendiente" {
		t.Errorf("active solicitud_reserva.estado = %q, want pendiente", activeReqEstado)
	}

	var holdEstado string
	if err := f.pool.QueryRow(ctx, "SELECT estado FROM reserva WHERE id = $1", expiredHoldID).Scan(&holdEstado); err != nil {
		t.Fatalf("query expired reserva failed: %v", err)
	}
	if holdEstado != "expirada" {
		t.Errorf("expired reserva.estado = %q, want expirada", holdEstado)
	}

	var eventCount int
	if err := f.pool.QueryRow(ctx, "SELECT COUNT(*) FROM reserva_evento WHERE reserva_id = $1 AND tipo = 'hold_expirado' AND estado_nuevo = 'expirada'", expiredHoldID).Scan(&eventCount); err != nil {
		t.Fatalf("query reserva_evento failed: %v", err)
	}
	if eventCount != 1 {
		t.Errorf("reserva_evento count = %d, want 1", eventCount)
	}

	var activeHoldEstado string
	if err := f.pool.QueryRow(ctx, "SELECT estado FROM reserva WHERE id = $1", activeHoldID).Scan(&activeHoldEstado); err != nil {
		t.Fatalf("query active reserva failed: %v", err)
	}
	if activeHoldEstado != "esperando_pasajero" {
		t.Errorf("active reserva.estado = %q, want esperando_pasajero", activeHoldEstado)
	}

	expectedRoom := realtime.UserRoom(f.passenger1ID)
	var p1ExpiredEvents int
	for _, ev := range pub.snapshot() {
		if ev.room == expectedRoom && ev.event == "request.expired" {
			p1ExpiredEvents++
		}
	}
	if p1ExpiredEvents != 2 {
		t.Errorf("published request.expired events to %s = %d, want 2 (1 request + 1 hold)", expectedRoom, p1ExpiredEvents)
	}
}

type recordingCreditReleaser struct {
	mu      sync.Mutex
	calls   []creditCall
	failErr error
}

type creditCall struct {
	userID  uuid.UUID
	refType string
	refID   uuid.UUID
	amount  float64
	desc    string
}

func (r *recordingCreditReleaser) CreditPassengerBalance(
	_ context.Context,
	_ pgx.Tx,
	userID uuid.UUID,
	refType string,
	refID uuid.UUID,
	amount float64,
	desc string,
) error {
	r.mu.Lock()
	defer r.mu.Unlock()
	r.calls = append(r.calls, creditCall{
		userID:  userID,
		refType: refType,
		refID:   refID,
		amount:  amount,
		desc:    desc,
	})
	return r.failErr
}

func TestReleaseScheduledCreditsOnceIntegration(t *testing.T) {
	ctx, cancel := context.WithTimeout(context.Background(), 20*time.Second)
	defer cancel()

	f := setupIntegrationFixture(t, ctx)

	reservaID := uuid.New()
	pagoID := uuid.New()
	dueReembolsoID := uuid.New()
	futureReembolsoID := uuid.New()

	f.reservaIDs = append(f.reservaIDs, reservaID)
	f.pagoIDs = append(f.pagoIDs, pagoID)
	f.reembolsoIDs = append(f.reembolsoIDs, dueReembolsoID, futureReembolsoID)

	if _, err := f.pool.Exec(ctx, `
		INSERT INTO reserva (
			id, codigo, viaje_id, pasajero_id, fraccion_recogida, fraccion_destino,
			distancia_reservada_km, asientos_reservados, estado, precio_original, precio_total,
			moneda_id, punto_recogida, punto_destino
		)
		VALUES (
			$1, $2, $3, $4, 0.0, 1.0, 5.8, 2, 'confirmada', 30.00, 30.00,
			$5,
			ST_GeogFromText('SRID=4326;POINT(-77.0290 -12.1191)'),
			ST_GeogFromText('SRID=4326;POINT(-77.0365 -12.0942)')
		)
	`, reservaID, "CR"+reservaID.String()[:8], f.viajeActiveID, f.passenger1ID, f.monedaPENID); err != nil {
		t.Fatalf("insert reserva failed: %v", err)
	}

	if _, err := f.pool.Exec(ctx, `
		INSERT INTO pago (
			id, reserva_id, pasajero_id, moneda_id, metodo_original,
			monto_bruto, monto_descuento, monto_total, monto_digital, estado
		)
		VALUES ($1, $2, $3, $4, 'digital', 30.00, 0.00, 30.00, 30.00, 'pagado')
	`, pagoID, reservaID, f.passenger1ID, f.monedaPENID); err != nil {
		t.Fatalf("insert pago failed: %v", err)
	}

	if _, err := f.pool.Exec(ctx, `
		INSERT INTO reembolso (
			id, pago_id, reserva_id, moneda_id, monto, destino, estado, motivo, disponible_at
		)
		VALUES
			($1, $2, $3, $4, 15.00, 'billetera', 'programado', 'Reduccion de 1 asiento', NOW() - INTERVAL '1 minute'),
			($5, $2, $3, $4, 5.00,  'billetera', 'programado', 'Credito futuro', NOW() + INTERVAL '2 hours')
	`, dueReembolsoID, pagoID, reservaID, f.monedaPENID, futureReembolsoID); err != nil {
		t.Fatalf("insert reembolso rows failed: %v", err)
	}

	// Nil CreditReleaser must skip safely.
	rNil := scheduler.NewRunner(f.pool, scheduler.Options{})
	if count, err := rNil.ReleaseScheduledCreditsOnce(ctx); err != nil || count != 0 {
		t.Fatalf("nil CreditReleaser got (%d, %v), want (0, nil)", count, err)
	}

	// Error from CreditReleaser must roll back transaction.
	failingReleaser := &recordingCreditReleaser{failErr: errors.New("wallet failure")}
	rFail := scheduler.NewRunner(f.pool, scheduler.Options{CreditReleaser: failingReleaser})
	if _, err := rFail.ReleaseScheduledCreditsOnce(ctx); err == nil {
		t.Fatal("expected error when CreditReleaser fails")
	}

	var stateAfterFail string
	if err := f.pool.QueryRow(ctx, "SELECT estado FROM reembolso WHERE id = $1", dueReembolsoID).Scan(&stateAfterFail); err != nil {
		t.Fatalf("query reembolso after fail failed: %v", err)
	}
	if stateAfterFail != "programado" {
		t.Fatalf("reembolso estado after rollback = %q, want programado", stateAfterFail)
	}

	// Successful release.
	releaser := &recordingCreditReleaser{}
	r := scheduler.NewRunner(f.pool, scheduler.Options{CreditReleaser: releaser})
	released, err := r.ReleaseScheduledCreditsOnce(ctx)
	if err != nil {
		t.Fatalf("ReleaseScheduledCreditsOnce failed: %v", err)
	}
	if released < 1 {
		t.Errorf("released = %d, want >= 1", released)
	}

	var dueState string
	var procesadoAt *time.Time
	if err := f.pool.QueryRow(ctx, "SELECT estado, procesado_at FROM reembolso WHERE id = $1", dueReembolsoID).Scan(&dueState, &procesadoAt); err != nil {
		t.Fatalf("query due reembolso failed: %v", err)
	}
	if dueState != "completado" || procesadoAt == nil {
		t.Errorf("due reembolso = (%q, %v), want (completado, non-nil procesado_at)", dueState, procesadoAt)
	}

	var futureState string
	if err := f.pool.QueryRow(ctx, "SELECT estado FROM reembolso WHERE id = $1", futureReembolsoID).Scan(&futureState); err != nil {
		t.Fatalf("query future reembolso failed: %v", err)
	}
	if futureState != "programado" {
		t.Errorf("future reembolso estado = %q, want programado", futureState)
	}

	var matchedCall bool
	for _, c := range releaser.calls {
		if c.refID == dueReembolsoID && c.userID == f.passenger1ID && c.refType == "reembolso" && c.amount == 15.00 {
			matchedCall = true
		}
	}
	if !matchedCall {
		t.Errorf("expected CreditPassengerBalance call for due reembolso %s, calls=%+v", dueReembolsoID, releaser.calls)
	}
}

type recordingRatingRecalculator struct {
	mu    sync.Mutex
	calls []ratingCall
}

type ratingCall struct {
	userID            uuid.UUID
	rolEvaluado       string
	sawPublishedInTx  bool
}

func (r *recordingRatingRecalculator) RecalculateRatingStats(ctx context.Context, tx pgx.Tx, userID uuid.UUID, rolEvaluado string) error {
	var publishedCount int
	if err := tx.QueryRow(ctx, `
		SELECT COUNT(*)
		FROM calificacion
		WHERE evaluado_id = $1
		  AND rol_evaluado = $2
		  AND publicada = TRUE
	`, userID, rolEvaluado).Scan(&publishedCount); err != nil {
		return err
	}

	r.mu.Lock()
	defer r.mu.Unlock()
	r.calls = append(r.calls, ratingCall{
		userID:           userID,
		rolEvaluado:      rolEvaluado,
		sawPublishedInTx: publishedCount > 0,
	})
	return nil
}

func TestPublishDelayedRatingsOnceIntegration(t *testing.T) {
	ctx, cancel := context.WithTimeout(context.Background(), 20*time.Second)
	defer cancel()

	f := setupIntegrationFixture(t, ctx)

	reservaID := uuid.New()
	dueCalifID := uuid.New()
	futureCalifID := uuid.New()

	f.reservaIDs = append(f.reservaIDs, reservaID)
	f.califIDs = append(f.califIDs, dueCalifID, futureCalifID)

	if _, err := f.pool.Exec(ctx, `
		INSERT INTO reserva (
			id, codigo, viaje_id, pasajero_id, fraccion_recogida, fraccion_destino,
			distancia_reservada_km, asientos_reservados, estado, precio_original, precio_total,
			moneda_id, punto_recogida, punto_destino
		)
		VALUES (
			$1, $2, $3, $4, 0.0, 1.0, 5.8, 1, 'completada', 20.00, 20.00,
			$5,
			ST_GeogFromText('SRID=4326;POINT(-77.0290 -12.1191)'),
			ST_GeogFromText('SRID=4326;POINT(-77.0365 -12.0942)')
		)
	`, reservaID, "RT"+reservaID.String()[:8], f.viajeDoneID, f.passenger1ID, f.monedaPENID); err != nil {
		t.Fatalf("insert reserva failed: %v", err)
	}

	// Due rating: passenger rates driver.
	if _, err := f.pool.Exec(ctx, `
		INSERT INTO calificacion (
			id, reserva_id, evaluador_id, evaluado_id, rol_evaluado, puntuacion, publicada, publicar_at
		)
		VALUES ($1, $2, $3, $4, 'conductor', 5, FALSE, NOW() - INTERVAL '1 minute')
	`, dueCalifID, reservaID, f.passenger1ID, f.driverID); err != nil {
		t.Fatalf("insert due calificacion failed: %v", err)
	}

	// Future rating: driver rates passenger.
	if _, err := f.pool.Exec(ctx, `
		INSERT INTO calificacion (
			id, reserva_id, evaluador_id, evaluado_id, rol_evaluado, puntuacion, publicada, publicar_at
		)
		VALUES ($1, $2, $3, $4, 'pasajero', 4, FALSE, NOW() + INTERVAL '2 hours')
	`, futureCalifID, reservaID, f.driverID, f.passenger1ID); err != nil {
		t.Fatalf("insert future calificacion failed: %v", err)
	}

	recalc := &recordingRatingRecalculator{}
	r := scheduler.NewRunner(f.pool, scheduler.Options{
		RatingRecalculator: recalc,
	})

	published, err := r.PublishDelayedRatingsOnce(ctx)
	if err != nil {
		t.Fatalf("PublishDelayedRatingsOnce failed: %v", err)
	}
	if published < 1 {
		t.Errorf("published = %d, want >= 1", published)
	}

	var duePub, futurePub bool
	if err := f.pool.QueryRow(ctx, "SELECT publicada FROM calificacion WHERE id = $1", dueCalifID).Scan(&duePub); err != nil {
		t.Fatalf("query due calificacion failed: %v", err)
	}
	if err := f.pool.QueryRow(ctx, "SELECT publicada FROM calificacion WHERE id = $1", futureCalifID).Scan(&futurePub); err != nil {
		t.Fatalf("query future calificacion failed: %v", err)
	}
	if !duePub {
		t.Error("due calificacion.publicada = false, want true")
	}
	if futurePub {
		t.Error("future calificacion.publicada = true, want false")
	}

	var matched bool
	for _, c := range recalc.calls {
		if c.userID == f.driverID && c.rolEvaluado == "conductor" && c.sawPublishedInTx {
			matched = true
		}
	}
	if !matched {
		t.Errorf("expected RecalculateRatingStats call for driver %s seeing publicada=true inside tx, calls=%+v", f.driverID, recalc.calls)
	}
}

func TestDeactivateExpiredTrackingLinksOnceIntegration(t *testing.T) {
	ctx, cancel := context.WithTimeout(context.Background(), 20*time.Second)
	defer cancel()

	f := setupIntegrationFixture(t, ctx)

	expiredLinkID := uuid.New()
	activeLinkID := uuid.New()
	f.trackingIDs = append(f.trackingIDs, expiredLinkID, activeLinkID)

	if _, err := f.pool.Exec(ctx, `
		INSERT INTO enlace_seguimiento (id, viaje_id, creado_por, token_hash, tipo_creador, activo, expira_at)
		VALUES
			($1, $2, $3, $4, 'conductor', TRUE, NOW() - INTERVAL '1 minute'),
			($5, $2, $3, $6, 'conductor', TRUE, NOW() + INTERVAL '1 hour')
	`,
		expiredLinkID, f.viajeActiveID, f.driverID, "hash_exp_"+expiredLinkID.String(),
		activeLinkID, "hash_act_"+activeLinkID.String(),
	); err != nil {
		t.Fatalf("insert enlace_seguimiento rows failed: %v", err)
	}

	r := scheduler.NewRunner(f.pool, scheduler.Options{})
	deactivated, err := r.DeactivateExpiredTrackingLinksOnce(ctx)
	if err != nil {
		t.Fatalf("DeactivateExpiredTrackingLinksOnce failed: %v", err)
	}
	if deactivated < 1 {
		t.Errorf("deactivated = %d, want >= 1", deactivated)
	}

	var expActivo, actActivo bool
	if err := f.pool.QueryRow(ctx, "SELECT activo FROM enlace_seguimiento WHERE id = $1", expiredLinkID).Scan(&expActivo); err != nil {
		t.Fatalf("query expired tracking link failed: %v", err)
	}
	if err := f.pool.QueryRow(ctx, "SELECT activo FROM enlace_seguimiento WHERE id = $1", activeLinkID).Scan(&actActivo); err != nil {
		t.Fatalf("query active tracking link failed: %v", err)
	}
	if expActivo {
		t.Error("expired enlace_seguimiento.activo = true, want false")
	}
	if !actActivo {
		t.Error("active enlace_seguimiento.activo = false, want true")
	}
}

func TestPurgeOldGPSPointsOnceIntegration(t *testing.T) {
	ctx, cancel := context.WithTimeout(context.Background(), 20*time.Second)
	defer cancel()

	f := setupIntegrationFixture(t, ctx)

	oldDoneEventID := uuid.New()
	oldCancelEventID := uuid.New()
	recentDoneEventID := uuid.New()
	oldActiveEventID := uuid.New()

	if _, err := f.pool.Exec(ctx, `
		INSERT INTO viaje_ubicacion_gps (viaje_id, conductor_id, client_event_id, ubicacion, registrado_at)
		VALUES
			($1, $2, $3, ST_GeogFromText('SRID=4326;POINT(-77.0290 -12.1191)'), NOW() - INTERVAL '35 days'),
			($4, $2, $5, ST_GeogFromText('SRID=4326;POINT(-77.0290 -12.1191)'), NOW() - INTERVAL '31 days'),
			($1, $2, $6, ST_GeogFromText('SRID=4326;POINT(-77.0320 -12.1060)'), NOW() - INTERVAL '5 days'),
			($7, $2, $8, ST_GeogFromText('SRID=4326;POINT(-77.0365 -12.0942)'), NOW() - INTERVAL '40 days')
	`,
		f.viajeDoneID, f.driverID, oldDoneEventID,
		f.viajeCancelID, oldCancelEventID,
		recentDoneEventID,
		f.viajeActiveID, oldActiveEventID,
	); err != nil {
		t.Fatalf("insert viaje_ubicacion_gps rows failed: %v", err)
	}

	r := scheduler.NewRunner(f.pool, scheduler.Options{})
	deleted, err := r.PurgeOldGPSPointsOnce(ctx)
	if err != nil {
		t.Fatalf("PurgeOldGPSPointsOnce failed: %v", err)
	}
	if deleted < 2 {
		t.Errorf("deleted = %d, want >= 2", deleted)
	}

	assertExists := func(eventID uuid.UUID, want bool) {
		t.Helper()
		var cnt int
		if err := f.pool.QueryRow(ctx, "SELECT COUNT(*) FROM viaje_ubicacion_gps WHERE client_event_id = $1", eventID).Scan(&cnt); err != nil {
			t.Fatalf("query gps point %s failed: %v", eventID, err)
		}
		if (cnt > 0) != want {
			t.Errorf("gps point %s exists=%v, want %v", eventID, cnt > 0, want)
		}
	}

	assertExists(oldDoneEventID, false)
	assertExists(oldCancelEventID, false)
	assertExists(recentDoneEventID, true)
	assertExists(oldActiveEventID, true)
}
