package db

import (
	"context"
	"errors"
	"fmt"
	"strings"
	"time"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgconn"
	"github.com/jackc/pgx/v5/pgxpool"
)

const (
	DefaultMaxConns        int32         = 25
	DefaultMinConns        int32         = 5
	DefaultMaxConnLifetime time.Duration = 30 * time.Minute
	DefaultTimezone        string        = "America/Lima"
)

// DBTX abstracts query execution across *pgxpool.Pool and pgx.Tx.
type DBTX interface {
	Exec(ctx context.Context, sql string, arguments ...any) (pgconn.CommandTag, error)
	Query(ctx context.Context, sql string, args ...any) (pgx.Rows, error)
	QueryRow(ctx context.Context, sql string, args ...any) pgx.Row
}

var (
	_ DBTX = (*pgxpool.Pool)(nil)
	_ DBTX = (pgx.Tx)(nil)
)

// BuildPoolConfig parses databaseURL and configures pool limits and session timezone.
func BuildPoolConfig(databaseURL, timezone string) (*pgxpool.Config, error) {
	if strings.TrimSpace(databaseURL) == "" {
		return nil, errors.New("db: databaseURL must not be empty")
	}

	tz := strings.TrimSpace(timezone)
	if tz == "" {
		tz = DefaultTimezone
	}
	if _, err := time.LoadLocation(tz); err != nil {
		return nil, fmt.Errorf("db: invalid timezone %q: %w", tz, err)
	}

	cfg, err := pgxpool.ParseConfig(databaseURL)
	if err != nil {
		return nil, fmt.Errorf("db: parse pool config: %w", err)
	}

	cfg.MaxConns = DefaultMaxConns
	cfg.MinConns = DefaultMinConns
	cfg.MaxConnLifetime = DefaultMaxConnLifetime

	tzSQL := "SET TIME ZONE " + pgx.Identifier{tz}.Sanitize()
	cfg.AfterConnect = func(ctx context.Context, conn *pgx.Conn) error {
		if _, err := conn.Exec(ctx, tzSQL); err != nil {
			return fmt.Errorf("db: set session timezone %q: %w", tz, err)
		}
		return nil
	}

	return cfg, nil
}

// NewPool creates a configured pgxpool.Pool and verifies connectivity with Ping.
func NewPool(ctx context.Context, databaseURL, timezone string) (*pgxpool.Pool, error) {
	cfg, err := BuildPoolConfig(databaseURL, timezone)
	if err != nil {
		return nil, err
	}

	pool, err := pgxpool.NewWithConfig(ctx, cfg)
	if err != nil {
		return nil, fmt.Errorf("db: create pool: %w", err)
	}

	if err := pool.Ping(ctx); err != nil {
		pool.Close()
		return nil, fmt.Errorf("db: ping database: %w", err)
	}

	return pool, nil
}

// WithTx executes fn inside a ReadCommitted database transaction,
// rolling back on error or panic and committing on success.
func WithTx(ctx context.Context, pool *pgxpool.Pool, fn func(tx pgx.Tx) error) (err error) {
	if pool == nil {
		return errors.New("db: pool must not be nil")
	}
	if fn == nil {
		return errors.New("db: transaction callback must not be nil")
	}

	tx, err := pool.BeginTx(ctx, pgx.TxOptions{IsoLevel: pgx.ReadCommitted})
	if err != nil {
		return fmt.Errorf("db: begin tx: %w", err)
	}

	defer func() {
		if r := recover(); r != nil {
			rbCtx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
			_ = tx.Rollback(rbCtx)
			cancel()
			panic(r)
		}
		if err != nil {
			rbCtx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
			_ = tx.Rollback(rbCtx)
			cancel()
		}
	}()

	if err = fn(tx); err != nil {
		return err
	}

	if err = tx.Commit(ctx); err != nil {
		return fmt.Errorf("db: commit tx: %w", err)
	}

	return nil
}

// WithExistingOrNewTx reuses existingTx when non-nil (leaving commit/rollback to the caller),
// or starts a new transaction via WithTx when existingTx is nil.
func WithExistingOrNewTx(ctx context.Context, existingTx pgx.Tx, pool *pgxpool.Pool, fn func(tx pgx.Tx) error) error {
	if fn == nil {
		return errors.New("db: transaction callback must not be nil")
	}
	if existingTx != nil {
		return fn(existingTx)
	}
	return WithTx(ctx, pool, fn)
}
