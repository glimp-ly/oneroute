package httpx_test

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"strings"
	"sync"
	"testing"
	"time"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgconn"

	"github.com/glimp-ly/oneroute/backend/internal/shared/httpx"
)

type customDomainError struct{}

func (customDomainError) HTTPStatus() int   { return http.StatusConflict }
func (customDomainError) ErrorCode() string { return "CUSTOM_CONFLICT" }
func (customDomainError) Error() string     { return "Conflicto de dominio personalizado." }

func TestWriteJSONEnvelope(t *testing.T) {
	t.Run("success without meta serializes null meta and null error", func(t *testing.T) {
		rec := httptest.NewRecorder()
		httpx.WriteJSON(rec, http.StatusOK, map[string]string{"id": "abc"}, nil)

		if rec.Code != http.StatusOK {
			t.Fatalf("status = %d, want 200", rec.Code)
		}
		if ct := rec.Header().Get("Content-Type"); !strings.Contains(ct, "application/json") {
			t.Errorf("Content-Type = %q, want application/json", ct)
		}

		var raw map[string]any
		if err := json.Unmarshal(rec.Body.Bytes(), &raw); err != nil {
			t.Fatalf("unmarshal response: %v", err)
		}
		if raw["success"] != true {
			t.Errorf("success = %v, want true", raw["success"])
		}
		if _, exists := raw["meta"]; !exists || raw["meta"] != nil {
			t.Errorf("meta = %v (exists=%v), want explicit null", raw["meta"], exists)
		}
		if _, exists := raw["error"]; !exists || raw["error"] != nil {
			t.Errorf("error = %v (exists=%v), want explicit null", raw["error"], exists)
		}
	})

	t.Run("success with zero total meta preserves total field", func(t *testing.T) {
		rec := httptest.NewRecorder()
		httpx.WriteJSON(rec, http.StatusOK, []string{}, &httpx.Meta{Page: 1, Limit: 20, Total: 0})

		var raw map[string]any
		if err := json.Unmarshal(rec.Body.Bytes(), &raw); err != nil {
			t.Fatalf("unmarshal response: %v", err)
		}
		metaMap, ok := raw["meta"].(map[string]any)
		if !ok {
			t.Fatalf("expected meta object, got %T", raw["meta"])
		}
		if metaMap["total"] != float64(0) || metaMap["page"] != float64(1) || metaMap["limit"] != float64(20) {
			t.Errorf("unexpected meta: %v", metaMap)
		}
	})
}

func TestAppErrorConstructorsAndWriteError(t *testing.T) {
	cases := []struct {
		name       string
		err        *httpx.AppError
		wantStatus int
		wantCode   string
	}{
		{"Unauthorized", httpx.Unauthorized(""), http.StatusUnauthorized, "UNAUTHORIZED"},
		{"TokenExpired", httpx.TokenExpired(""), http.StatusUnauthorized, "TOKEN_EXPIRED"},
		{"IdentityNotVerified", httpx.IdentityNotVerified(""), http.StatusForbidden, "IDENTITY_NOT_VERIFIED"},
		{"DriverNotApproved", httpx.DriverNotApproved(""), http.StatusForbidden, "DRIVER_NOT_APPROVED"},
		{"UserRestricted", httpx.UserRestricted(""), http.StatusForbidden, "USER_RESTRICTED"},
		{"Forbidden", httpx.Forbidden(""), http.StatusForbidden, "FORBIDDEN"},
		{"ActiveTripLimit", httpx.ActiveTripLimit(""), http.StatusConflict, "ACTIVE_TRIP_LIMIT"},
		{"CommissionBalanceLow", httpx.CommissionBalanceLow(""), http.StatusConflict, "COMMISSION_BALANCE_LOW"},
		{"InsufficientCapacity", httpx.InsufficientCapacity(""), http.StatusConflict, "INSUFFICIENT_CAPACITY"},
		{"HoldExpired", httpx.HoldExpired(""), http.StatusConflict, "HOLD_EXPIRED"},
		{"MaxAttemptsReached", httpx.MaxAttemptsReached(""), http.StatusConflict, "MAX_ATTEMPTS_REACHED"},
		{"ModificationLimitReached", httpx.ModificationLimitReached(""), http.StatusConflict, "MODIFICATION_LIMIT_REACHED"},
		{"CalendarDeadlineExpired", httpx.CalendarDeadlineExpired(""), http.StatusUnprocessableEntity, "CALENDAR_DEADLINE_EXPIRED"},
		{"Conflict", httpx.Conflict("", ""), http.StatusConflict, "CONFLICT"},
		{"BadRequest", httpx.BadRequest(""), http.StatusBadRequest, "BAD_REQUEST"},
		{"NotFound", httpx.NotFound(""), http.StatusNotFound, "NOT_FOUND"},
		{"Validation", httpx.Validation("", map[string]string{"campo": "requerido"}), http.StatusUnprocessableEntity, "VALIDATION_ERROR"},
		{"RateLimited", httpx.RateLimited(""), http.StatusTooManyRequests, "RATE_LIMITED"},
		{"Internal", httpx.Internal(errors.New("db password leaked")), http.StatusInternalServerError, "INTERNAL_ERROR"},
	}

	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			rec := httptest.NewRecorder()
			httpx.WriteError(rec, tc.err)

			if rec.Code != tc.wantStatus {
				t.Fatalf("status = %d, want %d", rec.Code, tc.wantStatus)
			}

			var env struct {
				Success bool             `json:"success"`
				Data    any              `json:"data"`
				Meta    *httpx.Meta      `json:"meta"`
				Error   *httpx.ErrorBody `json:"error"`
			}
			if err := json.Unmarshal(rec.Body.Bytes(), &env); err != nil {
				t.Fatalf("unmarshal error body: %v", err)
			}
			if env.Success {
				t.Errorf("expected success=false")
			}
			if env.Error == nil || env.Error.Code != tc.wantCode {
				t.Fatalf("error = %+v, want code %q", env.Error, tc.wantCode)
			}
			if tc.wantStatus >= 500 && strings.Contains(rec.Body.String(), "db password leaked") {
				t.Fatalf("500 response leaked internal error cause")
			}
		})
	}

	t.Run("HTTPStatusCoder interface handled", func(t *testing.T) {
		rec := httptest.NewRecorder()
		httpx.WriteError(rec, customDomainError{})
		if rec.Code != http.StatusConflict {
			t.Fatalf("status = %d, want 409", rec.Code)
		}
		if !strings.Contains(rec.Body.String(), "CUSTOM_CONFLICT") {
			t.Errorf("expected CUSTOM_CONFLICT in body, got %s", rec.Body.String())
		}
	})

	t.Run("raw pgx and pgconn errors mapped cleanly", func(t *testing.T) {
		rec1 := httptest.NewRecorder()
		httpx.WriteError(rec1, pgx.ErrNoRows)
		if rec1.Code != http.StatusNotFound {
			t.Errorf("ErrNoRows status = %d, want 404", rec1.Code)
		}

		rec2 := httptest.NewRecorder()
		httpx.WriteError(rec2, &pgconn.PgError{Code: "23505", ConstraintName: "uq_conductor_viaje_activo"})
		if rec2.Code != http.StatusConflict || !strings.Contains(rec2.Body.String(), "ACTIVE_TRIP_LIMIT") {
			t.Errorf("expected 409 ACTIVE_TRIP_LIMIT, got %d %s", rec2.Code, rec2.Body.String())
		}
	})

	t.Run("unknown error falls back to 500 without leaking internals", func(t *testing.T) {
		rec := httptest.NewRecorder()
		httpx.WriteError(rec, errors.New("pq: secret internal stack trace"))
		if rec.Code != http.StatusInternalServerError {
			t.Fatalf("status = %d, want 500", rec.Code)
		}
		body := rec.Body.String()
		if strings.Contains(body, "secret internal stack trace") {
			t.Fatalf("response leaked internal error: %s", body)
		}
		if !strings.Contains(body, "INTERNAL_ERROR") {
			t.Errorf("expected INTERNAL_ERROR in body, got %s", body)
		}
	})
}

func TestDecodeJSON(t *testing.T) {
	type samplePayload struct {
		Name  string `json:"name"`
		Seats int    `json:"seats"`
	}

	t.Run("valid json decodes", func(t *testing.T) {
		req := httptest.NewRequest(http.MethodPost, "/", strings.NewReader(`{"name":"Lima","seats":2}`))
		var dst samplePayload
		if err := httpx.DecodeJSON(req, &dst); err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		if dst.Name != "Lima" || dst.Seats != 2 {
			t.Errorf("unexpected decoded struct: %+v", dst)
		}
	})

	t.Run("unknown field rejected by default", func(t *testing.T) {
		req := httptest.NewRequest(http.MethodPost, "/", strings.NewReader(`{"name":"Lima","extra":1}`))
		var dst samplePayload
		err := httpx.DecodeJSON(req, &dst)
		var appErr *httpx.AppError
		if !errors.As(err, &appErr) || appErr.HTTPStatus() != http.StatusBadRequest {
			t.Fatalf("expected 400 AppError, got %v", err)
		}
	})

	t.Run("unknown field allowed via option", func(t *testing.T) {
		req := httptest.NewRequest(http.MethodPost, "/", strings.NewReader(`{"name":"Lima","extra":1}`))
		var dst samplePayload
		if err := httpx.DecodeJSON(req, &dst, httpx.WithDisallowUnknownFields(false)); err != nil {
			t.Fatalf("expected success when unknown fields allowed, got %v", err)
		}
	})

	t.Run("body exceeding max bytes returns 413", func(t *testing.T) {
		oversized := `{"name":"` + strings.Repeat("a", 64) + `"}`
		req := httptest.NewRequest(http.MethodPost, "/", strings.NewReader(oversized))
		var dst samplePayload
		err := httpx.DecodeJSON(req, &dst, httpx.WithMaxBytes(32))
		var appErr *httpx.AppError
		if !errors.As(err, &appErr) || appErr.HTTPStatus() != http.StatusRequestEntityTooLarge || appErr.ErrorCode() != "PAYLOAD_TOO_LARGE" {
			t.Fatalf("expected 413 PAYLOAD_TOO_LARGE, got %v", err)
		}
	})

	t.Run("empty body returns 400", func(t *testing.T) {
		req := httptest.NewRequest(http.MethodPost, "/", strings.NewReader(""))
		var dst samplePayload
		err := httpx.DecodeJSON(req, &dst)
		var appErr *httpx.AppError
		if !errors.As(err, &appErr) || appErr.HTTPStatus() != http.StatusBadRequest {
			t.Fatalf("expected 400 AppError, got %v", err)
		}
	})

	t.Run("trailing json value rejected", func(t *testing.T) {
		req := httptest.NewRequest(http.MethodPost, "/", strings.NewReader(`{"name":"A"}{"name":"B"}`))
		var dst samplePayload
		err := httpx.DecodeJSON(req, &dst)
		var appErr *httpx.AppError
		if !errors.As(err, &appErr) || appErr.HTTPStatus() != http.StatusBadRequest {
			t.Fatalf("expected 400 AppError, got %v", err)
		}
	})
}

func TestMiddlewares(t *testing.T) {
	t.Run("RequestID generates and propagates header and context", func(t *testing.T) {
		var gotCtxID string
		h := httpx.RequestID(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			gotCtxID = httpx.RequestIDFromContext(r.Context())
			w.WriteHeader(http.StatusOK)
		}))

		// Generated when absent.
		rec := httptest.NewRecorder()
		h.ServeHTTP(rec, httptest.NewRequest(http.MethodGet, "/", nil))
		if gotCtxID == "" || rec.Header().Get(httpx.RequestIDHeader) != gotCtxID {
			t.Errorf("expected generated request ID in header and ctx, got header=%q ctx=%q", rec.Header().Get(httpx.RequestIDHeader), gotCtxID)
		}

		// Preserved when valid.
		req := httptest.NewRequest(http.MethodGet, "/", nil)
		req.Header.Set(httpx.RequestIDHeader, "req-123-abc")
		rec2 := httptest.NewRecorder()
		h.ServeHTTP(rec2, req)
		if gotCtxID != "req-123-abc" || rec2.Header().Get(httpx.RequestIDHeader) != "req-123-abc" {
			t.Errorf("expected req-123-abc preserved, got ctx=%q header=%q", gotCtxID, rec2.Header().Get(httpx.RequestIDHeader))
		}
	})

	t.Run("StructuredLogger logs request fields", func(t *testing.T) {
		var buf bytes.Buffer
		logger := slog.New(slog.NewJSONHandler(&buf, nil))

		h := httpx.RequestID(httpx.StructuredLogger(logger)(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			httpx.WriteJSON(w, http.StatusCreated, map[string]int{"ok": 1}, nil)
		})))

		rec := httptest.NewRecorder()
		h.ServeHTTP(rec, httptest.NewRequest(http.MethodPost, "/api/v1/trips", nil))
		if rec.Code != http.StatusCreated {
			t.Fatalf("status = %d, want 201", rec.Code)
		}

		logLine := buf.String()
		for _, expected := range []string{`"method":"POST"`, `"/api/v1/trips"`, `"status":201`, `"request_id"`} {
			if !strings.Contains(logLine, expected) {
				t.Errorf("expected %s in log line: %s", expected, logLine)
			}
		}
	})

	t.Run("Recoverer catches panic and returns 500 envelope", func(t *testing.T) {
		var buf bytes.Buffer
		logger := slog.New(slog.NewJSONHandler(&buf, nil))

		h := httpx.Recoverer(logger)(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			panic("unexpected nil pointer secret")
		}))

		rec := httptest.NewRecorder()
		h.ServeHTTP(rec, httptest.NewRequest(http.MethodGet, "/boom", nil))
		if rec.Code != http.StatusInternalServerError {
			t.Fatalf("status = %d, want 500", rec.Code)
		}
		if strings.Contains(rec.Body.String(), "unexpected nil pointer secret") {
			t.Fatalf("client response leaked panic message")
		}
		if !strings.Contains(buf.String(), "panic_recovered") {
			t.Errorf("expected panic_recovered in server logs, got %s", buf.String())
		}
	})

	t.Run("CORS sets headers and handles preflight", func(t *testing.T) {
		h := httpx.CORS([]string{"http://localhost:3000"})(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			w.WriteHeader(http.StatusOK)
		}))

		req := httptest.NewRequest(http.MethodOptions, "/api/v1/trips", nil)
		req.Header.Set("Origin", "http://localhost:3000")
		rec := httptest.NewRecorder()
		h.ServeHTTP(rec, req)

		if rec.Code != http.StatusNoContent {
			t.Fatalf("preflight status = %d, want 204", rec.Code)
		}
		if got := rec.Header().Get("Access-Control-Allow-Origin"); got != "http://localhost:3000" {
			t.Errorf("Allow-Origin = %q, want http://localhost:3000", got)
		}

		reqEvil := httptest.NewRequest(http.MethodGet, "/api/v1/trips", nil)
		reqEvil.Header.Set("Origin", "https://evil.example")
		recEvil := httptest.NewRecorder()
		h.ServeHTTP(recEvil, reqEvil)
		if got := recEvil.Header().Get("Access-Control-Allow-Origin"); got != "" {
			t.Errorf("expected empty Allow-Origin for disallowed origin, got %q", got)
		}
	})

	t.Run("Timeout attaches deadline to context", func(t *testing.T) {
		var hasDeadline bool
		var deadline time.Time
		h := httpx.Timeout(2 * time.Second)(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			deadline, hasDeadline = r.Context().Deadline()
			w.WriteHeader(http.StatusOK)
		}))

		rec := httptest.NewRecorder()
		h.ServeHTTP(rec, httptest.NewRequest(http.MethodGet, "/", nil))
		if !hasDeadline {
			t.Fatalf("expected request context to have a deadline")
		}
		if time.Until(deadline) <= 0 || time.Until(deadline) > 3*time.Second {
			t.Errorf("unexpected deadline delta: %v", time.Until(deadline))
		}
	})
}

func TestRateLimiter(t *testing.T) {
	t.Run("concurrent requests enforce limit and return 429 RATE_LIMITED", func(t *testing.T) {
		rl := httpx.NewRateLimiter(5, 80*time.Millisecond, nil)
		h := rl.Middleware(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			httpx.WriteJSON(w, http.StatusOK, map[string]bool{"ok": true}, nil)
		}))

		const total = 12
		var wg sync.WaitGroup
		var mu sync.Mutex
		codes := make(map[int]int)
		var sawRetryAfter bool

		for i := 0; i < total; i++ {
			wg.Add(1)
			go func() {
				defer wg.Done()
				req := httptest.NewRequest(http.MethodPost, "/auth/otp/send", nil)
				req.RemoteAddr = "190.12.34.56:12345"
				rec := httptest.NewRecorder()
				h.ServeHTTP(rec, req)

				mu.Lock()
				codes[rec.Code]++
				if rec.Code == http.StatusTooManyRequests && rec.Header().Get("Retry-After") != "" {
					sawRetryAfter = true
				}
				mu.Unlock()
			}()
		}
		wg.Wait()

		if codes[http.StatusOK] != 5 {
			t.Errorf("200 count = %d, want 5", codes[http.StatusOK])
		}
		if codes[http.StatusTooManyRequests] != 7 {
			t.Errorf("429 count = %d, want 7", codes[http.StatusTooManyRequests])
		}
		if !sawRetryAfter {
			t.Errorf("expected Retry-After header on 429 response")
		}

		// Different IP is unaffected.
		reqOther := httptest.NewRequest(http.MethodPost, "/auth/otp/send", nil)
		reqOther.Header.Set("X-Forwarded-For", "190.99.88.77, 10.0.0.1")
		recOther := httptest.NewRecorder()
		h.ServeHTTP(recOther, reqOther)
		if recOther.Code != http.StatusOK {
			t.Errorf("other IP status = %d, want 200", recOther.Code)
		}

		// Window resets after expiry.
		time.Sleep(100 * time.Millisecond)
		reqAfter := httptest.NewRequest(http.MethodPost, "/auth/otp/send", nil)
		reqAfter.RemoteAddr = "190.12.34.56:12345"
		recAfter := httptest.NewRecorder()
		h.ServeHTTP(recAfter, reqAfter)
		if recAfter.Code != http.StatusOK {
			t.Errorf("after window reset status = %d, want 200", recAfter.Code)
		}
	})

	t.Run("RequestIDFromContext handles nil context", func(t *testing.T) {
		var nilCtx context.Context
		if got := httpx.RequestIDFromContext(nilCtx); got != "" {
			t.Errorf("expected empty string for nil ctx, got %q", got)
		}
	})
}
