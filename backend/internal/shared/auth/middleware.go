package auth

import (
	"context"
	"errors"
	"net/http"
	"slices"
	"strings"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5"

	"github.com/glimp-ly/oneroute/backend/internal/shared/db"
	"github.com/glimp-ly/oneroute/backend/internal/shared/httpx"
)

const (
	queryActiveSession = `
		SELECT EXISTS (
			SELECT 1
			FROM usuario u
			INNER JOIN usuario_sesion s ON s.usuario_id = u.id
			WHERE u.id = $1
			  AND s.id = $2
			  AND u.estado = 'activo'
			  AND s.revocado_at IS NULL
			  AND s.expira_at > NOW()
		)`

	queryHasAnyActiveRole = `
		SELECT EXISTS (
			SELECT 1
			FROM usuario_rol ur
			INNER JOIN rol r ON r.id = ur.rol_id
			WHERE ur.usuario_id = $1
			  AND ur.activo = TRUE
			  AND r.activo = TRUE
			  AND r.codigo = ANY($2)
		)`

	queryIdentityVerified = `
		SELECT EXISTS (
			SELECT 1
			FROM usuario
			WHERE id = $1
			  AND estado = 'activo'
			  AND identidad_verificada = TRUE
		)`

	queryDriverApproved = `
		SELECT EXISTS (
			SELECT 1
			FROM perfil_conductor
			WHERE usuario_id = $1
			  AND estado = 'aprobado'
		)`

	queryActiveRestriction = `
		SELECT EXISTS (
			SELECT 1
			FROM restriccion_preventiva
			WHERE usuario_id = $1
			  AND activa = TRUE
			  AND (termina_at IS NULL OR termina_at > NOW())
			  AND alcance = ANY($2)
		)`
)

// AuthStore defines the database query contract required by Middleware.
// It is satisfied by *pgxpool.Pool, pgx.Tx, and db.DBTX.
type AuthStore = db.DBTX

// Middleware enforces JWT authentication, session validity, and database-backed
// role, identity, driver, and preventive restriction guards.
type Middleware struct {
	manager *Manager
	store   db.DBTX
}

// NewMiddleware constructs an authentication and authorization Middleware.
func NewMiddleware(manager *Manager, store db.DBTX) *Middleware {
	return &Middleware{
		manager: manager,
		store:   store,
	}
}

// AuthenticateToken validates a raw JWT access token (or Bearer token), confirms
// the user is active and the session is not revoked or expired in PostgreSQL,
// and returns the authenticated user UUID. It satisfies realtime.TokenValidator.
func (m *Middleware) AuthenticateToken(ctx context.Context, token string) (uuid.UUID, error) {
	trimmed := strings.TrimSpace(token)
	if len(trimmed) > 7 && strings.EqualFold(trimmed[:7], "Bearer ") {
		trimmed = strings.TrimSpace(trimmed[7:])
	}
	claims, err := m.authenticateToken(ctx, trimmed)
	if err != nil {
		return uuid.Nil, err
	}
	return claims.UserID, nil
}

// RequireAuth extracts the Bearer access token, validates JWT signature and expiry,
// verifies active user/session state in PostgreSQL, and attaches Claims to context.
func (m *Middleware) RequireAuth(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		token, ok := extractBearerToken(r.Header.Get("Authorization"))
		if !ok {
			httpx.WriteError(w, httpx.Unauthorized("Token de autorizacion requerido."))
			return
		}

		claims, err := m.authenticateToken(r.Context(), token)
		if err != nil {
			httpx.WriteError(w, err)
			return
		}

		next.ServeHTTP(w, r.WithContext(WithClaims(r.Context(), claims)))
	})
}

// RequireRoles verifies in PostgreSQL that the authenticated user holds at least
// one active role among roles.
func (m *Middleware) RequireRoles(roles ...string) func(http.Handler) http.Handler {
	normalized := normalizeNonEmpty(roles)
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			claims, ok := m.requireClaims(w, r)
			if !ok {
				return
			}
			if len(normalized) == 0 {
				httpx.WriteError(w, httpx.Forbidden("No tienes permisos para realizar esta accion."))
				return
			}

			allowed, err := m.queryExists(r.Context(), queryHasAnyActiveRole, claims.UserID, normalized)
			if err != nil {
				httpx.WriteError(w, httpx.Internal(err))
				return
			}
			if !allowed {
				httpx.WriteError(w, httpx.Forbidden("No tienes el rol requerido para realizar esta accion."))
				return
			}

			next.ServeHTTP(w, r)
		})
	}
}

// RequireVerifiedIdentity verifies in PostgreSQL that usuario.identidad_verificada is TRUE.
func (m *Middleware) RequireVerifiedIdentity(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		claims, ok := m.requireClaims(w, r)
		if !ok {
			return
		}

		verified, err := m.queryExists(r.Context(), queryIdentityVerified, claims.UserID)
		if err != nil {
			httpx.WriteError(w, httpx.Internal(err))
			return
		}
		if !verified {
			httpx.WriteError(w, httpx.IdentityNotVerified(""))
			return
		}

		next.ServeHTTP(w, r)
	})
}

// RequireApprovedDriver verifies in PostgreSQL that perfil_conductor.estado is 'aprobado'.
func (m *Middleware) RequireApprovedDriver(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		claims, ok := m.requireClaims(w, r)
		if !ok {
			return
		}

		approved, err := m.queryExists(r.Context(), queryDriverApproved, claims.UserID)
		if err != nil {
			httpx.WriteError(w, httpx.Internal(err))
			return
		}
		if !approved {
			httpx.WriteError(w, httpx.DriverNotApproved(""))
			return
		}

		next.ServeHTTP(w, r)
	})
}

// CheckActiveRestrictions blocks requests when the user has an active preventive
// restriction in PostgreSQL matching any of scopes or 'total'.
func (m *Middleware) CheckActiveRestrictions(scopes ...string) func(http.Handler) http.Handler {
	effectiveScopes := normalizeScopesWithTotal(scopes)
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			claims, ok := m.requireClaims(w, r)
			if !ok {
				return
			}

			restricted, err := m.queryExists(r.Context(), queryActiveRestriction, claims.UserID, effectiveScopes)
			if err != nil {
				httpx.WriteError(w, httpx.Internal(err))
				return
			}
			if restricted {
				httpx.WriteError(w, httpx.UserRestricted(""))
				return
			}

			next.ServeHTTP(w, r)
		})
	}
}

func (m *Middleware) authenticateToken(ctx context.Context, token string) (*Claims, error) {
	if m == nil || m.manager == nil || m.store == nil {
		return nil, httpx.Internal(errors.New("auth: middleware is not initialized"))
	}
	if strings.TrimSpace(token) == "" {
		return nil, httpx.Unauthorized("Token de autorizacion requerido.").WithCause(ErrInvalidToken)
	}

	claims, err := m.manager.ParseAccessToken(token)
	if err != nil {
		if errors.Is(err, ErrTokenExpired) {
			return nil, httpx.TokenExpired("").WithCause(err)
		}
		return nil, httpx.Unauthorized("Token de acceso invalido.").WithCause(err)
	}

	active, err := m.queryExists(ctx, queryActiveSession, claims.UserID, claims.SessionID)
	if err != nil {
		return nil, httpx.Internal(err)
	}
	if !active {
		return nil, httpx.Unauthorized("La sesion es invalida o el usuario no esta activo.").WithCause(ErrInvalidToken)
	}

	return claims, nil
}

func (m *Middleware) requireClaims(w http.ResponseWriter, r *http.Request) (*Claims, bool) {
	if m == nil || m.store == nil {
		httpx.WriteError(w, httpx.Internal(errors.New("auth: middleware store is nil")))
		return nil, false
	}
	claims, ok := ClaimsFromContext(r.Context())
	if !ok || claims.UserID == uuid.Nil {
		httpx.WriteError(w, httpx.Unauthorized("Autenticacion requerida."))
		return nil, false
	}
	return claims, true
}

func (m *Middleware) queryExists(ctx context.Context, query string, args ...any) (bool, error) {
	var exists bool
	err := m.store.QueryRow(ctx, query, args...).Scan(&exists)
	if err != nil {
		if errors.Is(err, pgx.ErrNoRows) {
			return false, nil
		}
		return false, err
	}
	return exists, nil
}

func extractBearerToken(header string) (string, bool) {
	trimmed := strings.TrimSpace(header)
	if len(trimmed) <= 7 || !strings.EqualFold(trimmed[:7], "Bearer ") {
		return "", false
	}
	token := strings.TrimSpace(trimmed[7:])
	if token == "" {
		return "", false
	}
	return token, true
}

func normalizeNonEmpty(items []string) []string {
	out := make([]string, 0, len(items))
	for _, item := range items {
		v := strings.TrimSpace(item)
		if v != "" && !slices.Contains(out, v) {
			out = append(out, v)
		}
	}
	return out
}

func normalizeScopesWithTotal(scopes []string) []string {
	out := normalizeNonEmpty(scopes)
	if !slices.Contains(out, "total") {
		out = append(out, "total")
	}
	return out
}
