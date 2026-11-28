package main

import (
	"context"
	"errors"
	"fmt"
	"log/slog"
	"net/http"
	"os"
	"os/signal"
	"strconv"
	"strings"
	"syscall"
	"time"

	"github.com/go-chi/chi/v5"
	"github.com/google/uuid"
	"github.com/jackc/pgx/v5/pgxpool"

	"github.com/glimp-ly/oneroute/backend/internal/shared/auth"
	"github.com/glimp-ly/oneroute/backend/internal/shared/config"
	"github.com/glimp-ly/oneroute/backend/internal/shared/db"
	"github.com/glimp-ly/oneroute/backend/internal/shared/geo"
	"github.com/glimp-ly/oneroute/backend/internal/shared/httpx"
	"github.com/glimp-ly/oneroute/backend/internal/shared/providers"
	"github.com/glimp-ly/oneroute/backend/internal/shared/realtime"
	"github.com/glimp-ly/oneroute/backend/internal/shared/scheduler"
	"github.com/glimp-ly/oneroute/backend/internal/shared/storage"
)

const (
	defaultRESTTimeout       = 15 * time.Second
	defaultShutdownTimeout   = 10 * time.Second
	defaultReadHeaderTimeout = 10 * time.Second
)

// AppDependencies groups initialized shared infrastructure and providers.
type AppDependencies struct {
	Config           *config.Config
	Logger           *slog.Logger
	Pool             *pgxpool.Pool
	DBHealthCheck    func(ctx context.Context) (string, error)
	Storage          storage.StorageProvider
	Routing          geo.RoutingProvider
	PaymentGateway   providers.PaymentGateway
	IdentityProvider providers.IdentityProvider
	OTPProvider      providers.OTPProvider
	Hub              *realtime.Hub
	PushProvider     realtime.PushProvider
	AuthManager      *auth.Manager
	AuthMiddleware   *auth.Middleware
	Scheduler        *scheduler.Runner
}

// NewRouter builds the HTTP and WebSocket router using shared middlewares.
func NewRouter(deps AppDependencies) http.Handler {
	logger := deps.Logger
	if logger == nil {
		logger = slog.Default()
	}

	var allowedOrigins []string
	timezone := db.DefaultTimezone
	if deps.Config != nil {
		allowedOrigins = deps.Config.App.CORSAllowedOrigins
		if deps.Config.App.Timezone != "" {
			timezone = deps.Config.App.Timezone
		}
	}

	r := chi.NewRouter()
	r.Use(httpx.RequestID)
	r.Use(httpx.StructuredLogger(logger))
	r.Use(httpx.Recoverer(logger))
	r.Use(httpx.CORS(allowedOrigins))

	r.With(httpx.Timeout(defaultRESTTimeout)).Get("/healthz", healthHandler(deps, timezone))

	r.Route("/api/v1", func(api chi.Router) {
		if deps.Hub != nil && deps.AuthMiddleware != nil {
			// WebSocket connections are long-lived, so they intentionally omit httpx.Timeout.
			api.Get("/ws", deps.Hub.ServeWS(
				deps.AuthMiddleware.AuthenticateToken,
				defaultRoomAuthorizer(deps.Pool),
				nil,
			))
		}

		api.Group(func(rest chi.Router) {
			rest.Use(httpx.Timeout(defaultRESTTimeout))

			// Module mount hooks (each module owns its routes.go and wires here with coordination):
			// - auth_identity: authIdentityHandler.RegisterRoutes(rest, deps.AuthMiddleware)
			// - routes_trips: routesTripsHandler.RegisterRoutes(rest, deps.AuthMiddleware)
			// - search_bookings: searchBookingsHandler.RegisterRoutes(rest, deps.AuthMiddleware)
			// - active_trip_gps: activeTripGPSHandler.RegisterRoutes(rest, deps.AuthMiddleware)
			// - payments_wallets: paymentsWalletsHandler.RegisterRoutes(rest, deps.AuthMiddleware)
			// - messaging_safety_support: messagingSupportHandler.RegisterRoutes(rest, deps.AuthMiddleware)
			_ = rest
		})
	})

	return r
}

func healthHandler(deps AppDependencies, timezone string) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		ctx := r.Context()

		postgisVersion, dbErr := checkDatabaseHealth(ctx, deps)
		storageStatus := "ok"
		storageErr := checkStorageHealth(ctx, deps.Storage)

		if dbErr != nil || storageErr != nil {
			errParts := make([]string, 0, 2)
			dbStatus := postgisVersion
			if dbErr != nil {
				dbStatus = "error"
				errParts = append(errParts, "db: "+dbErr.Error())
			}
			if storageErr != nil {
				storageStatus = "error"
				errParts = append(errParts, "storage: "+storageErr.Error())
			}
			httpx.WriteJSON(w, http.StatusServiceUnavailable, map[string]any{
				"status":   "unhealthy",
				"postgis":  dbStatus,
				"storage":  storageStatus,
				"timezone": timezone,
				"details":  strings.Join(errParts, "; "),
			}, nil)
			return
		}

		httpx.WriteJSON(w, http.StatusOK, map[string]any{
			"status":   "ok",
			"postgis":  postgisVersion,
			"storage":  storageStatus,
			"timezone": timezone,
		}, nil)
	}
}

func checkDatabaseHealth(ctx context.Context, deps AppDependencies) (string, error) {
	if deps.DBHealthCheck != nil {
		return deps.DBHealthCheck(ctx)
	}
	if deps.Pool == nil {
		return "", errors.New("database pool is nil")
	}
	var version string
	if err := deps.Pool.QueryRow(ctx, "SELECT PostGIS_Version()").Scan(&version); err != nil {
		return "", err
	}
	return version, nil
}

func checkStorageHealth(ctx context.Context, store storage.StorageProvider) error {
	if store == nil {
		return errors.New("storage provider is nil")
	}
	return store.HealthCheck(ctx)
}

func defaultRoomAuthorizer(pool *pgxpool.Pool) realtime.RoomAuthorizer {
	return func(ctx context.Context, userID uuid.UUID, room string) error {
		prefix, rawID, ok := strings.Cut(room, ":")
		if !ok {
			return httpx.BadRequest("invalid room format")
		}
		targetID, err := uuid.Parse(rawID)
		if err != nil {
			return httpx.BadRequest("invalid room identifier")
		}

		switch prefix {
		case "user":
			if targetID != userID {
				return httpx.Forbidden("cannot subscribe to another user room")
			}
			return nil
		case "trip":
			if pool == nil {
				return nil
			}
			var allowed bool
			err := pool.QueryRow(ctx, `
				SELECT EXISTS (
					SELECT 1 FROM viaje WHERE id = $1 AND conductor_id = $2
					UNION ALL
					SELECT 1 FROM reserva WHERE viaje_id = $1 AND pasajero_id = $2
					  AND estado IN ('esperando_pasajero', 'confirmada', 'en_curso')
				)`, targetID, userID).Scan(&allowed)
			if err != nil {
				return err
			}
			if !allowed {
				return httpx.Forbidden("not authorized for trip room")
			}
			return nil
		case "reservation":
			if pool == nil {
				return nil
			}
			var allowed bool
			err := pool.QueryRow(ctx, `
				SELECT EXISTS (
					SELECT 1
					FROM reserva r
					JOIN viaje v ON v.id = r.viaje_id
					WHERE r.id = $1 AND (r.pasajero_id = $2 OR v.conductor_id = $2)
				)`, targetID, userID).Scan(&allowed)
			if err != nil {
				return err
			}
			if !allowed {
				return httpx.Forbidden("not authorized for reservation room")
			}
			return nil
		default:
			return httpx.BadRequest("unsupported room prefix")
		}
	}
}

func buildDependencies(ctx context.Context, cfg *config.Config, logger *slog.Logger) (*AppDependencies, error) {
	pool, err := db.NewPool(ctx, cfg.DB.DatabaseURL, cfg.App.Timezone)
	if err != nil {
		return nil, fmt.Errorf("init db pool: %w", err)
	}

	store, err := storage.NewMinIOStorage(storage.MinIOConfig{
		Endpoint:            cfg.Storage.Endpoint,
		PublicURL:           cfg.Storage.PublicURL,
		AccessKey:           cfg.Storage.AccessKey,
		SecretKey:           cfg.Storage.SecretKey,
		PublicBucket:        cfg.Storage.BucketPublic,
		PrivateBucket:       cfg.Storage.BucketPrivate,
		UseSSL:              cfg.Storage.UseSSL,
		DefaultPresignedTTL: cfg.Storage.PresignedTTL,
	})
	if err != nil {
		pool.Close()
		return nil, fmt.Errorf("init storage: %w", err)
	}

	routing, err := geo.NewRoutingProvider(
		cfg.Routing.Provider,
		cfg.Routing.OSRMBaseURL,
		cfg.Routing.OSRMLocalURL,
		cfg.Routing.Timeout,
	)
	if err != nil {
		pool.Close()
		return nil, fmt.Errorf("init routing provider: %w", err)
	}

	authMgr, err := auth.NewManager(
		cfg.JWT.AccessSecret,
		cfg.JWT.RefreshSecret,
		cfg.JWT.AccessTTL,
		cfg.JWT.RefreshTTL,
	)
	if err != nil {
		pool.Close()
		return nil, fmt.Errorf("init auth manager: %w", err)
	}

	hub := realtime.NewHub(logger)
	push := realtime.NewMockPushProvider(hub, logger)

	runner := scheduler.NewRunner(pool, scheduler.Options{
		HoldPollInterval:         cfg.Scheduler.HoldPollInterval,
		CreditsPollInterval:      cfg.Scheduler.CreditsPollInterval,
		RatingsPollInterval:      cfg.Scheduler.RatingsPollInterval,
		GPSRetentionPollInterval: cfg.Scheduler.GPSRetentionPollInterval,
		Enabled:                  cfg.Scheduler.Enabled,
		Logger:                   logger,
		Publisher:                hub,
	})

	deps := &AppDependencies{
		Config:           cfg,
		Logger:           logger,
		Pool:             pool,
		Storage:          store,
		Routing:          routing,
		PaymentGateway:   providers.NewMockPaymentGateway(cfg.Providers.PaymentMockDefaultResult),
		IdentityProvider: providers.NewMockIdentityProvider(cfg.Providers.IdentityMockAutoApprove),
		OTPProvider: providers.NewMailpitConsoleOTPProvider(
			cfg.Providers.SMTPHost,
			strconv.Itoa(cfg.Providers.SMTPPort),
			cfg.Providers.SMTPFrom,
			cfg.Providers.OTPProvider,
			logger,
		),
		Hub:            hub,
		PushProvider:   push,
		AuthManager:    authMgr,
		AuthMiddleware: auth.NewMiddleware(authMgr, pool),
		Scheduler:      runner,
	}

	return deps, nil
}

func run() error {
	cfg, err := config.Load()
	if err != nil {
		return fmt.Errorf("load config: %w", err)
	}

	logger := slog.New(slog.NewJSONHandler(os.Stdout, &slog.HandlerOptions{
		Level: slog.LevelInfo,
	}))
	slog.SetDefault(logger)

	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()

	deps, err := buildDependencies(ctx, cfg, logger)
	if err != nil {
		return err
	}
	defer deps.Pool.Close()

	hubDone := make(chan struct{})
	go func() {
		defer close(hubDone)
		deps.Hub.Run(ctx)
	}()

	deps.Scheduler.Start(ctx)

	server := &http.Server{
		Addr:              fmt.Sprintf(":%d", cfg.App.Port),
		Handler:           NewRouter(*deps),
		ReadHeaderTimeout: defaultReadHeaderTimeout,
	}

	errCh := make(chan error, 1)
	go func() {
		logger.Info("api server starting",
			slog.Int("port", cfg.App.Port),
			slog.String("env", cfg.App.Env),
			slog.String("timezone", cfg.App.Timezone),
		)
		if serveErr := server.ListenAndServe(); serveErr != nil && !errors.Is(serveErr, http.ErrServerClosed) {
			errCh <- serveErr
			return
		}
		errCh <- nil
	}()

	select {
	case <-ctx.Done():
		logger.Info("shutdown signal received")
	case serveErr := <-errCh:
		if serveErr != nil {
			stop()
			deps.Scheduler.Wait()
			<-hubDone
			return serveErr
		}
	}

	shutdownCtx, cancel := context.WithTimeout(context.Background(), defaultShutdownTimeout)
	defer cancel()

	if shutdownErr := server.Shutdown(shutdownCtx); shutdownErr != nil {
		logger.Error("http server shutdown error", slog.Any("error", shutdownErr))
	}

	stop()
	deps.Scheduler.Wait()
	<-hubDone
	logger.Info("api server stopped cleanly")
	return nil
}

func main() {
	if err := run(); err != nil {
		slog.Error("fatal application error", slog.Any("error", err))
		os.Exit(1)
	}
}
