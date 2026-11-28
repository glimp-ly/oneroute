package auth_test

import (
	"context"
	"encoding/hex"
	"encoding/json"
	"errors"
	"net/http"
	"net/http/httptest"
	"reflect"
	"slices"
	"strings"
	"testing"
	"time"

	"github.com/golang-jwt/jwt/v5"
	"github.com/google/uuid"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgconn"
	"golang.org/x/crypto/bcrypt"

	"github.com/glimp-ly/oneroute/backend/internal/shared/auth"
	"github.com/glimp-ly/oneroute/backend/internal/shared/httpx"
	"github.com/glimp-ly/oneroute/backend/internal/shared/realtime"
)

type stubRow struct {
	val bool
	err error
}

func (r stubRow) Scan(dest ...any) error {
	if r.err != nil {
		return r.err
	}
	if len(dest) == 0 {
		return errors.New("stubRow: no scan destinations")
	}
	ptr, ok := dest[0].(*bool)
	if !ok {
		return errors.New("stubRow: expected *bool scan destination")
	}
	*ptr = r.val
	return nil
}

type queryCall struct {
	sql  string
	args []any
}

type stubDBTX struct {
	fn    func(ctx context.Context, sql string, args ...any) pgx.Row
	calls []queryCall
}

func (s *stubDBTX) Exec(_ context.Context, _ string, _ ...any) (pgconn.CommandTag, error) {
	return pgconn.CommandTag{}, nil
}

func (s *stubDBTX) Query(_ context.Context, _ string, _ ...any) (pgx.Rows, error) {
	return nil, errors.New("stubDBTX.Query not implemented")
}

func (s *stubDBTX) QueryRow(ctx context.Context, sql string, args ...any) pgx.Row {
	s.calls = append(s.calls, queryCall{sql: sql, args: args})
	if s.fn != nil {
		return s.fn(ctx, sql, args...)
	}
	return stubRow{val: true}
}

func newTestManager(t *testing.T, accessTTL, refreshTTL time.Duration) *auth.Manager {
	t.Helper()
	mgr, err := auth.NewManager(
		"test-access-secret-key-at-least-32-chars",
		"test-refresh-secret-key-at-least-32-chars",
		accessTTL,
		refreshTTL,
	)
	if err != nil {
		t.Fatalf("NewManager failed: %v", err)
	}
	return mgr
}

func decodeEnvelope(t *testing.T, rec *httptest.ResponseRecorder) httpx.Envelope {
	t.Helper()
	var env httpx.Envelope
	if err := json.Unmarshal(rec.Body.Bytes(), &env); err != nil {
		t.Fatalf("decode response envelope failed: %v (body=%q)", err, rec.Body.String())
	}
	return env
}

func TestNewManagerValidation(t *testing.T) {
	cases := []struct {
		name          string
		accessSecret  string
		refreshSecret string
		accessTTL     time.Duration
		refreshTTL    time.Duration
		wantErr       bool
	}{
		{
			name:          "valid config",
			accessSecret:  "access-secret",
			refreshSecret: "refresh-secret",
			accessTTL:     15 * time.Minute,
			refreshTTL:    30 * 24 * time.Hour,
			wantErr:       false,
		},
		{
			name:          "empty access secret",
			accessSecret:  "   ",
			refreshSecret: "refresh-secret",
			accessTTL:     15 * time.Minute,
			refreshTTL:    24 * time.Hour,
			wantErr:       true,
		},
		{
			name:          "empty refresh secret",
			accessSecret:  "access-secret",
			refreshSecret: "",
			accessTTL:     15 * time.Minute,
			refreshTTL:    24 * time.Hour,
			wantErr:       true,
		},
		{
			name:          "non-positive access ttl",
			accessSecret:  "access-secret",
			refreshSecret: "refresh-secret",
			accessTTL:     0,
			refreshTTL:    24 * time.Hour,
			wantErr:       true,
		},
		{
			name:          "non-positive refresh ttl",
			accessSecret:  "access-secret",
			refreshSecret: "refresh-secret",
			accessTTL:     15 * time.Minute,
			refreshTTL:    -time.Second,
			wantErr:       true,
		},
	}

	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			mgr, err := auth.NewManager(tc.accessSecret, tc.refreshSecret, tc.accessTTL, tc.refreshTTL)
			if tc.wantErr {
				if err == nil {
					t.Fatalf("expected error, got nil")
				}
				return
			}
			if err != nil {
				t.Fatalf("unexpected error: %v", err)
			}
			if mgr.AccessTTL() != tc.accessTTL {
				t.Errorf("AccessTTL() = %v, want %v", mgr.AccessTTL(), tc.accessTTL)
			}
			if mgr.RefreshTTL() != tc.refreshTTL {
				t.Errorf("RefreshTTL() = %v, want %v", mgr.RefreshTTL(), tc.refreshTTL)
			}
		})
	}
}

func TestAccessTokenSignAndParse(t *testing.T) {
	mgr := newTestManager(t, 15*time.Minute, 30*24*time.Hour)
	userID := uuid.New()
	sessionID := uuid.New()
	roles := []string{"pasajero", "conductor"}

	t.Run("round trip valid HS256 token", func(t *testing.T) {
		tok, err := mgr.GenerateAccessToken(userID, sessionID, roles, true, false)
		if err != nil {
			t.Fatalf("GenerateAccessToken failed: %v", err)
		}
		// Mutate caller slice to verify defensive copy.
		roles[0] = "admin"

		claims, err := mgr.ParseAccessToken(tok)
		if err != nil {
			t.Fatalf("ParseAccessToken failed: %v", err)
		}
		if claims.UserID != userID {
			t.Errorf("UserID = %s, want %s", claims.UserID, userID)
		}
		if claims.SessionID != sessionID {
			t.Errorf("SessionID = %s, want %s", claims.SessionID, sessionID)
		}
		if !reflect.DeepEqual(claims.Roles, []string{"pasajero", "conductor"}) {
			t.Errorf("Roles = %v, want [pasajero conductor]", claims.Roles)
		}
		if !claims.IdentityVerified {
			t.Errorf("IdentityVerified = false, want true")
		}
		if claims.DriverApproved {
			t.Errorf("DriverApproved = true, want false")
		}
		if claims.ExpiresAt == nil || claims.IssuedAt == nil {
			t.Errorf("expected ExpiresAt and IssuedAt to be populated")
		}
	})

	t.Run("reject nil user or session ID", func(t *testing.T) {
		if _, err := mgr.GenerateAccessToken(uuid.Nil, sessionID, nil, false, false); err == nil {
			t.Errorf("expected error for nil userID")
		}
		if _, err := mgr.GenerateAccessToken(userID, uuid.Nil, nil, false, false); err == nil {
			t.Errorf("expected error for nil sessionID")
		}
	})

	t.Run("expired token returns ErrTokenExpired", func(t *testing.T) {
		past := time.Now().UTC().Add(-5 * time.Minute)
		expiredClaims := auth.Claims{
			UserID:    userID,
			SessionID: sessionID,
			Roles:     []string{"pasajero"},
			RegisteredClaims: jwt.RegisteredClaims{
				IssuedAt:  jwt.NewNumericDate(past.Add(-15 * time.Minute)),
				NotBefore: jwt.NewNumericDate(past.Add(-15 * time.Minute)),
				ExpiresAt: jwt.NewNumericDate(past),
			},
		}
		tok, err := jwt.NewWithClaims(jwt.SigningMethodHS256, expiredClaims).SignedString([]byte("test-access-secret-key-at-least-32-chars"))
		if err != nil {
			t.Fatalf("sign expired token failed: %v", err)
		}

		_, err = mgr.ParseAccessToken(tok)
		if !errors.Is(err, auth.ErrTokenExpired) {
			t.Fatalf("err = %v, want ErrTokenExpired", err)
		}
		if errors.Is(err, auth.ErrInvalidToken) {
			t.Fatalf("expired token must not match ErrInvalidToken")
		}
	})

	t.Run("invalid tokens return ErrInvalidToken", func(t *testing.T) {
		validTok, err := mgr.GenerateAccessToken(userID, sessionID, nil, false, false)
		if err != nil {
			t.Fatalf("GenerateAccessToken failed: %v", err)
		}

		otherMgr, _ := auth.NewManager("another-secret-key-completely-different", "refresh-secret", 15*time.Minute, time.Hour)
		wrongSecretTok, _ := otherMgr.GenerateAccessToken(userID, sessionID, nil, false, false)

		algNoneTok, _ := jwt.NewWithClaims(jwt.SigningMethodNone, auth.Claims{
			UserID:    userID,
			SessionID: sessionID,
			RegisteredClaims: jwt.RegisteredClaims{
				ExpiresAt: jwt.NewNumericDate(time.Now().Add(time.Hour)),
			},
		}).SignedString(jwt.UnsafeAllowNoneSignatureType)

		missingSubTok, _ := jwt.NewWithClaims(jwt.SigningMethodHS256, auth.Claims{
			UserID:    uuid.Nil,
			SessionID: sessionID,
			RegisteredClaims: jwt.RegisteredClaims{
				ExpiresAt: jwt.NewNumericDate(time.Now().Add(time.Hour)),
			},
		}).SignedString([]byte("test-access-secret-key-at-least-32-chars"))

		tampered := validTok[:len(validTok)-2] + "aa"
		if tampered == validTok {
			tampered = validTok[:len(validTok)-2] + "bb"
		}

		invalidInputs := []struct {
			name  string
			token string
		}{
			{name: "empty", token: ""},
			{name: "malformed", token: "not.a.jwt"},
			{name: "tampered signature", token: tampered},
			{name: "wrong secret", token: wrongSecretTok},
			{name: "alg none rejected", token: algNoneTok},
			{name: "nil user id", token: missingSubTok},
		}

		for _, tc := range invalidInputs {
			t.Run(tc.name, func(t *testing.T) {
				_, err := mgr.ParseAccessToken(tc.token)
				if !errors.Is(err, auth.ErrInvalidToken) {
					t.Fatalf("err = %v, want ErrInvalidToken", err)
				}
			})
		}
	})
}

func TestRefreshTokenGenerationAndHashing(t *testing.T) {
	mgr := newTestManager(t, 15*time.Minute, 30*24*time.Hour)

	raw1, hash1, err := mgr.GenerateRefreshToken()
	if err != nil {
		t.Fatalf("GenerateRefreshToken failed: %v", err)
	}
	raw2, hash2, err := mgr.GenerateRefreshToken()
	if err != nil {
		t.Fatalf("GenerateRefreshToken 2 failed: %v", err)
	}

	if len(raw1) != 64 {
		t.Errorf("len(rawHex) = %d, want 64", len(raw1))
	}
	if _, err := hex.DecodeString(raw1); err != nil {
		t.Errorf("rawHex is not valid hex: %v", err)
	}
	if len(hash1) != 64 {
		t.Errorf("len(sha256Hex) = %d, want 64", len(hash1))
	}
	if got := mgr.HashRefreshToken(raw1); got != hash1 {
		t.Errorf("HashRefreshToken = %q, want %q", got, hash1)
	}
	if raw1 == raw2 || hash1 == hash2 {
		t.Errorf("expected unique random refresh tokens")
	}
}

func TestContextHelpers(t *testing.T) {
	if c, ok := auth.ClaimsFromContext(nil); ok || c != nil {
		t.Errorf("ClaimsFromContext(nil) = (%v, %v), want (nil, false)", c, ok)
	}
	if id, ok := auth.UserIDFromContext(nil); ok || id != uuid.Nil {
		t.Errorf("UserIDFromContext(nil) = (%v, %v), want (Nil, false)", id, ok)
	}

	userID := uuid.New()
	claims := &auth.Claims{UserID: userID, SessionID: uuid.New()}
	ctx := auth.WithClaims(context.Background(), claims)

	got, ok := auth.ClaimsFromContext(ctx)
	if !ok || got == nil || got.UserID != userID {
		t.Fatalf("ClaimsFromContext failed: got=(%v, %v)", got, ok)
	}
	gotID, ok := auth.UserIDFromContext(ctx)
	if !ok || gotID != userID {
		t.Fatalf("UserIDFromContext failed: got=(%v, %v)", gotID, ok)
	}
}

func TestPasswordHashingAndVerification(t *testing.T) {
	plain := "Password123!"
	hash, err := auth.HashPassword(plain)
	if err != nil {
		t.Fatalf("HashPassword failed: %v", err)
	}

	cost, err := bcrypt.Cost([]byte(hash))
	if err != nil {
		t.Fatalf("bcrypt.Cost failed: %v", err)
	}
	if cost != auth.DefaultBcryptCost || cost != 10 {
		t.Errorf("bcrypt cost = %d, want 10", cost)
	}

	if !auth.VerifyPassword(hash, plain) {
		t.Errorf("VerifyPassword returned false for matching password")
	}
	if auth.VerifyPassword(hash, "WrongPass!") {
		t.Errorf("VerifyPassword returned true for wrong password")
	}
	if auth.VerifyPassword("", plain) {
		t.Errorf("VerifyPassword returned true for empty hash")
	}
	if auth.VerifyPassword(hash, "") {
		t.Errorf("VerifyPassword returned true for empty plaintext")
	}
	if _, err := auth.HashPassword(""); err == nil {
		t.Errorf("HashPassword should reject empty password")
	}
}

func TestRequireAuthAndAuthenticateToken(t *testing.T) {
	mgr := newTestManager(t, 15*time.Minute, 30*24*time.Hour)
	userID := uuid.New()
	sessionID := uuid.New()
	validToken, err := mgr.GenerateAccessToken(userID, sessionID, []string{"pasajero"}, true, false)
	if err != nil {
		t.Fatalf("GenerateAccessToken failed: %v", err)
	}

	past := time.Now().UTC().Add(-time.Minute)
	expiredToken, _ := jwt.NewWithClaims(jwt.SigningMethodHS256, auth.Claims{
		UserID:    userID,
		SessionID: sessionID,
		RegisteredClaims: jwt.RegisteredClaims{
			IssuedAt:  jwt.NewNumericDate(past.Add(-15 * time.Minute)),
			ExpiresAt: jwt.NewNumericDate(past),
		},
	}).SignedString([]byte("test-access-secret-key-at-least-32-chars"))

	cases := []struct {
		name       string
		authHeader string
		dbRow      stubRow
		wantStatus int
		wantCode   string
		wantNext   bool
	}{
		{
			name:       "missing authorization header",
			authHeader: "",
			wantStatus: http.StatusUnauthorized,
			wantCode:   "UNAUTHORIZED",
		},
		{
			name:       "non-bearer scheme",
			authHeader: "Basic abc123",
			wantStatus: http.StatusUnauthorized,
			wantCode:   "UNAUTHORIZED",
		},
		{
			name:       "expired token",
			authHeader: "Bearer " + expiredToken,
			wantStatus: http.StatusUnauthorized,
			wantCode:   "TOKEN_EXPIRED",
		},
		{
			name:       "invalid token",
			authHeader: "Bearer invalid.jwt.token",
			wantStatus: http.StatusUnauthorized,
			wantCode:   "UNAUTHORIZED",
		},
		{
			name:       "revoked or inactive session in db",
			authHeader: "Bearer " + validToken,
			dbRow:      stubRow{val: false},
			wantStatus: http.StatusUnauthorized,
			wantCode:   "UNAUTHORIZED",
		},
		{
			name:       "db query error returns 500",
			authHeader: "Bearer " + validToken,
			dbRow:      stubRow{err: errors.New("connection refused")},
			wantStatus: http.StatusInternalServerError,
			wantCode:   "INTERNAL_ERROR",
		},
		{
			name:       "valid token and active session passes",
			authHeader: "bearer " + validToken,
			dbRow:      stubRow{val: true},
			wantStatus: http.StatusOK,
			wantNext:   true,
		},
	}

	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			store := &stubDBTX{
				fn: func(_ context.Context, _ string, _ ...any) pgx.Row {
					return tc.dbRow
				},
			}
			mw := auth.NewMiddleware(mgr, store)

			nextCalled := false
			handler := mw.RequireAuth(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
				nextCalled = true
				gotID, ok := auth.UserIDFromContext(r.Context())
				if !ok || gotID != userID {
					t.Errorf("UserIDFromContext = (%s, %v), want %s", gotID, ok, userID)
				}
				httpx.WriteJSON(w, http.StatusOK, map[string]string{"ok": "true"}, nil)
			}))

			req := httptest.NewRequest(http.MethodGet, "/api/v1/protected", nil)
			if tc.authHeader != "" {
				req.Header.Set("Authorization", tc.authHeader)
			}
			rec := httptest.NewRecorder()
			handler.ServeHTTP(rec, req)

			if rec.Code != tc.wantStatus {
				t.Errorf("status = %d, want %d (body=%s)", rec.Code, tc.wantStatus, rec.Body.String())
			}
			if nextCalled != tc.wantNext {
				t.Errorf("nextCalled = %v, want %v", nextCalled, tc.wantNext)
			}
			if tc.wantCode != "" {
				env := decodeEnvelope(t, rec)
				if env.Error == nil || env.Error.Code != tc.wantCode {
					t.Errorf("error code = %+v, want %q", env.Error, tc.wantCode)
				}
			}
		})
	}

	t.Run("AuthenticateToken satisfies realtime.TokenValidator", func(t *testing.T) {
		store := &stubDBTX{
			fn: func(_ context.Context, _ string, _ ...any) pgx.Row {
				return stubRow{val: true}
			},
		}
		mw := auth.NewMiddleware(mgr, store)

		var validator realtime.TokenValidator = mw.AuthenticateToken
		gotID, err := validator(context.Background(), validToken)
		if err != nil {
			t.Fatalf("validator returned unexpected error: %v", err)
		}
		if gotID != userID {
			t.Errorf("gotID = %s, want %s", gotID, userID)
		}

		if _, err := validator(context.Background(), expiredToken); !errors.Is(err, auth.ErrTokenExpired) {
			t.Errorf("expected ErrTokenExpired or wrapped AppError, got %v", err)
		}
	})
}

func TestAuthorizationMiddlewares(t *testing.T) {
	mgr := newTestManager(t, 15*time.Minute, 30*24*time.Hour)
	userID := uuid.New()
	claimsCtx := auth.WithClaims(context.Background(), &auth.Claims{
		UserID:    userID,
		SessionID: uuid.New(),
		Roles:     []string{"pasajero"},
	})

	t.Run("RequireRoles", func(t *testing.T) {
		cases := []struct {
			name       string
			ctx        context.Context
			roles      []string
			dbRow      stubRow
			wantStatus int
			wantCode   string
		}{
			{
				name:       "missing context claims",
				ctx:        context.Background(),
				roles:      []string{"admin"},
				wantStatus: http.StatusUnauthorized,
				wantCode:   "UNAUTHORIZED",
			},
			{
				name:       "empty roles fail closed",
				ctx:        claimsCtx,
				roles:      nil,
				wantStatus: http.StatusForbidden,
				wantCode:   "FORBIDDEN",
			},
			{
				name:       "user lacks required role",
				ctx:        claimsCtx,
				roles:      []string{"admin", "moderador"},
				dbRow:      stubRow{val: false},
				wantStatus: http.StatusForbidden,
				wantCode:   "FORBIDDEN",
			},
			{
				name:       "db failure returns 500",
				ctx:        claimsCtx,
				roles:      []string{"admin"},
				dbRow:      stubRow{err: errors.New("db down")},
				wantStatus: http.StatusInternalServerError,
				wantCode:   "INTERNAL_ERROR",
			},
			{
				name:       "user has matching active role",
				ctx:        claimsCtx,
				roles:      []string{"soporte", "admin"},
				dbRow:      stubRow{val: true},
				wantStatus: http.StatusOK,
			},
		}

		for _, tc := range cases {
			t.Run(tc.name, func(t *testing.T) {
				store := &stubDBTX{fn: func(_ context.Context, _ string, _ ...any) pgx.Row { return tc.dbRow }}
				mw := auth.NewMiddleware(mgr, store)
				h := mw.RequireRoles(tc.roles...)(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
					httpx.WriteJSON(w, http.StatusOK, map[string]bool{"ok": true}, nil)
				}))

				req := httptest.NewRequest(http.MethodGet, "/admin", nil).WithContext(tc.ctx)
				rec := httptest.NewRecorder()
				h.ServeHTTP(rec, req)

				if rec.Code != tc.wantStatus {
					t.Errorf("status = %d, want %d", rec.Code, tc.wantStatus)
				}
				if tc.wantCode != "" {
					env := decodeEnvelope(t, rec)
					if env.Error == nil || env.Error.Code != tc.wantCode {
						t.Errorf("error code = %+v, want %q", env.Error, tc.wantCode)
					}
				}
			})
		}
	})

	t.Run("RequireVerifiedIdentity", func(t *testing.T) {
		cases := []struct {
			name       string
			ctx        context.Context
			dbRow      stubRow
			wantStatus int
			wantCode   string
		}{
			{
				name:       "missing claims",
				ctx:        context.Background(),
				wantStatus: http.StatusUnauthorized,
				wantCode:   "UNAUTHORIZED",
			},
			{
				name:       "identity not verified",
				ctx:        claimsCtx,
				dbRow:      stubRow{val: false},
				wantStatus: http.StatusForbidden,
				wantCode:   "IDENTITY_NOT_VERIFIED",
			},
			{
				name:       "db error",
				ctx:        claimsCtx,
				dbRow:      stubRow{err: errors.New("timeout")},
				wantStatus: http.StatusInternalServerError,
				wantCode:   "INTERNAL_ERROR",
			},
			{
				name:       "identity verified",
				ctx:        claimsCtx,
				dbRow:      stubRow{val: true},
				wantStatus: http.StatusOK,
			},
		}

		for _, tc := range cases {
			t.Run(tc.name, func(t *testing.T) {
				store := &stubDBTX{fn: func(_ context.Context, _ string, _ ...any) pgx.Row { return tc.dbRow }}
				mw := auth.NewMiddleware(mgr, store)
				h := mw.RequireVerifiedIdentity(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
					httpx.WriteJSON(w, http.StatusOK, map[string]bool{"ok": true}, nil)
				}))

				req := httptest.NewRequest(http.MethodPost, "/bookings", nil).WithContext(tc.ctx)
				rec := httptest.NewRecorder()
				h.ServeHTTP(rec, req)

				if rec.Code != tc.wantStatus {
					t.Errorf("status = %d, want %d", rec.Code, tc.wantStatus)
				}
				if tc.wantCode != "" {
					env := decodeEnvelope(t, rec)
					if env.Error == nil || env.Error.Code != tc.wantCode {
						t.Errorf("error code = %+v, want %q", env.Error, tc.wantCode)
					}
				}
			})
		}
	})

	t.Run("RequireApprovedDriver", func(t *testing.T) {
		cases := []struct {
			name       string
			ctx        context.Context
			dbRow      stubRow
			wantStatus int
			wantCode   string
		}{
			{
				name:       "missing claims",
				ctx:        context.Background(),
				wantStatus: http.StatusUnauthorized,
				wantCode:   "UNAUTHORIZED",
			},
			{
				name:       "driver not approved",
				ctx:        claimsCtx,
				dbRow:      stubRow{val: false},
				wantStatus: http.StatusForbidden,
				wantCode:   "DRIVER_NOT_APPROVED",
			},
			{
				name:       "db error",
				ctx:        claimsCtx,
				dbRow:      stubRow{err: errors.New("timeout")},
				wantStatus: http.StatusInternalServerError,
				wantCode:   "INTERNAL_ERROR",
			},
			{
				name:       "driver approved",
				ctx:        claimsCtx,
				dbRow:      stubRow{val: true},
				wantStatus: http.StatusOK,
			},
		}

		for _, tc := range cases {
			t.Run(tc.name, func(t *testing.T) {
				store := &stubDBTX{fn: func(_ context.Context, _ string, _ ...any) pgx.Row { return tc.dbRow }}
				mw := auth.NewMiddleware(mgr, store)
				h := mw.RequireApprovedDriver(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
					httpx.WriteJSON(w, http.StatusOK, map[string]bool{"ok": true}, nil)
				}))

				req := httptest.NewRequest(http.MethodPost, "/trips", nil).WithContext(tc.ctx)
				rec := httptest.NewRecorder()
				h.ServeHTTP(rec, req)

				if rec.Code != tc.wantStatus {
					t.Errorf("status = %d, want %d", rec.Code, tc.wantStatus)
				}
				if tc.wantCode != "" {
					env := decodeEnvelope(t, rec)
					if env.Error == nil || env.Error.Code != tc.wantCode {
						t.Errorf("error code = %+v, want %q", env.Error, tc.wantCode)
					}
				}
			})
		}
	})

	t.Run("CheckActiveRestrictions", func(t *testing.T) {
		cases := []struct {
			name       string
			ctx        context.Context
			scopes     []string
			dbRow      stubRow
			wantStatus int
			wantCode   string
			wantScopes []string
		}{
			{
				name:       "missing claims",
				ctx:        context.Background(),
				scopes:     []string{"pasajero"},
				wantStatus: http.StatusUnauthorized,
				wantCode:   "UNAUTHORIZED",
			},
			{
				name:       "restricted returns 403 USER_RESTRICTED and includes total automatically",
				ctx:        claimsCtx,
				scopes:     []string{"pasajero"},
				dbRow:      stubRow{val: true},
				wantStatus: http.StatusForbidden,
				wantCode:   "USER_RESTRICTED",
				wantScopes: []string{"pasajero", "total"},
			},
			{
				name:       "no scopes still checks total",
				ctx:        claimsCtx,
				scopes:     nil,
				dbRow:      stubRow{val: false},
				wantStatus: http.StatusOK,
				wantScopes: []string{"total"},
			},
			{
				name:       "db error",
				ctx:        claimsCtx,
				scopes:     []string{"conductor"},
				dbRow:      stubRow{err: errors.New("db error")},
				wantStatus: http.StatusInternalServerError,
				wantCode:   "INTERNAL_ERROR",
				wantScopes: []string{"conductor", "total"},
			},
		}

		for _, tc := range cases {
			t.Run(tc.name, func(t *testing.T) {
				store := &stubDBTX{fn: func(_ context.Context, _ string, _ ...any) pgx.Row { return tc.dbRow }}
				mw := auth.NewMiddleware(mgr, store)
				h := mw.CheckActiveRestrictions(tc.scopes...)(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
					httpx.WriteJSON(w, http.StatusOK, map[string]bool{"ok": true}, nil)
				}))

				req := httptest.NewRequest(http.MethodPost, "/action", nil).WithContext(tc.ctx)
				rec := httptest.NewRecorder()
				h.ServeHTTP(rec, req)

				if rec.Code != tc.wantStatus {
					t.Errorf("status = %d, want %d", rec.Code, tc.wantStatus)
				}
				if tc.wantCode != "" {
					env := decodeEnvelope(t, rec)
					if env.Error == nil || env.Error.Code != tc.wantCode {
						t.Errorf("error code = %+v, want %q", env.Error, tc.wantCode)
					}
				}
				if tc.wantScopes != nil {
					if len(store.calls) != 1 || len(store.calls[0].args) != 2 {
						t.Fatalf("expected 1 query call with 2 args, got %+v", store.calls)
					}
					if !strings.Contains(store.calls[0].sql, "restriccion_preventiva") {
						t.Errorf("expected query against restriccion_preventiva, got %q", store.calls[0].sql)
					}
					gotScopes, ok := store.calls[0].args[1].([]string)
					if !ok {
						t.Fatalf("expected []string arg[1], got %T", store.calls[0].args[1])
					}
					for _, wantScope := range tc.wantScopes {
						if !slices.Contains(gotScopes, wantScope) {
							t.Errorf("scopes %v missing %q", gotScopes, wantScope)
						}
					}
				}
			})
		}
	})
}
