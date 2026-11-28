//go:build integration

package db_test

import (
	"context"
	"errors"
	"net/http"
	"os"
	"testing"
	"time"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5"

	"github.com/glimp-ly/oneroute/backend/internal/shared/db"
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

func TestNewPoolAndTimezoneIntegration(t *testing.T) {
	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()

	pool, err := db.NewPool(ctx, testDatabaseURL(), "")
	if err != nil {
		t.Fatalf("NewPool failed: %v", err)
	}
	defer pool.Close()

	if maxConns := pool.Stat().MaxConns(); maxConns != 25 {
		t.Errorf("Stat().MaxConns() = %d, want 25", maxConns)
	}

	var tz string
	if err := pool.QueryRow(ctx, "SHOW TIME ZONE").Scan(&tz); err != nil {
		t.Fatalf("SHOW TIME ZONE failed: %v", err)
	}
	if tz != "America/Lima" {
		t.Errorf("session timezone = %q, want America/Lima", tz)
	}
}

func TestWithTxCommitAndRollbackIntegration(t *testing.T) {
	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()

	pool, err := db.NewPool(ctx, testDatabaseURL(), "America/Lima")
	if err != nil {
		t.Fatalf("NewPool failed: %v", err)
	}
	defer pool.Close()

	commitKey := "test_db_commit_" + uuid.NewString()
	rollbackKey := "test_db_rollback_" + uuid.NewString()
	t.Cleanup(func() {
		cleanCtx, cleanCancel := context.WithTimeout(context.Background(), 5*time.Second)
		defer cleanCancel()
		_, _ = pool.Exec(cleanCtx, "DELETE FROM configuracion WHERE clave IN ($1, $2)", commitKey, rollbackKey)
	})

	// Commit path.
	err = db.WithTx(ctx, pool, func(tx pgx.Tx) error {
		_, execErr := tx.Exec(ctx,
			"INSERT INTO configuracion (clave, valor, descripcion) VALUES ($1, $2::jsonb, $3)",
			commitKey, `{"ok":true}`, "integration test commit",
		)
		return execErr
	})
	if err != nil {
		t.Fatalf("WithTx commit failed: %v", err)
	}

	var count int
	if err := pool.QueryRow(ctx, "SELECT COUNT(*) FROM configuracion WHERE clave = $1", commitKey).Scan(&count); err != nil {
		t.Fatalf("verify committed row failed: %v", err)
	}
	if count != 1 {
		t.Fatalf("committed row count = %d, want 1", count)
	}

	// Rollback path (also verifies WithExistingOrNewTx does not commit early).
	sentinel := errors.New("force rollback")
	err = db.WithTx(ctx, pool, func(tx pgx.Tx) error {
		return db.WithExistingOrNewTx(ctx, tx, pool, func(innerTx pgx.Tx) error {
			if _, execErr := innerTx.Exec(ctx,
				"INSERT INTO configuracion (clave, valor, descripcion) VALUES ($1, $2::jsonb, $3)",
				rollbackKey, `{"ok":false}`, "integration test rollback",
			); execErr != nil {
				return execErr
			}
			return sentinel
		})
	})
	if !errors.Is(err, sentinel) {
		t.Fatalf("expected sentinel error, got: %v", err)
	}

	if err := pool.QueryRow(ctx, "SELECT COUNT(*) FROM configuracion WHERE clave = $1", rollbackKey).Scan(&count); err != nil {
		t.Fatalf("verify rolled back row failed: %v", err)
	}
	if count != 0 {
		t.Fatalf("rolled back row count = %d, want 0", count)
	}
}

func TestMapPgErrorLivePostgresIntegration(t *testing.T) {
	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()

	pool, err := db.NewPool(ctx, testDatabaseURL(), "America/Lima")
	if err != nil {
		t.Fatalf("NewPool failed: %v", err)
	}
	defer pool.Close()

	t.Run("ErrNoRows maps to 404 NOT_FOUND", func(t *testing.T) {
		var clave string
		err := pool.QueryRow(ctx, "SELECT clave FROM configuracion WHERE clave = $1", "missing_"+uuid.NewString()).Scan(&clave)
		mapped := db.MapPgError(err)
		var pgApp *db.PgAppError
		if !errors.As(mapped, &pgApp) || pgApp.HTTPStatus() != http.StatusNotFound || pgApp.ErrorCode() != "NOT_FOUND" {
			t.Fatalf("expected 404 NOT_FOUND, got %v", mapped)
		}
	})

	t.Run("23505 unique violation maps to 409 CONFLICT", func(t *testing.T) {
		dupKey := "test_db_dup_" + uuid.NewString()
		t.Cleanup(func() {
			cleanCtx, cleanCancel := context.WithTimeout(context.Background(), 5*time.Second)
			defer cleanCancel()
			_, _ = pool.Exec(cleanCtx, "DELETE FROM configuracion WHERE clave = $1", dupKey)
		})

		_, err := pool.Exec(ctx, "INSERT INTO configuracion (clave, valor) VALUES ($1, '1'::jsonb)", dupKey)
		if err != nil {
			t.Fatalf("first insert failed: %v", err)
		}
		_, err = pool.Exec(ctx, "INSERT INTO configuracion (clave, valor) VALUES ($1, '2'::jsonb)", dupKey)
		mapped := db.MapPgError(err)
		var pgApp *db.PgAppError
		if !errors.As(mapped, &pgApp) || pgApp.HTTPStatus() != http.StatusConflict || pgApp.ErrorCode() != "CONFLICT" {
			t.Fatalf("expected 409 CONFLICT, got %v", mapped)
		}
	})

	t.Run("P0001 raise exception maps to INSUFFICIENT_CAPACITY", func(t *testing.T) {
		_, err := pool.Exec(ctx, "DO $$ BEGIN RAISE EXCEPTION 'Capacidad insuficiente en el tramo solicitado'; END $$;")
		mapped := db.MapPgError(err)
		var pgApp *db.PgAppError
		if !errors.As(mapped, &pgApp) || pgApp.HTTPStatus() != http.StatusConflict || pgApp.ErrorCode() != "INSUFFICIENT_CAPACITY" {
			t.Fatalf("expected 409 INSUFFICIENT_CAPACITY, got %v", mapped)
		}
	})
}
