package main

import (
	"context"
	"encoding/json"
	"errors"
	"io"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"

	"github.com/google/uuid"
	"github.com/gorilla/websocket"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgconn"

	"github.com/glimp-ly/oneroute/backend/internal/shared/auth"
	"github.com/glimp-ly/oneroute/backend/internal/shared/config"
	"github.com/glimp-ly/oneroute/backend/internal/shared/httpx"
	"github.com/glimp-ly/oneroute/backend/internal/shared/realtime"
	"github.com/glimp-ly/oneroute/backend/internal/shared/storage"
)

type healthResponseEnvelope struct {
	Success bool              `json:"success"`
	Data    map[string]any    `json:"data"`
	Error   *httpx.ErrorBody  `json:"error"`
}

type noopAuthDB struct{}

func (noopAuthDB) Exec(context.Context, string, ...any) (pgconn.CommandTag, error) {
	return pgconn.CommandTag{}, nil
}

func (noopAuthDB) Query(context.Context, string, ...any) (pgx.Rows, error) {
	return nil, errors.New("not implemented")
}

func (noopAuthDB) QueryRow(context.Context, string, ...any) pgx.Row {
	return failingRow{err: pgx.ErrNoRows}
}

type failingRow struct {
	err error
}

func (r failingRow) Scan(...any) error {
	return r.err
}

func newTestConfig() *config.Config {
	return &config.Config{
		App: config.AppConfig{
			Env:                "development",
			Port:               8080,
			Timezone:           "America/Lima",
			CORSAllowedOrigins: []string{"http://localhost:3000"},
		},
	}
}

func newTestLogger() *slog.Logger {
	return slog.New(slog.NewTextHandler(io.Discard, nil))
}

func TestHealthz_Healthy(t *testing.T) {
	mockStore := storage.NewMockStorage()
	deps := AppDependencies{
		Config:  newTestConfig(),
		Logger:  newTestLogger(),
		Storage: mockStore,
		DBHealthCheck: func(context.Context) (string, error) {
			return "3.4 USE_GEOS=1 USE_PROJ=1", nil
		},
	}

	router := NewRouter(deps)
	req := httptest.NewRequest(http.MethodGet, "/healthz", nil)
	req.Header.Set(httpx.RequestIDHeader, "req-health-123")
	rec := httptest.NewRecorder()

	router.ServeHTTP(rec, req)

	if rec.Code != http.StatusOK {
		t.Fatalf("status = %d, want %d; body = %s", rec.Code, http.StatusOK, rec.Body.String())
	}
	if got := rec.Header().Get(httpx.RequestIDHeader); got != "req-health-123" {
		t.Errorf("%s = %q, want %q", httpx.RequestIDHeader, got, "req-health-123")
	}

	var env healthResponseEnvelope
	if err := json.Unmarshal(rec.Body.Bytes(), &env); err != nil {
		t.Fatalf("unmarshal response: %v", err)
	}
	if !env.Success {
		t.Fatalf("expected success=true, got false (error=%+v)", env.Error)
	}
	if env.Data["status"] != "ok" {
		t.Errorf("data.status = %v, want ok", env.Data["status"])
	}
	if env.Data["postgis"] != "3.4 USE_GEOS=1 USE_PROJ=1" {
		t.Errorf("data.postgis = %v, want PostGIS version string", env.Data["postgis"])
	}
	if env.Data["storage"] != "ok" {
		t.Errorf("data.storage = %v, want ok", env.Data["storage"])
	}
	if env.Data["timezone"] != "America/Lima" {
		t.Errorf("data.timezone = %v, want America/Lima", env.Data["timezone"])
	}
}

func TestHealthz_Unhealthy(t *testing.T) {
	cases := []struct {
		name        string
		dbErr       error
		storageErr  error
		wantPostGIS string
		wantStorage string
	}{
		{
			name:        "database check fails",
			dbErr:       errors.New("postgres unreachable"),
			wantPostGIS: "error",
			wantStorage: "ok",
		},
		{
			name:        "storage check fails",
			storageErr:  errors.New("minio unreachable"),
			wantPostGIS: "3.4",
			wantStorage: "error",
		},
	}

	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			mockStore := storage.NewMockStorage()
			mockStore.SetHealthError(tc.storageErr)

			deps := AppDependencies{
				Config:  newTestConfig(),
				Logger:  newTestLogger(),
				Storage: mockStore,
				DBHealthCheck: func(context.Context) (string, error) {
					if tc.dbErr != nil {
						return "", tc.dbErr
					}
					return "3.4", nil
				},
			}

			router := NewRouter(deps)
			req := httptest.NewRequest(http.MethodGet, "/healthz", nil)
			rec := httptest.NewRecorder()
			router.ServeHTTP(rec, req)

			if rec.Code != http.StatusServiceUnavailable {
				t.Fatalf("status = %d, want %d", rec.Code, http.StatusServiceUnavailable)
			}

			var env healthResponseEnvelope
			if err := json.Unmarshal(rec.Body.Bytes(), &env); err != nil {
				t.Fatalf("unmarshal response: %v", err)
			}
			if env.Data["status"] != "unhealthy" {
				t.Errorf("data.status = %v, want unhealthy", env.Data["status"])
			}
			if env.Data["postgis"] != tc.wantPostGIS {
				t.Errorf("data.postgis = %v, want %s", env.Data["postgis"], tc.wantPostGIS)
			}
			if env.Data["storage"] != tc.wantStorage {
				t.Errorf("data.storage = %v, want %s", env.Data["storage"], tc.wantStorage)
			}
		})
	}
}

func TestCORSAndWebSocketAuthWiring(t *testing.T) {
	mgr, err := auth.NewManager(
		"test_access_secret_32_characters!!",
		"test_refresh_secret_32_characters!",
		15*time.Minute,
		24*time.Hour,
	)
	if err != nil {
		t.Fatalf("NewManager: %v", err)
	}

	hub := realtime.NewHub(newTestLogger())
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	go hub.Run(ctx)

	deps := AppDependencies{
		Config:         newTestConfig(),
		Logger:         newTestLogger(),
		Storage:        storage.NewMockStorage(),
		Hub:            hub,
		AuthManager:    mgr,
		AuthMiddleware: auth.NewMiddleware(mgr, noopAuthDB{}),
		DBHealthCheck: func(context.Context) (string, error) {
			return "3.4", nil
		},
	}

	router := NewRouter(deps)

	// Verify CORS preflight headers.
	preReq := httptest.NewRequest(http.MethodOptions, "/healthz", nil)
	preReq.Header.Set("Origin", "http://localhost:3000")
	preReq.Header.Set("Access-Control-Request-Method", http.MethodGet)
	preRec := httptest.NewRecorder()
	router.ServeHTTP(preRec, preReq)

	if got := preRec.Header().Get("Access-Control-Allow-Origin"); got != "http://localhost:3000" {
		t.Errorf("Access-Control-Allow-Origin = %q, want http://localhost:3000", got)
	}

	// Verify WebSocket rejects unauthenticated connections with 401.
	srv := httptest.NewServer(router)
	defer srv.Close()

	wsURL := "ws" + strings.TrimPrefix(srv.URL, "http") + "/api/v1/ws"
	conn, resp, dialErr := websocket.DefaultDialer.Dial(wsURL, nil)
	if conn != nil {
		_ = conn.Close()
	}
	if dialErr == nil {
		t.Fatal("expected unauthenticated websocket dial to fail")
	}
	if resp == nil || resp.StatusCode != http.StatusUnauthorized {
		status := 0
		if resp != nil {
			status = resp.StatusCode
		}
		t.Fatalf("ws unauthenticated status = %d, want %d", status, http.StatusUnauthorized)
	}

	// Verify defaultRoomAuthorizer enforces user room isolation.
	authorizer := defaultRoomAuthorizer(nil)
	selfID := uuid.New()
	otherID := uuid.New()

	if err := authorizer(context.Background(), selfID, realtime.UserRoom(selfID)); err != nil {
		t.Errorf("self user room should be allowed, got: %v", err)
	}
	if err := authorizer(context.Background(), selfID, realtime.UserRoom(otherID)); err == nil {
		t.Error("other user room should be rejected")
	}
	if err := authorizer(context.Background(), selfID, "invalid-room"); err == nil {
		t.Error("invalid room format should be rejected")
	}
}
