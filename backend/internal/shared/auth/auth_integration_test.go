//go:build integration

package auth_test

import (
	"context"
	"net/http"
	"net/http/httptest"
	"os"
	"testing"
	"time"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5"

	"github.com/glimp-ly/oneroute/backend/internal/shared/auth"
	"github.com/glimp-ly/oneroute/backend/internal/shared/db"
	"github.com/glimp-ly/oneroute/backend/internal/shared/httpx"
)

const (
	seedAdminID      = "10000000-0000-0000-0000-000000000001"
	seedDriverID     = "10000000-0000-0000-0000-000000000002"
	seedPassenger1ID = "10000000-0000-0000-0000-000000000003"
	seedPassenger2ID = "10000000-0000-0000-0000-000000000004"
	seedNewUserID    = "10000000-0000-0000-0000-000000000005"
	seedTripID       = "32000000-0000-0000-0000-000000000001"
	seedReservaID    = "42000000-0000-0000-0000-000000000001"
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

func withRolledBackTx(t *testing.T, fn func(ctx context.Context, tx pgx.Tx)) {
	t.Helper()
	ctx, cancel := context.WithTimeout(context.Background(), 15*time.Second)
	defer cancel()

	pool, err := db.NewPool(ctx, testDatabaseURL(), "America/Lima")
	if err != nil {
		t.Fatalf("NewPool failed: %v", err)
	}
	defer pool.Close()

	tx, err := pool.BeginTx(ctx, pgx.TxOptions{IsoLevel: pgx.ReadCommitted})
	if err != nil {
		t.Fatalf("BeginTx failed: %v", err)
	}
	defer func() {
		rbCtx, rbCancel := context.WithTimeout(context.Background(), 5*time.Second)
		defer rbCancel()
		_ = tx.Rollback(rbCtx)
	}()

	fn(ctx, tx)
}

func TestSeedDevPasswordHashesIntegration(t *testing.T) {
	withRolledBackTx(t, func(ctx context.Context, tx pgx.Tx) {
		emails := []string{
			"admin@rutacompartida.pe",
			"conductor@rutacompartida.pe",
			"pasajero1@rutacompartida.pe",
			"pasajero2@rutacompartida.pe",
			"nuevo@rutacompartida.pe",
		}
		for _, email := range emails {
			var hash string
			if err := tx.QueryRow(ctx, "SELECT password_hash FROM usuario WHERE correo = $1", email).Scan(&hash); err != nil {
				t.Fatalf("query password_hash for %s failed: %v", email, err)
			}
			if !auth.VerifyPassword(hash, "Password123!") {
				t.Errorf("VerifyPassword failed for seeded user %s", email)
			}
			if auth.VerifyPassword(hash, "Incorrect999!") {
				t.Errorf("VerifyPassword unexpectedly matched wrong password for %s", email)
			}
		}
	})
}

func TestAuthMiddlewareGuardsIntegration(t *testing.T) {
	withRolledBackTx(t, func(ctx context.Context, tx pgx.Tx) {
		mgr, err := auth.NewManager(
			"integration-access-secret-key-32-chars",
			"integration-refresh-secret-key-32-chars",
			15*time.Minute,
			30*24*time.Hour,
		)
		if err != nil {
			t.Fatalf("NewManager failed: %v", err)
		}

		mw := auth.NewMiddleware(mgr, tx)
		adminID := uuid.MustParse(seedAdminID)
		driverID := uuid.MustParse(seedDriverID)
		passenger2ID := uuid.MustParse(seedPassenger2ID)
		newUserID := uuid.MustParse(seedNewUserID)

		// Create active, revoked, and expired sessions inside the rolled-back tx.
		activeSessionID := uuid.New()
		revokedSessionID := uuid.New()
		expiredSessionID := uuid.New()

		_, rawHashActive, _ := mgr.GenerateRefreshToken()
		_, rawHashRevoked, _ := mgr.GenerateRefreshToken()
		_, rawHashExpired, _ := mgr.GenerateRefreshToken()

		_, err = tx.Exec(ctx, `
			INSERT INTO usuario_sesion (id, usuario_id, refresh_token_hash, expira_at, revocado_at, created_at)
			VALUES
				($1, $2, $3, NOW() + INTERVAL '1 day', NULL, NOW()),
				($4, $2, $5, NOW() + INTERVAL '1 day', NOW(), NOW() - INTERVAL '1 hour'),
				($6, $2, $7, NOW() - INTERVAL '1 minute', NULL, NOW() - INTERVAL '2 days')`,
			activeSessionID, driverID, rawHashActive,
			revokedSessionID, rawHashRevoked,
			expiredSessionID, rawHashExpired,
		)
		if err != nil {
			t.Fatalf("insert test sessions failed: %v", err)
		}

		activeTok, err := mgr.GenerateAccessToken(driverID, activeSessionID, []string{"pasajero", "conductor"}, true, true)
		if err != nil {
			t.Fatalf("GenerateAccessToken active failed: %v", err)
		}
		revokedTok, _ := mgr.GenerateAccessToken(driverID, revokedSessionID, []string{"pasajero"}, true, true)
		expiredSessTok, _ := mgr.GenerateAccessToken(driverID, expiredSessionID, []string{"pasajero"}, true, true)

		t.Run("RequireAuth and AuthenticateToken against live sessions", func(t *testing.T) {
			okHandler := mw.RequireAuth(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
				uid, ok := auth.UserIDFromContext(r.Context())
				if !ok || uid != driverID {
					t.Errorf("UserIDFromContext = (%s, %v), want %s", uid, ok, driverID)
				}
				httpx.WriteJSON(w, http.StatusOK, map[string]string{"uid": uid.String()}, nil)
			}))

			// Active session passes.
			req := httptest.NewRequest(http.MethodGet, "/me", nil).WithContext(ctx)
			req.Header.Set("Authorization", "Bearer "+activeTok)
			rec := httptest.NewRecorder()
			okHandler.ServeHTTP(rec, req)
			if rec.Code != http.StatusOK {
				t.Fatalf("active session status = %d, want 200 (body=%s)", rec.Code, rec.Body.String())
			}

			// AuthenticateToken returns driverID.
			gotID, err := mw.AuthenticateToken(ctx, activeTok)
			if err != nil || gotID != driverID {
				t.Fatalf("AuthenticateToken = (%s, %v), want (%s, nil)", gotID, err, driverID)
			}

			// Revoked session is rejected with 401.
			reqRev := httptest.NewRequest(http.MethodGet, "/me", nil).WithContext(ctx)
			reqRev.Header.Set("Authorization", "Bearer "+revokedTok)
			recRev := httptest.NewRecorder()
			okHandler.ServeHTTP(recRev, reqRev)
			if recRev.Code != http.StatusUnauthorized {
				t.Fatalf("revoked session status = %d, want 401", recRev.Code)
			}

			// Expired session is rejected with 401.
			reqExp := httptest.NewRequest(http.MethodGet, "/me", nil).WithContext(ctx)
			reqExp.Header.Set("Authorization", "Bearer "+expiredSessTok)
			recExp := httptest.NewRecorder()
			okHandler.ServeHTTP(recExp, reqExp)
			if recExp.Code != http.StatusUnauthorized {
				t.Fatalf("expired session status = %d, want 401", recExp.Code)
			}
		})

		t.Run("RequireRoles checks usuario_rol and rol", func(t *testing.T) {
			adminSessionID := uuid.New()
			_, adminHash, _ := mgr.GenerateRefreshToken()
			if _, err := tx.Exec(ctx, `
				INSERT INTO usuario_sesion (id, usuario_id, refresh_token_hash, expira_at)
				VALUES ($1, $2, $3, NOW() + INTERVAL '1 day')`,
				adminSessionID, adminID, adminHash,
			); err != nil {
				t.Fatalf("insert admin session failed: %v", err)
			}
			adminTok, _ := mgr.GenerateAccessToken(adminID, adminSessionID, []string{"admin"}, true, false)

			guarded := mw.RequireAuth(mw.RequireRoles("admin", "moderador")(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
				httpx.WriteJSON(w, http.StatusOK, map[string]bool{"ok": true}, nil)
			})))

			// Driver lacks admin/moderador -> 403 FORBIDDEN.
			reqDriver := httptest.NewRequest(http.MethodGet, "/admin", nil).WithContext(ctx)
			reqDriver.Header.Set("Authorization", "Bearer "+activeTok)
			recDriver := httptest.NewRecorder()
			guarded.ServeHTTP(recDriver, reqDriver)
			if recDriver.Code != http.StatusForbidden {
				t.Fatalf("driver role check status = %d, want 403", recDriver.Code)
			}

			// Admin passes -> 200 OK.
			reqAdmin := httptest.NewRequest(http.MethodGet, "/admin", nil).WithContext(ctx)
			reqAdmin.Header.Set("Authorization", "Bearer "+adminTok)
			recAdmin := httptest.NewRecorder()
			guarded.ServeHTTP(recAdmin, reqAdmin)
			if recAdmin.Code != http.StatusOK {
				t.Fatalf("admin role check status = %d, want 200", recAdmin.Code)
			}
		})

		t.Run("RequireVerifiedIdentity and RequireApprovedDriver", func(t *testing.T) {
			newSessionID := uuid.New()
			_, newHash, _ := mgr.GenerateRefreshToken()
			if _, err := tx.Exec(ctx, `
				INSERT INTO usuario_sesion (id, usuario_id, refresh_token_hash, expira_at)
				VALUES ($1, $2, $3, NOW() + INTERVAL '1 day')`,
				newSessionID, newUserID, newHash,
			); err != nil {
				t.Fatalf("insert new user session failed: %v", err)
			}
			newUserTok, _ := mgr.GenerateAccessToken(newUserID, newSessionID, []string{"pasajero"}, false, false)

			verifiedGuard := mw.RequireAuth(mw.RequireVerifiedIdentity(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
				httpx.WriteJSON(w, http.StatusOK, map[string]bool{"ok": true}, nil)
			})))

			// Unverified user -> 403 IDENTITY_NOT_VERIFIED.
			reqNew := httptest.NewRequest(http.MethodPost, "/bookings", nil).WithContext(ctx)
			reqNew.Header.Set("Authorization", "Bearer "+newUserTok)
			recNew := httptest.NewRecorder()
			verifiedGuard.ServeHTTP(recNew, reqNew)
			if recNew.Code != http.StatusForbidden {
				t.Fatalf("unverified status = %d, want 403", recNew.Code)
			}
			if env := decodeEnvelope(t, recNew); env.Error == nil || env.Error.Code != "IDENTITY_NOT_VERIFIED" {
				t.Fatalf("unverified error = %+v, want IDENTITY_NOT_VERIFIED", env.Error)
			}

			// Verified driver -> 200 OK.
			reqDrv := httptest.NewRequest(http.MethodPost, "/bookings", nil).WithContext(ctx)
			reqDrv.Header.Set("Authorization", "Bearer "+activeTok)
			recDrv := httptest.NewRecorder()
			verifiedGuard.ServeHTTP(recDrv, reqDrv)
			if recDrv.Code != http.StatusOK {
				t.Fatalf("verified driver status = %d, want 200", recDrv.Code)
			}

			driverGuard := mw.RequireAuth(mw.RequireApprovedDriver(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
				httpx.WriteJSON(w, http.StatusOK, map[string]bool{"ok": true}, nil)
			})))

			// New user has no approved perfil_conductor -> 403 DRIVER_NOT_APPROVED.
			reqNewDrv := httptest.NewRequest(http.MethodPost, "/trips", nil).WithContext(ctx)
			reqNewDrv.Header.Set("Authorization", "Bearer "+newUserTok)
			recNewDrv := httptest.NewRecorder()
			driverGuard.ServeHTTP(recNewDrv, reqNewDrv)
			if recNewDrv.Code != http.StatusForbidden {
				t.Fatalf("non-driver status = %d, want 403", recNewDrv.Code)
			}
			if env := decodeEnvelope(t, recNewDrv); env.Error == nil || env.Error.Code != "DRIVER_NOT_APPROVED" {
				t.Fatalf("non-driver error = %+v, want DRIVER_NOT_APPROVED", env.Error)
			}

			// Seeded approved driver -> 200 OK.
			reqApprovedDrv := httptest.NewRequest(http.MethodPost, "/trips", nil).WithContext(ctx)
			reqApprovedDrv.Header.Set("Authorization", "Bearer "+activeTok)
			recApprovedDrv := httptest.NewRecorder()
			driverGuard.ServeHTTP(recApprovedDrv, reqApprovedDrv)
			if recApprovedDrv.Code != http.StatusOK {
				t.Fatalf("approved driver status = %d, want 200", recApprovedDrv.Code)
			}
		})

		t.Run("CheckActiveRestrictions checks scope and total", func(t *testing.T) {
			p2SessionID := uuid.New()
			_, p2Hash, _ := mgr.GenerateRefreshToken()
			if _, err := tx.Exec(ctx, `
				INSERT INTO usuario_sesion (id, usuario_id, refresh_token_hash, expira_at)
				VALUES ($1, $2, $3, NOW() + INTERVAL '1 day')`,
				p2SessionID, passenger2ID, p2Hash,
			); err != nil {
				t.Fatalf("insert passenger2 session failed: %v", err)
			}
			p2Tok, _ := mgr.GenerateAccessToken(passenger2ID, p2SessionID, []string{"pasajero"}, true, false)

			var catID uuid.UUID
			if err := tx.QueryRow(ctx, "SELECT id FROM categoria_reporte LIMIT 1").Scan(&catID); err != nil {
				t.Fatalf("select categoria_reporte failed: %v", err)
			}

			reporteID := uuid.New()
			investigacionID := uuid.New()
			if _, err := tx.Exec(ctx, `
				INSERT INTO reporte (id, viaje_id, reserva_id, reportante_id, reportado_id, categoria_principal_id, descripcion)
				VALUES ($1, $2, $3, $4, $5, $6, 'Integration test report')`,
				reporteID, uuid.MustParse(seedTripID), uuid.MustParse(seedReservaID),
				uuid.MustParse(seedPassenger1ID), passenger2ID, catID,
			); err != nil {
				t.Fatalf("insert reporte failed: %v", err)
			}

			if _, err := tx.Exec(ctx, `
				INSERT INTO investigacion (id, reporte_id, responsable_id, estado)
				VALUES ($1, $2, $3, 'en_proceso')`,
				investigacionID, reporteID, adminID,
			); err != nil {
				t.Fatalf("insert investigacion failed: %v", err)
			}

			// Insert an active 'total' restriction on passenger2.
			if _, err := tx.Exec(ctx, `
				INSERT INTO restriccion_preventiva (usuario_id, investigacion_id, alcance, gravedad, motivo, autorizada_por, termina_at, activa)
				VALUES ($1, $2, 'total', 'alta', 'Bloqueo preventivo test', $3, NOW() + INTERVAL '1 hour', TRUE)`,
				passenger2ID, investigacionID, adminID,
			); err != nil {
				t.Fatalf("insert restriccion_preventiva failed: %v", err)
			}

			restrictedGuard := mw.RequireAuth(mw.CheckActiveRestrictions("pasajero")(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
				httpx.WriteJSON(w, http.StatusOK, map[string]bool{"ok": true}, nil)
			})))

			reqRestricted := httptest.NewRequest(http.MethodPost, "/bookings", nil).WithContext(ctx)
			reqRestricted.Header.Set("Authorization", "Bearer "+p2Tok)
			recRestricted := httptest.NewRecorder()
			restrictedGuard.ServeHTTP(recRestricted, reqRestricted)
			if recRestricted.Code != http.StatusForbidden {
				t.Fatalf("restricted status = %d, want 403 (body=%s)", recRestricted.Code, recRestricted.Body.String())
			}
			if env := decodeEnvelope(t, recRestricted); env.Error == nil || env.Error.Code != "USER_RESTRICTED" {
				t.Fatalf("restricted error = %+v, want USER_RESTRICTED", env.Error)
			}

			// Unrestricted driver passes.
			reqClear := httptest.NewRequest(http.MethodPost, "/bookings", nil).WithContext(ctx)
			reqClear.Header.Set("Authorization", "Bearer "+activeTok)
			recClear := httptest.NewRecorder()
			restrictedGuard.ServeHTTP(recClear, reqClear)
			if recClear.Code != http.StatusOK {
				t.Fatalf("unrestricted status = %d, want 200", recClear.Code)
			}
		})
	})
}
