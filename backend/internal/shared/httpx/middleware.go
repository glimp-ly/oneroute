package httpx

import (
	"bufio"
	"context"
	"fmt"
	"log/slog"
	"net"
	"net/http"
	"runtime/debug"
	"strconv"
	"strings"
	"sync"
	"time"

	"github.com/google/uuid"
)

type ctxKey string

const requestIDKey ctxKey = "httpx_request_id"

// RequestIDHeader is the HTTP header used to propagate correlation IDs.
const RequestIDHeader = "X-Request-ID"

// RequestIDFromContext returns the correlation ID stored in ctx, or empty string.
func RequestIDFromContext(ctx context.Context) string {
	if ctx == nil {
		return ""
	}
	id, _ := ctx.Value(requestIDKey).(string)
	return id
}

// RequestID reads X-Request-ID or generates a new UUID, stores it in the request
// context, and echoes it on the response X-Request-ID header.
func RequestID(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		id := sanitizeRequestID(r.Header.Get(RequestIDHeader))
		if id == "" {
			id = uuid.NewString()
		}
		w.Header().Set(RequestIDHeader, id)
		ctx := context.WithValue(r.Context(), requestIDKey, id)
		next.ServeHTTP(w, r.WithContext(ctx))
	})
}

func sanitizeRequestID(raw string) string {
	trimmed := strings.TrimSpace(raw)
	if trimmed == "" || len(trimmed) > 128 {
		return ""
	}
	for i := 0; i < len(trimmed); i++ {
		if trimmed[i] < 0x20 || trimmed[i] == 0x7f {
			return ""
		}
	}
	return trimmed
}

type responseRecorder struct {
	http.ResponseWriter
	status      int
	bytes       int
	wroteHeader bool
}

func (rw *responseRecorder) WriteHeader(code int) {
	if rw.wroteHeader {
		return
	}
	rw.status = code
	rw.wroteHeader = true
	rw.ResponseWriter.WriteHeader(code)
}

func (rw *responseRecorder) Write(p []byte) (int, error) {
	if !rw.wroteHeader {
		rw.WriteHeader(http.StatusOK)
	}
	n, err := rw.ResponseWriter.Write(p)
	rw.bytes += n
	return n, err
}

// Unwrap supports http.ResponseController (Flusher, SetWriteDeadline, etc.).
func (rw *responseRecorder) Unwrap() http.ResponseWriter {
	return rw.ResponseWriter
}

// Hijack forwards WebSocket connection upgrades to the underlying ResponseWriter.
func (rw *responseRecorder) Hijack() (net.Conn, *bufio.ReadWriter, error) {
	h, ok := rw.ResponseWriter.(http.Hijacker)
	if !ok {
		return nil, nil, fmt.Errorf("httpx: underlying ResponseWriter does not implement http.Hijacker")
	}
	return h.Hijack()
}

// Flush forwards streaming flushes if supported.
func (rw *responseRecorder) Flush() {
	if f, ok := rw.ResponseWriter.(http.Flusher); ok {
		f.Flush()
	}
}

// StructuredLogger logs HTTP requests in structured slog fields.
func StructuredLogger(logger *slog.Logger) func(http.Handler) http.Handler {
	if logger == nil {
		logger = slog.Default()
	}
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			start := time.Now()
			rec := &responseRecorder{ResponseWriter: w, status: http.StatusOK}
			next.ServeHTTP(rec, r)

			duration := time.Since(start)
			attrs := []slog.Attr{
				slog.String("method", r.Method),
				slog.String("path", r.URL.Path),
				slog.Int("status", rec.status),
				slog.Int("bytes", rec.bytes),
				slog.Float64("duration_ms", float64(duration.Microseconds())/1000.0),
				slog.String("request_id", RequestIDFromContext(r.Context())),
				slog.String("remote_addr", r.RemoteAddr),
			}

			level := slog.LevelInfo
			if rec.status >= http.StatusInternalServerError {
				level = slog.LevelError
			} else if rec.status >= http.StatusBadRequest {
				level = slog.LevelWarn
			}
			logger.LogAttrs(r.Context(), level, "http_request", attrs...)
		})
	}
}

// Recoverer catches panics, logs stack traces server-side, and returns a safe
// 500 INTERNAL_ERROR envelope when headers have not yet been sent.
func Recoverer(logger *slog.Logger) func(http.Handler) http.Handler {
	if logger == nil {
		logger = slog.Default()
	}
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			rec := &responseRecorder{ResponseWriter: w, status: http.StatusOK}
			defer func() {
				if rv := recover(); rv != nil {
					if rv == http.ErrAbortHandler {
						panic(rv)
					}
					logger.ErrorContext(r.Context(), "panic_recovered",
						slog.Any("panic", rv),
						slog.String("request_id", RequestIDFromContext(r.Context())),
						slog.String("method", r.Method),
						slog.String("path", r.URL.Path),
						slog.String("stack", string(debug.Stack())),
					)
					if !rec.wroteHeader {
						WriteError(rec, Internal(nil))
					}
				}
			}()
			next.ServeHTTP(rec, r)
		})
	}
}

// CORS configures Cross-Origin Resource Sharing headers and handles preflight OPTIONS requests.
func CORS(allowedOrigins []string) func(http.Handler) http.Handler {
	allowAll := false
	allowed := make(map[string]struct{}, len(allowedOrigins))
	for _, o := range allowedOrigins {
		trimmed := strings.TrimSpace(o)
		if trimmed == "" {
			continue
		}
		if trimmed == "*" {
			allowAll = true
			continue
		}
		allowed[trimmed] = struct{}{}
	}

	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			origin := r.Header.Get("Origin")
			if origin != "" {
				if allowAll {
					w.Header().Set("Access-Control-Allow-Origin", "*")
					w.Header().Add("Vary", "Origin")
				} else if _, ok := allowed[origin]; ok {
					w.Header().Set("Access-Control-Allow-Origin", origin)
					w.Header().Set("Access-Control-Allow-Credentials", "true")
					w.Header().Add("Vary", "Origin")
				}
			}

			if r.Method == http.MethodOptions {
				w.Header().Set("Access-Control-Allow-Methods", "GET, POST, PUT, PATCH, DELETE, OPTIONS")
				w.Header().Set("Access-Control-Allow-Headers", "Authorization, Content-Type, Idempotency-Key, X-Request-ID, X-Mock-Payment-Result")
				w.Header().Set("Access-Control-Expose-Headers", RequestIDHeader)
				w.Header().Set("Access-Control-Max-Age", "3600")
				w.WriteHeader(http.StatusNoContent)
				return
			}

			w.Header().Set("Access-Control-Expose-Headers", RequestIDHeader)
			next.ServeHTTP(w, r)
		})
	}
}

// Timeout applies a request context deadline (defaulting to 15s when d <= 0)
// without buffering responses so streaming and WebSocket upgrades remain intact.
func Timeout(d time.Duration) func(http.Handler) http.Handler {
	if d <= 0 {
		d = 15 * time.Second
	}
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			ctx, cancel := context.WithTimeout(r.Context(), d)
			defer cancel()
			next.ServeHTTP(w, r.WithContext(ctx))
		})
	}
}

type rateBucket struct {
	count   int
	resetAt time.Time
}

// RateLimiter provides thread-safe in-memory per-key fixed-window rate limiting.
type RateLimiter struct {
	mu      sync.Mutex
	limit   int
	window  time.Duration
	keyFn   func(*http.Request) string
	buckets map[string]rateBucket
	nowFn   func() time.Time
}

// NewRateLimiter constructs a RateLimiter allowing limit requests per window per key.
// If keyFn is nil, IPKeyFunc is used.
func NewRateLimiter(limit int, window time.Duration, keyFn func(*http.Request) string) *RateLimiter {
	if limit <= 0 {
		limit = 60
	}
	if window <= 0 {
		window = time.Minute
	}
	if keyFn == nil {
		keyFn = IPKeyFunc
	}
	return &RateLimiter{
		limit:   limit,
		window:  window,
		keyFn:   keyFn,
		buckets: make(map[string]rateBucket),
		nowFn:   time.Now,
	}
}

// IPKeyFunc extracts the client IP from X-Forwarded-For, X-Real-IP, or RemoteAddr.
func IPKeyFunc(r *http.Request) string {
	if r == nil {
		return "unknown"
	}
	if xff := r.Header.Get("X-Forwarded-For"); xff != "" {
		first, _, _ := strings.Cut(xff, ",")
		if ip := strings.TrimSpace(first); ip != "" {
			return ip
		}
	}
	if xrip := strings.TrimSpace(r.Header.Get("X-Real-IP")); xrip != "" {
		return xrip
	}
	host, _, err := net.SplitHostPort(strings.TrimSpace(r.RemoteAddr))
	if err == nil && host != "" {
		return host
	}
	if r.RemoteAddr != "" {
		return r.RemoteAddr
	}
	return "unknown"
}

// Allow checks and consumes one request quota unit for key.
func (rl *RateLimiter) Allow(key string) (allowed bool, retryAfter time.Duration) {
	now := rl.nowFn()
	rl.mu.Lock()
	defer rl.mu.Unlock()

	if len(rl.buckets) > 1024 {
		for k, b := range rl.buckets {
			if !now.Before(b.resetAt) {
				delete(rl.buckets, k)
			}
		}
	}

	b, ok := rl.buckets[key]
	if !ok || !now.Before(b.resetAt) {
		rl.buckets[key] = rateBucket{
			count:   1,
			resetAt: now.Add(rl.window),
		}
		return true, 0
	}

	if b.count >= rl.limit {
		return false, b.resetAt.Sub(now)
	}

	b.count++
	rl.buckets[key] = b
	return true, 0
}

// Middleware enforces the rate limit and writes 429 RATE_LIMITED when exceeded.
func (rl *RateLimiter) Middleware(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		key := rl.keyFn(r)
		allowed, retryAfter := rl.Allow(key)
		if !allowed {
			secs := int(retryAfter.Seconds())
			if secs < 1 {
				secs = 1
			}
			w.Header().Set("Retry-After", strconv.Itoa(secs))
			WriteError(w, RateLimited("Demasiadas solicitudes. Intente nuevamente en unos segundos."))
			return
		}
		next.ServeHTTP(w, r)
	})
}
