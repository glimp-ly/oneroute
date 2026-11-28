package scheduler_test

import (
	"bytes"
	"context"
	"log/slog"
	"strings"
	"sync"
	"testing"
	"time"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5"

	"github.com/glimp-ly/oneroute/backend/internal/shared/realtime"
	"github.com/glimp-ly/oneroute/backend/internal/shared/scheduler"
)

type publishedEvent struct {
	room    string
	event   string
	payload any
}

type mockPublisher struct {
	mu     sync.Mutex
	events []publishedEvent
}

func (m *mockPublisher) Publish(room string, event string, payload any) {
	m.mu.Lock()
	defer m.mu.Unlock()
	m.events = append(m.events, publishedEvent{
		room:    room,
		event:   event,
		payload: payload,
	})
}

func (m *mockPublisher) snapshot() []publishedEvent {
	m.mu.Lock()
	defer m.mu.Unlock()
	out := make([]publishedEvent, len(m.events))
	copy(out, m.events)
	return out
}

type mockCreditReleaser struct {
	called bool
	err    error
}

func (m *mockCreditReleaser) CreditPassengerBalance(
	_ context.Context,
	_ pgx.Tx,
	_ uuid.UUID,
	_ string,
	_ uuid.UUID,
	_ float64,
	_ string,
) error {
	m.called = true
	return m.err
}

type mockRatingRecalculator struct {
	called bool
	err    error
}

func (m *mockRatingRecalculator) RecalculateRatingStats(
	_ context.Context,
	_ pgx.Tx,
	_ uuid.UUID,
	_ string,
) error {
	m.called = true
	return m.err
}

// Compile-time interface satisfaction checks.
var (
	_ scheduler.EventPublisher     = (*realtime.Hub)(nil)
	_ scheduler.EventPublisher     = (*mockPublisher)(nil)
	_ scheduler.CreditReleaser     = (*mockCreditReleaser)(nil)
	_ scheduler.RatingRecalculator = (*mockRatingRecalculator)(nil)
)

func TestNewRunnerOptionsDefaults(t *testing.T) {
	t.Run("applies defaults when zero options provided", func(t *testing.T) {
		r := scheduler.NewRunner(nil, scheduler.Options{})
		if r == nil {
			t.Fatal("NewRunner returned nil")
		}

		got := r.Options()
		if got.HoldPollInterval != scheduler.DefaultHoldPollInterval {
			t.Errorf("HoldPollInterval = %v, want %v", got.HoldPollInterval, scheduler.DefaultHoldPollInterval)
		}
		if got.CreditsPollInterval != scheduler.DefaultCreditsPollInterval {
			t.Errorf("CreditsPollInterval = %v, want %v", got.CreditsPollInterval, scheduler.DefaultCreditsPollInterval)
		}
		if got.RatingsPollInterval != scheduler.DefaultRatingsPollInterval {
			t.Errorf("RatingsPollInterval = %v, want %v", got.RatingsPollInterval, scheduler.DefaultRatingsPollInterval)
		}
		if got.TrackingPollInterval != scheduler.DefaultTrackingPollInterval {
			t.Errorf("TrackingPollInterval = %v, want %v", got.TrackingPollInterval, scheduler.DefaultTrackingPollInterval)
		}
		if got.GPSRetentionPollInterval != scheduler.DefaultGPSRetentionPollInterval {
			t.Errorf("GPSRetentionPollInterval = %v, want %v", got.GPSRetentionPollInterval, scheduler.DefaultGPSRetentionPollInterval)
		}
		if got.Logger == nil {
			t.Error("Logger must default to non-nil logger")
		}
		if got.Enabled {
			t.Error("Enabled should remain false when not configured")
		}
	})

	t.Run("preserves explicit positive intervals and callbacks", func(t *testing.T) {
		logger := slog.New(slog.NewTextHandler(&bytes.Buffer{}, nil))
		pub := &mockPublisher{}
		cr := &mockCreditReleaser{}
		rr := &mockRatingRecalculator{}

		r := scheduler.NewRunner(nil, scheduler.Options{
			HoldPollInterval:         2 * time.Second,
			CreditsPollInterval:      15 * time.Second,
			RatingsPollInterval:      20 * time.Second,
			TrackingPollInterval:     25 * time.Second,
			GPSRetentionPollInterval: 10 * time.Minute,
			Enabled:                  true,
			Logger:                   logger,
			Publisher:                pub,
			CreditReleaser:           cr,
			RatingRecalculator:       rr,
		})

		got := r.Options()
		if got.HoldPollInterval != 2*time.Second {
			t.Errorf("HoldPollInterval = %v, want 2s", got.HoldPollInterval)
		}
		if got.CreditsPollInterval != 15*time.Second {
			t.Errorf("CreditsPollInterval = %v, want 15s", got.CreditsPollInterval)
		}
		if got.RatingsPollInterval != 20*time.Second {
			t.Errorf("RatingsPollInterval = %v, want 20s", got.RatingsPollInterval)
		}
		if got.TrackingPollInterval != 25*time.Second {
			t.Errorf("TrackingPollInterval = %v, want 25s", got.TrackingPollInterval)
		}
		if got.GPSRetentionPollInterval != 10*time.Minute {
			t.Errorf("GPSRetentionPollInterval = %v, want 10m", got.GPSRetentionPollInterval)
		}
		if !got.Enabled {
			t.Error("Enabled = false, want true")
		}
		if got.Logger != logger {
			t.Error("Logger was not preserved")
		}
	})
}

func TestRunnerDisabledMode(t *testing.T) {
	r := scheduler.NewRunner(nil, scheduler.Options{
		HoldPollInterval:         5 * time.Millisecond,
		CreditsPollInterval:      5 * time.Millisecond,
		RatingsPollInterval:      5 * time.Millisecond,
		TrackingPollInterval:     5 * time.Millisecond,
		GPSRetentionPollInterval: 5 * time.Millisecond,
		Enabled:                  false,
	})

	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	r.Start(ctx)

	done := make(chan struct{})
	go func() {
		r.Wait()
		close(done)
	}()

	select {
	case <-done:
	case <-time.After(200 * time.Millisecond):
		t.Fatal("Wait did not return immediately when scheduler is disabled")
	}
}

func TestRunnerGracefulStartShutdownAndNilCallbacksUnderRace(t *testing.T) {
	var logBuf bytes.Buffer
	var logMu sync.Mutex
	logger := slog.New(slog.NewTextHandler(&syncWriter{mu: &logMu, buf: &logBuf}, nil))

	var typedNilHub *realtime.Hub
	var typedNilReleaser *mockCreditReleaser
	var typedNilRecalc *mockRatingRecalculator

	r := scheduler.NewRunner(nil, scheduler.Options{
		HoldPollInterval:         5 * time.Millisecond,
		CreditsPollInterval:      5 * time.Millisecond,
		RatingsPollInterval:      5 * time.Millisecond,
		TrackingPollInterval:     5 * time.Millisecond,
		GPSRetentionPollInterval: 5 * time.Millisecond,
		Enabled:                  true,
		Logger:                   logger,
		Publisher:                typedNilHub,
		CreditReleaser:           typedNilReleaser,
		RatingRecalculator:       typedNilRecalc,
	})

	ctx, cancel := context.WithCancel(context.Background())
	r.Start(ctx)
	// Calling Start a second time must be idempotent and safe under -race.
	r.Start(ctx)

	time.Sleep(25 * time.Millisecond)
	cancel()

	done := make(chan struct{})
	go func() {
		r.Wait()
		close(done)
	}()

	select {
	case <-done:
	case <-time.After(time.Second):
		t.Fatal("Wait timed out after context cancellation")
	}
}

func TestOnceMethodsNilPoolAndNilCreditReleaser(t *testing.T) {
	ctx := context.Background()

	t.Run("all once methods return explicit error on nil pool", func(t *testing.T) {
		r := scheduler.NewRunner(nil, scheduler.Options{
			CreditReleaser: &mockCreditReleaser{},
		})

		if _, _, err := r.ExpireRequestsAndHoldsOnce(ctx); err == nil {
			t.Error("ExpireRequestsAndHoldsOnce expected error on nil pool")
		}
		if _, err := r.ReleaseScheduledCreditsOnce(ctx); err == nil {
			t.Error("ReleaseScheduledCreditsOnce expected error on nil pool")
		}
		if _, err := r.PublishDelayedRatingsOnce(ctx); err == nil {
			t.Error("PublishDelayedRatingsOnce expected error on nil pool")
		}
		if _, err := r.DeactivateExpiredTrackingLinksOnce(ctx); err == nil {
			t.Error("DeactivateExpiredTrackingLinksOnce expected error on nil pool")
		}
		if _, err := r.PurgeOldGPSPointsOnce(ctx); err == nil {
			t.Error("PurgeOldGPSPointsOnce expected error on nil pool")
		}
	})

	t.Run("ReleaseScheduledCreditsOnce skips and logs warning when CreditReleaser is nil", func(t *testing.T) {
		var logBuf bytes.Buffer
		logger := slog.New(slog.NewTextHandler(&logBuf, &slog.HandlerOptions{Level: slog.LevelWarn}))

		r := scheduler.NewRunner(nil, scheduler.Options{
			Logger: logger,
		})

		released, err := r.ReleaseScheduledCreditsOnce(ctx)
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		if released != 0 {
			t.Fatalf("released = %d, want 0", released)
		}
		if !strings.Contains(logBuf.String(), "CreditReleaser") {
			t.Errorf("expected warning log mentioning CreditReleaser, got %q", logBuf.String())
		}
	})
}

type syncWriter struct {
	mu  *sync.Mutex
	buf *bytes.Buffer
}

func (w *syncWriter) Write(p []byte) (int, error) {
	w.mu.Lock()
	defer w.mu.Unlock()
	return w.buf.Write(p)
}
