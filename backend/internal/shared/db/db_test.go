package db_test

import (
	"context"
	"errors"
	"fmt"
	"net/http"
	"testing"
	"time"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgconn"

	"github.com/glimp-ly/oneroute/backend/internal/shared/db"
)

type stubTx struct {
	pgx.Tx
	committed  bool
	rolledBack bool
}

func (s *stubTx) Commit(_ context.Context) error {
	s.committed = true
	return nil
}

func (s *stubTx) Rollback(_ context.Context) error {
	s.rolledBack = true
	return nil
}

func TestBuildPoolConfig(t *testing.T) {
	t.Run("valid url and default timezone", func(t *testing.T) {
		cfg, err := db.BuildPoolConfig("postgres://user:pass@localhost:5432/db?sslmode=disable", "")
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		if cfg.MaxConns != 25 {
			t.Errorf("MaxConns = %d, want 25", cfg.MaxConns)
		}
		if cfg.MinConns != 5 {
			t.Errorf("MinConns = %d, want 5", cfg.MinConns)
		}
		if cfg.MaxConnLifetime != 30*time.Minute {
			t.Errorf("MaxConnLifetime = %v, want 30m", cfg.MaxConnLifetime)
		}
		if cfg.AfterConnect == nil {
			t.Fatalf("expected AfterConnect hook to be configured")
		}
	})

	t.Run("empty url fails", func(t *testing.T) {
		if _, err := db.BuildPoolConfig("", "America/Lima"); err == nil {
			t.Fatalf("expected error for empty databaseURL")
		}
	})

	t.Run("invalid timezone fails", func(t *testing.T) {
		if _, err := db.BuildPoolConfig("postgres://user:pass@localhost:5432/db", "Invalid/Zone"); err == nil {
			t.Fatalf("expected error for invalid timezone")
		}
	})
}

func TestWithTxAndWithExistingOrNewTxGuards(t *testing.T) {
	ctx := context.Background()

	if err := db.WithTx(ctx, nil, func(tx pgx.Tx) error { return nil }); err == nil {
		t.Fatalf("expected error when pool is nil")
	}

	if err := db.WithExistingOrNewTx(ctx, nil, nil, nil); err == nil {
		t.Fatalf("expected error when callback is nil")
	}

	tx := &stubTx{}
	called := false
	err := db.WithExistingOrNewTx(ctx, tx, nil, func(got pgx.Tx) error {
		called = true
		if got != tx {
			t.Fatalf("expected existing tx to be forwarded")
		}
		return nil
	})
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if !called {
		t.Fatalf("expected callback to be called")
	}
	if tx.committed || tx.rolledBack {
		t.Fatalf("WithExistingOrNewTx must not commit or rollback caller-owned tx")
	}
}

func TestMapPgError(t *testing.T) {
	if got := db.MapPgError(nil); got != nil {
		t.Fatalf("expected nil for nil error, got %v", got)
	}

	plainErr := errors.New("plain error")
	if got := db.MapPgError(plainErr); !errors.Is(got, plainErr) {
		t.Fatalf("expected non-pg error to pass through unchanged, got %v", got)
	}

	cases := []struct {
		name           string
		input          error
		wantStatus     int
		wantCode       string
		wantConstraint string
		wantMsgExact   string
	}{
		{
			name:       "pgx ErrNoRows wrapped",
			input:      fmt.Errorf("query user: %w", pgx.ErrNoRows),
			wantStatus: http.StatusNotFound,
			wantCode:   "NOT_FOUND",
		},
		{
			name: "unique active trip limit",
			input: &pgconn.PgError{
				Code:           "23505",
				ConstraintName: "uq_conductor_viaje_activo",
			},
			wantStatus:     http.StatusConflict,
			wantCode:       "ACTIVE_TRIP_LIMIT",
			wantConstraint: "uq_conductor_viaje_activo",
		},
		{
			name: "unique passenger active reservation",
			input: &pgconn.PgError{
				Code:           "23505",
				ConstraintName: "uq_pasajero_reserva_activa",
			},
			wantStatus:     http.StatusConflict,
			wantCode:       "CONFLICT",
			wantConstraint: "uq_pasajero_reserva_activa",
		},
		{
			name: "unique trip passenger active reservation",
			input: &pgconn.PgError{
				Code:           "23505",
				ConstraintName: "uq_reserva_viaje_pasajero_activa",
			},
			wantStatus:     http.StatusConflict,
			wantCode:       "CONFLICT",
			wantConstraint: "uq_reserva_viaje_pasajero_activa",
		},
		{
			name: "unique modification limit pickup",
			input: &pgconn.PgError{
				Code:           "23505",
				ConstraintName: "uq_reserva_cambio_recogida_unico",
			},
			wantStatus:     http.StatusConflict,
			wantCode:       "MODIFICATION_LIMIT_REACHED",
			wantConstraint: "uq_reserva_cambio_recogida_unico",
		},
		{
			name: "unique modification limit destination",
			input: &pgconn.PgError{
				Code:           "23505",
				ConstraintName: "uq_reserva_cambio_destino_unico",
			},
			wantStatus:     http.StatusConflict,
			wantCode:       "MODIFICATION_LIMIT_REACHED",
			wantConstraint: "uq_reserva_cambio_destino_unico",
		},
		{
			name: "generic unique violation",
			input: &pgconn.PgError{
				Code:           "23505",
				ConstraintName: "usuario_email_key",
			},
			wantStatus:     http.StatusConflict,
			wantCode:       "CONFLICT",
			wantConstraint: "usuario_email_key",
		},
		{
			name: "foreign key missing referenced row",
			input: &pgconn.PgError{
				Code:           "23503",
				ConstraintName: "fk_viaje_conductor",
				Detail:         "Key (conductor_id)=(123) is not present in table \"usuario\".",
			},
			wantStatus:     http.StatusNotFound,
			wantCode:       "NOT_FOUND",
			wantConstraint: "fk_viaje_conductor",
		},
		{
			name: "foreign key still referenced",
			input: &pgconn.PgError{
				Code:           "23503",
				ConstraintName: "fk_reserva_viaje",
				Detail:         "Key (id)=(123) is still referenced from table \"reserva\".",
			},
			wantStatus:     http.StatusBadRequest,
			wantCode:       "BAD_REQUEST",
			wantConstraint: "fk_reserva_viaje",
		},
		{
			name: "check constraint violation",
			input: &pgconn.PgError{
				Code:           "23514",
				ConstraintName: "chk_asientos_positivos",
			},
			wantStatus:     http.StatusUnprocessableEntity,
			wantCode:       "VALIDATION_ERROR",
			wantConstraint: "chk_asientos_positivos",
		},
		{
			name: "not null violation",
			input: &pgconn.PgError{
				Code:       "23502",
				ColumnName: "origen_texto",
			},
			wantStatus:     http.StatusBadRequest,
			wantCode:       "VALIDATION_ERROR",
			wantConstraint: "origen_texto",
		},
		{
			name: "invalid uuid format",
			input: &pgconn.PgError{
				Code: "22P02",
			},
			wantStatus: http.StatusBadRequest,
			wantCode:   "BAD_REQUEST",
		},
		{
			name: "P0001 insufficient capacity trigger",
			input: &pgconn.PgError{
				Code:    "P0001",
				Message: "Capacidad insuficiente en el tramo solicitado (ocupados: 3, solicitados: 2, capacidad: 4)",
			},
			wantStatus:   http.StatusConflict,
			wantCode:     "INSUFFICIENT_CAPACITY",
			wantMsgExact: "Capacidad insuficiente en el tramo solicitado (ocupados: 3, solicitados: 2, capacidad: 4)",
		},
		{
			name: "P0001 calendar deadline expired trigger",
			input: &pgconn.PgError{
				Code:    "P0001",
				Message: "El plazo de 7 días calendario para presentar descargos ha vencido",
			},
			wantStatus:   http.StatusUnprocessableEntity,
			wantCode:     "CALENDAR_DEADLINE_EXPIRED",
			wantMsgExact: "El plazo de 7 días calendario para presentar descargos ha vencido",
		},
		{
			name: "P0001 immutable report trigger",
			input: &pgconn.PgError{
				Code:    "P0001",
				Message: "El contenido original de un reporte es inmutable",
			},
			wantStatus:   http.StatusConflict,
			wantCode:     "IMMUTABLE_RECORD",
			wantMsgExact: "El contenido original de un reporte es inmutable",
		},
		{
			name: "P0001 immutable wallet ledger trigger",
			input: &pgconn.PgError{
				Code:    "P0001",
				Message: "Los movimientos de billetera no pueden modificarse ni eliminarse",
			},
			wantStatus:   http.StatusConflict,
			wantCode:     "IMMUTABLE_RECORD",
			wantMsgExact: "Los movimientos de billetera no pueden modificarse ni eliminarse",
		},
		{
			name: "P0001 generic trigger message preserved",
			input: &pgconn.PgError{
				Code:    "P0001",
				Message: "Regla de negocio especifica incumplida",
			},
			wantStatus:   http.StatusUnprocessableEntity,
			wantCode:     "BUSINESS_RULE_VIOLATION",
			wantMsgExact: "Regla de negocio especifica incumplida",
		},
	}

	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			mapped := db.MapPgError(tc.input)
			var pgApp *db.PgAppError
			if !errors.As(mapped, &pgApp) {
				t.Fatalf("expected *db.PgAppError, got %T (%v)", mapped, mapped)
			}
			if pgApp.HTTPStatus() != tc.wantStatus {
				t.Errorf("HTTPStatus() = %d, want %d", pgApp.HTTPStatus(), tc.wantStatus)
			}
			if pgApp.ErrorCode() != tc.wantCode {
				t.Errorf("ErrorCode() = %q, want %q", pgApp.ErrorCode(), tc.wantCode)
			}
			if tc.wantConstraint != "" && pgApp.Constraint != tc.wantConstraint {
				t.Errorf("Constraint = %q, want %q", pgApp.Constraint, tc.wantConstraint)
			}
			if tc.wantMsgExact != "" && pgApp.Error() != tc.wantMsgExact {
				t.Errorf("Error() = %q, want %q", pgApp.Error(), tc.wantMsgExact)
			}
			if pgApp.Unwrap() == nil {
				t.Errorf("expected Unwrap() to return cause")
			}
			// Idempotent re-mapping.
			if again := db.MapPgError(mapped); again != mapped {
				t.Errorf("expected MapPgError on *PgAppError to be idempotent")
			}
		})
	}
}
