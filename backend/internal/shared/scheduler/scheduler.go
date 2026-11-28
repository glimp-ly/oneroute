package scheduler

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"log/slog"
	"reflect"
	"strconv"
	"strings"
	"sync"
	"time"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"

	"github.com/glimp-ly/oneroute/backend/internal/shared/db"
	"github.com/glimp-ly/oneroute/backend/internal/shared/realtime"
)

const (
	DefaultHoldPollInterval         = 5 * time.Second
	DefaultCreditsPollInterval      = 60 * time.Second
	DefaultRatingsPollInterval      = 60 * time.Second
	DefaultTrackingPollInterval     = 60 * time.Second
	DefaultGPSRetentionPollInterval = 60 * time.Minute
	DefaultGPSRetentionDays         = 30

	configKeyGPSRetentionDays = "gps.retencion_dias"
)

var errNilPool = errors.New("scheduler: pgxpool must not be nil")

// EventPublisher broadcasts real-time events to WebSocket rooms (satisfied by *realtime.Hub).
type EventPublisher interface {
	Publish(room string, event string, payload any)
}

// CreditReleaser credits deferred passenger refunds into the passenger wallet inside an active transaction.
type CreditReleaser interface {
	CreditPassengerBalance(ctx context.Context, tx pgx.Tx, userID uuid.UUID, refType string, refID uuid.UUID, amount float64, desc string) error
}

// RatingRecalculator updates cached rating aggregates after delayed ratings are published.
type RatingRecalculator interface {
	RecalculateRatingStats(ctx context.Context, tx pgx.Tx, userID uuid.UUID, rolEvaluado string) error
}

// Options configures background worker intervals and optional domain callbacks.
type Options struct {
	HoldPollInterval         time.Duration
	CreditsPollInterval      time.Duration
	RatingsPollInterval      time.Duration
	TrackingPollInterval     time.Duration
	GPSRetentionPollInterval time.Duration
	Enabled                  bool
	Logger                   *slog.Logger
	Publisher                EventPublisher
	CreditReleaser           CreditReleaser
	RatingRecalculator       RatingRecalculator
}

// Runner executes OneRoute periodic maintenance workers and single-pass jobs.
type Runner struct {
	pool               *pgxpool.Pool
	opts               Options
	logger             *slog.Logger
	publisher          EventPublisher
	creditReleaser     CreditReleaser
	ratingRecalculator RatingRecalculator

	mu      sync.Mutex
	started bool
	wg      sync.WaitGroup
}

// NewRunner constructs a Runner applying default intervals and loggers for unset fields.
func NewRunner(pool *pgxpool.Pool, opts Options) *Runner {
	if opts.HoldPollInterval <= 0 {
		opts.HoldPollInterval = DefaultHoldPollInterval
	}
	if opts.CreditsPollInterval <= 0 {
		opts.CreditsPollInterval = DefaultCreditsPollInterval
	}
	if opts.RatingsPollInterval <= 0 {
		opts.RatingsPollInterval = DefaultRatingsPollInterval
	}
	if opts.TrackingPollInterval <= 0 {
		opts.TrackingPollInterval = DefaultTrackingPollInterval
	}
	if opts.GPSRetentionPollInterval <= 0 {
		opts.GPSRetentionPollInterval = DefaultGPSRetentionPollInterval
	}
	if opts.Logger == nil {
		opts.Logger = slog.Default()
	}
	if isNilInterface(opts.Publisher) {
		opts.Publisher = nil
	}
	if isNilInterface(opts.CreditReleaser) {
		opts.CreditReleaser = nil
	}
	if isNilInterface(opts.RatingRecalculator) {
		opts.RatingRecalculator = nil
	}

	return &Runner{
		pool:               pool,
		opts:               opts,
		logger:             opts.Logger,
		publisher:          opts.Publisher,
		creditReleaser:     opts.CreditReleaser,
		ratingRecalculator: opts.RatingRecalculator,
	}
}

// Options returns a copy of the normalized runner configuration.
func (r *Runner) Options() Options {
	return r.opts
}

// Start launches the 5 background ticker goroutines when Enabled is true.
// Calling Start when disabled or more than once is a safe no-op.
func (r *Runner) Start(ctx context.Context) {
	if !r.opts.Enabled {
		return
	}
	if ctx == nil {
		ctx = context.Background()
	}

	r.mu.Lock()
	if r.started {
		r.mu.Unlock()
		return
	}
	r.started = true
	r.wg.Add(5)
	r.mu.Unlock()

	go r.runWorker(ctx, "expire_requests_and_holds", r.opts.HoldPollInterval, func(c context.Context) error {
		reqCount, holdCount, err := r.ExpireRequestsAndHoldsOnce(c)
		if err == nil && (reqCount > 0 || holdCount > 0) {
			r.logger.Info("scheduler expired requests and holds",
				slog.Int("expired_requests", reqCount),
				slog.Int("expired_holds", holdCount),
			)
		}
		return err
	})

	go r.runWorker(ctx, "release_scheduled_credits", r.opts.CreditsPollInterval, func(c context.Context) error {
		released, err := r.ReleaseScheduledCreditsOnce(c)
		if err == nil && released > 0 {
			r.logger.Info("scheduler released scheduled credits",
				slog.Int("released", released),
			)
		}
		return err
	})

	go r.runWorker(ctx, "publish_delayed_ratings", r.opts.RatingsPollInterval, func(c context.Context) error {
		published, err := r.PublishDelayedRatingsOnce(c)
		if err == nil && published > 0 {
			r.logger.Info("scheduler published delayed ratings",
				slog.Int("published", published),
			)
		}
		return err
	})

	go r.runWorker(ctx, "deactivate_expired_tracking_links", r.opts.TrackingPollInterval, func(c context.Context) error {
		deactivated, err := r.DeactivateExpiredTrackingLinksOnce(c)
		if err == nil && deactivated > 0 {
			r.logger.Info("scheduler deactivated expired tracking links",
				slog.Int("deactivated", deactivated),
			)
		}
		return err
	})

	go r.runWorker(ctx, "purge_old_gps_points", r.opts.GPSRetentionPollInterval, func(c context.Context) error {
		deleted, err := r.PurgeOldGPSPointsOnce(c)
		if err == nil && deleted > 0 {
			r.logger.Info("scheduler purged old gps points",
				slog.Int64("deleted", deleted),
			)
		}
		return err
	})
}

// Wait blocks until all launched worker goroutines exit after ctx cancellation.
func (r *Runner) Wait() {
	r.wg.Wait()
}

func (r *Runner) runWorker(ctx context.Context, name string, interval time.Duration, fn func(context.Context) error) {
	defer r.wg.Done()
	if ctx.Err() != nil {
		return
	}

	ticker := time.NewTicker(interval)
	defer ticker.Stop()

	for {
		select {
		case <-ctx.Done():
			return
		case <-ticker.C:
			if ctx.Err() != nil {
				return
			}
			if err := fn(ctx); err != nil && !errors.Is(err, context.Canceled) {
				r.logger.Error("scheduler worker iteration failed",
					slog.String("worker", name),
					slog.String("error", err.Error()),
				)
			}
		}
	}
}

type wsNotification struct {
	room    string
	event   string
	payload any
}

// ExpireRequestsAndHoldsOnce expires overdue booking requests and 60s reservation holds,
// increments attempts up to 3, logs reserva_evento rows, and publishes "request.expired" after commit.
func (r *Runner) ExpireRequestsAndHoldsOnce(ctx context.Context) (expiredRequests int, expiredHolds int, err error) {
	if r.pool == nil {
		return 0, 0, errNilPool
	}

	type expiredReq struct {
		id         uuid.UUID
		viajeID    uuid.UUID
		pasajeroID uuid.UUID
	}
	type expiredHold struct {
		id          uuid.UUID
		viajeID     uuid.UUID
		pasajeroID  uuid.UUID
		solicitudID *uuid.UUID
	}

	var notifications []wsNotification

	err = db.WithTx(ctx, r.pool, func(tx pgx.Tx) error {
		reqRows, qErr := tx.Query(ctx, `
			SELECT id, viaje_id, pasajero_id
			FROM solicitud_reserva
			WHERE estado = 'pendiente'
			  AND expira_at IS NOT NULL
			  AND expira_at <= NOW()
			FOR UPDATE SKIP LOCKED
		`)
		if qErr != nil {
			return fmt.Errorf("scheduler: select expired requests: %w", qErr)
		}

		var reqs []expiredReq
		for reqRows.Next() {
			var item expiredReq
			if sErr := reqRows.Scan(&item.id, &item.viajeID, &item.pasajeroID); sErr != nil {
				reqRows.Close()
				return fmt.Errorf("scheduler: scan expired request: %w", sErr)
			}
			reqs = append(reqs, item)
		}
		if rErr := reqRows.Err(); rErr != nil {
			reqRows.Close()
			return fmt.Errorf("scheduler: iterate expired requests: %w", rErr)
		}
		reqRows.Close()

		for _, req := range reqs {
			if _, uErr := tx.Exec(ctx, `
				UPDATE solicitud_reserva
				SET estado = 'expirada',
				    intentos_consumidos = LEAST(intentos_consumidos + 1, 3),
				    respuesta_at = NOW(),
				    motivo_respuesta = COALESCE(NULLIF(motivo_respuesta, ''), 'Solicitud expirada por tiempo limite')
				WHERE id = $1
			`, req.id); uErr != nil {
				return fmt.Errorf("scheduler: expire solicitud_reserva %s: %w", req.id, uErr)
			}

			if _, uErr := tx.Exec(ctx, `
				UPDATE solicitud_intento
				SET resultado = 'expirada',
				    finalizado_at = NOW(),
				    motivo = COALESCE(NULLIF(motivo, ''), 'Solicitud expirada por tiempo limite')
				WHERE solicitud_id = $1
				  AND resultado = 'pendiente'
			`, req.id); uErr != nil {
				return fmt.Errorf("scheduler: expire solicitud_intento for %s: %w", req.id, uErr)
			}

			notifications = append(notifications, wsNotification{
				room:  realtime.UserRoom(req.pasajeroID),
				event: "request.expired",
				payload: map[string]any{
					"kind":         "request",
					"solicitud_id": req.id.String(),
					"viaje_id":     req.viajeID.String(),
					"pasajero_id":  req.pasajeroID.String(),
					"estado":       "expirada",
				},
			})
			expiredRequests++
		}

		holdRows, qErr := tx.Query(ctx, `
			SELECT id, viaje_id, pasajero_id, solicitud_id
			FROM reserva
			WHERE estado = 'esperando_pasajero'
			  AND hold_expira_at IS NOT NULL
			  AND hold_expira_at <= NOW()
			FOR UPDATE SKIP LOCKED
		`)
		if qErr != nil {
			return fmt.Errorf("scheduler: select expired holds: %w", qErr)
		}

		var holds []expiredHold
		for holdRows.Next() {
			var item expiredHold
			if sErr := holdRows.Scan(&item.id, &item.viajeID, &item.pasajeroID, &item.solicitudID); sErr != nil {
				holdRows.Close()
				return fmt.Errorf("scheduler: scan expired hold: %w", sErr)
			}
			holds = append(holds, item)
		}
		if rErr := holdRows.Err(); rErr != nil {
			holdRows.Close()
			return fmt.Errorf("scheduler: iterate expired holds: %w", rErr)
		}
		holdRows.Close()

		for _, hold := range holds {
			if _, uErr := tx.Exec(ctx, `
				UPDATE reserva
				SET estado = 'expirada',
				    updated_at = NOW()
				WHERE id = $1
			`, hold.id); uErr != nil {
				return fmt.Errorf("scheduler: expire reserva hold %s: %w", hold.id, uErr)
			}

			eventMeta, mErr := json.Marshal(map[string]string{
				"origen":   "scheduler",
				"viaje_id": hold.viajeID.String(),
			})
			if mErr != nil {
				return fmt.Errorf("scheduler: marshal reserva_evento metadata: %w", mErr)
			}

			if _, iErr := tx.Exec(ctx, `
				INSERT INTO reserva_evento (
					reserva_id, actor_id, tipo, estado_anterior, estado_nuevo, descripcion, datos
				)
				VALUES ($1, NULL, 'hold_expirado', 'esperando_pasajero', 'expirada', $2, $3::jsonb)
			`, hold.id, "Retencion temporal expirada por tiempo limite", string(eventMeta)); iErr != nil {
				return fmt.Errorf("scheduler: insert reserva_evento for %s: %w", hold.id, iErr)
			}

			payload := map[string]any{
				"kind":        "hold",
				"reserva_id":  hold.id.String(),
				"viaje_id":    hold.viajeID.String(),
				"pasajero_id": hold.pasajeroID.String(),
				"estado":      "expirada",
			}
			if hold.solicitudID != nil {
				payload["solicitud_id"] = hold.solicitudID.String()
			}
			notifications = append(notifications, wsNotification{
				room:    realtime.UserRoom(hold.pasajeroID),
				event:   "request.expired",
				payload: payload,
			})
			expiredHolds++
		}

		return nil
	})
	if err != nil {
		return 0, 0, err
	}

	if r.publisher != nil {
		for _, n := range notifications {
			r.publisher.Publish(n.room, n.event, n.payload)
		}
	}

	return expiredRequests, expiredHolds, nil
}

// ReleaseScheduledCreditsOnce releases scheduled refunds whose disponible_at has elapsed,
// crediting the passenger wallet via CreditReleaser inside the same transaction.
func (r *Runner) ReleaseScheduledCreditsOnce(ctx context.Context) (int, error) {
	if r.creditReleaser == nil {
		r.logger.Warn("scheduler skipping scheduled credits release because CreditReleaser is nil")
		return 0, nil
	}
	if r.pool == nil {
		return 0, errNilPool
	}

	type dueCredit struct {
		id         uuid.UUID
		pagoID     uuid.UUID
		reservaID  uuid.UUID
		pasajeroID uuid.UUID
		monto      float64
		destino    string
		motivo     string
	}

	var released int
	err := db.WithTx(ctx, r.pool, func(tx pgx.Tx) error {
		rows, qErr := tx.Query(ctx, `
			SELECT re.id, re.pago_id, re.reserva_id, re.monto::float8, re.destino, re.motivo, res.pasajero_id
			FROM reembolso re
			JOIN reserva res ON res.id = re.reserva_id
			WHERE re.estado = 'programado'
			  AND re.disponible_at IS NOT NULL
			  AND re.disponible_at <= NOW()
			FOR UPDATE OF re SKIP LOCKED
		`)
		if qErr != nil {
			return fmt.Errorf("scheduler: select scheduled credits: %w", qErr)
		}

		var credits []dueCredit
		for rows.Next() {
			var item dueCredit
			if sErr := rows.Scan(
				&item.id,
				&item.pagoID,
				&item.reservaID,
				&item.monto,
				&item.destino,
				&item.motivo,
				&item.pasajeroID,
			); sErr != nil {
				rows.Close()
				return fmt.Errorf("scheduler: scan scheduled credit: %w", sErr)
			}
			credits = append(credits, item)
		}
		if rErr := rows.Err(); rErr != nil {
			rows.Close()
			return fmt.Errorf("scheduler: iterate scheduled credits: %w", rErr)
		}
		rows.Close()

		for _, credit := range credits {
			desc := strings.TrimSpace(credit.motivo)
			if desc == "" {
				desc = "Credito diferido liberado a billetera pasajero"
			}

			if credit.monto > 0 {
				if cErr := r.creditReleaser.CreditPassengerBalance(
					ctx,
					tx,
					credit.pasajeroID,
					"reembolso",
					credit.id,
					credit.monto,
					desc,
				); cErr != nil {
					return fmt.Errorf("scheduler: credit passenger balance for reembolso %s: %w", credit.id, cErr)
				}
			}

			if _, uErr := tx.Exec(ctx, `
				UPDATE reembolso
				SET estado = 'completado',
				    procesado_at = NOW()
				WHERE id = $1
			`, credit.id); uErr != nil {
				return fmt.Errorf("scheduler: mark reembolso %s completado: %w", credit.id, uErr)
			}
			released++
		}

		return nil
	})
	if err != nil {
		return 0, err
	}

	return released, nil
}

// PublishDelayedRatingsOnce publishes ratings whose publicar_at has elapsed
// and recalculates rating aggregates when RatingRecalculator is configured.
func (r *Runner) PublishDelayedRatingsOnce(ctx context.Context) (int, error) {
	if r.pool == nil {
		return 0, errNilPool
	}

	type ratingRow struct {
		id          uuid.UUID
		evaluadoID  uuid.UUID
		rolEvaluado string
	}
	type recalcTarget struct {
		evaluadoID  uuid.UUID
		rolEvaluado string
	}

	var published int
	err := db.WithTx(ctx, r.pool, func(tx pgx.Tx) error {
		rows, qErr := tx.Query(ctx, `
			SELECT id, evaluado_id, rol_evaluado
			FROM calificacion
			WHERE publicada = FALSE
			  AND publicar_at IS NOT NULL
			  AND publicar_at <= NOW()
			FOR UPDATE SKIP LOCKED
		`)
		if qErr != nil {
			return fmt.Errorf("scheduler: select delayed ratings: %w", qErr)
		}

		var items []ratingRow
		for rows.Next() {
			var item ratingRow
			if sErr := rows.Scan(&item.id, &item.evaluadoID, &item.rolEvaluado); sErr != nil {
				rows.Close()
				return fmt.Errorf("scheduler: scan delayed rating: %w", sErr)
			}
			items = append(items, item)
		}
		if rErr := rows.Err(); rErr != nil {
			rows.Close()
			return fmt.Errorf("scheduler: iterate delayed ratings: %w", rErr)
		}
		rows.Close()

		if len(items) == 0 {
			return nil
		}

		ids := make([]uuid.UUID, 0, len(items))
		seenTargets := make(map[recalcTarget]struct{}, len(items))
		targets := make([]recalcTarget, 0, len(items))
		for _, item := range items {
			ids = append(ids, item.id)
			target := recalcTarget{
				evaluadoID:  item.evaluadoID,
				rolEvaluado: item.rolEvaluado,
			}
			if _, ok := seenTargets[target]; !ok {
				seenTargets[target] = struct{}{}
				targets = append(targets, target)
			}
		}

		if _, uErr := tx.Exec(ctx, `
			UPDATE calificacion
			SET publicada = TRUE
			WHERE id = ANY($1)
		`, ids); uErr != nil {
			return fmt.Errorf("scheduler: publish ratings: %w", uErr)
		}

		if r.ratingRecalculator != nil {
			for _, target := range targets {
				if recErr := r.ratingRecalculator.RecalculateRatingStats(ctx, tx, target.evaluadoID, target.rolEvaluado); recErr != nil {
					return fmt.Errorf("scheduler: recalculate ratings for %s (%s): %w", target.evaluadoID, target.rolEvaluado, recErr)
				}
			}
		}

		published = len(items)
		return nil
	})
	if err != nil {
		return 0, err
	}

	return published, nil
}

// DeactivateExpiredTrackingLinksOnce disables active tracking links whose expira_at has elapsed.
func (r *Runner) DeactivateExpiredTrackingLinksOnce(ctx context.Context) (int, error) {
	if r.pool == nil {
		return 0, errNilPool
	}

	var deactivated int
	err := db.WithTx(ctx, r.pool, func(tx pgx.Tx) error {
		rows, qErr := tx.Query(ctx, `
			SELECT id
			FROM enlace_seguimiento
			WHERE activo = TRUE
			  AND expira_at <= NOW()
			FOR UPDATE SKIP LOCKED
		`)
		if qErr != nil {
			return fmt.Errorf("scheduler: select expired tracking links: %w", qErr)
		}

		var ids []uuid.UUID
		for rows.Next() {
			var id uuid.UUID
			if sErr := rows.Scan(&id); sErr != nil {
				rows.Close()
				return fmt.Errorf("scheduler: scan expired tracking link: %w", sErr)
			}
			ids = append(ids, id)
		}
		if rErr := rows.Err(); rErr != nil {
			rows.Close()
			return fmt.Errorf("scheduler: iterate expired tracking links: %w", rErr)
		}
		rows.Close()

		if len(ids) == 0 {
			return nil
		}

		if _, uErr := tx.Exec(ctx, `
			UPDATE enlace_seguimiento
			SET activo = FALSE
			WHERE id = ANY($1)
		`, ids); uErr != nil {
			return fmt.Errorf("scheduler: deactivate tracking links: %w", uErr)
		}

		deactivated = len(ids)
		return nil
	})
	if err != nil {
		return 0, err
	}

	return deactivated, nil
}

// PurgeOldGPSPointsOnce deletes GPS telemetry rows older than gps.retencion_dias (default 30)
// belonging to completed or cancelled trips.
func (r *Runner) PurgeOldGPSPointsOnce(ctx context.Context) (int64, error) {
	if r.pool == nil {
		return 0, errNilPool
	}

	var deleted int64
	err := db.WithTx(ctx, r.pool, func(tx pgx.Tx) error {
		retentionDays := readRetentionDays(ctx, tx, r.logger)

		tag, dErr := tx.Exec(ctx, `
			DELETE FROM viaje_ubicacion_gps g
			USING viaje v
			WHERE g.viaje_id = v.id
			  AND v.estado IN ('completado', 'cancelado')
			  AND g.registrado_at < NOW() - make_interval(days => $1)
		`, retentionDays)
		if dErr != nil {
			return fmt.Errorf("scheduler: purge old gps points: %w", dErr)
		}

		deleted = tag.RowsAffected()
		return nil
	})
	if err != nil {
		return 0, err
	}

	return deleted, nil
}

func readRetentionDays(ctx context.Context, tx pgx.Tx, logger *slog.Logger) int {
	var raw string
	err := tx.QueryRow(ctx, `
		SELECT valor::text
		FROM configuracion
		WHERE clave = $1
	`, configKeyGPSRetentionDays).Scan(&raw)
	if err != nil {
		if !errors.Is(err, pgx.ErrNoRows) {
			logger.Warn("scheduler failed to read gps retention config; using default",
				slog.String("clave", configKeyGPSRetentionDays),
				slog.String("error", err.Error()),
			)
		}
		return DefaultGPSRetentionDays
	}

	if days, ok := parseRetentionDaysJSON(raw); ok {
		return days
	}

	logger.Warn("scheduler invalid gps retention config value; using default",
		slog.String("clave", configKeyGPSRetentionDays),
		slog.String("valor", raw),
	)
	return DefaultGPSRetentionDays
}

func parseRetentionDaysJSON(raw string) (int, bool) {
	var num int
	if err := json.Unmarshal([]byte(raw), &num); err == nil && num > 0 {
		return num, true
	}

	var str string
	if err := json.Unmarshal([]byte(raw), &str); err == nil {
		if parsed, convErr := strconv.Atoi(strings.TrimSpace(str)); convErr == nil && parsed > 0 {
			return parsed, true
		}
	}

	return 0, false
}

func isNilInterface(v any) bool {
	if v == nil {
		return true
	}
	rv := reflect.ValueOf(v)
	switch rv.Kind() {
	case reflect.Ptr, reflect.Interface, reflect.Func, reflect.Map, reflect.Slice, reflect.Chan:
		return rv.IsNil()
	default:
		return false
	}
}
