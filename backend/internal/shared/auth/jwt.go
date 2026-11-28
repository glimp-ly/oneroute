package auth

import (
	"context"
	"crypto/rand"
	"crypto/sha256"
	"encoding/hex"
	"errors"
	"fmt"
	"slices"
	"strings"
	"time"

	"github.com/golang-jwt/jwt/v5"
	"github.com/google/uuid"
)

const refreshTokenByteLen = 32

var (
	// ErrTokenExpired indicates that a JWT access token has passed its expiration time.
	ErrTokenExpired = errors.New("auth: token expired")

	// ErrInvalidToken indicates that a JWT access token is malformed, untrusted, or missing required claims.
	ErrInvalidToken = errors.New("auth: invalid token")
)

type claimsCtxKey struct{}

// Claims represents the authenticated JWT access token payload.
type Claims struct {
	UserID           uuid.UUID `json:"sub"`
	SessionID        uuid.UUID `json:"sid"`
	Roles            []string  `json:"roles"`
	IdentityVerified bool      `json:"id_ver"`
	DriverApproved   bool      `json:"drv_ok"`
	jwt.RegisteredClaims
}

// Manager issues and verifies HS256 JWT access tokens and cryptographic refresh tokens.
type Manager struct {
	accessSecret  []byte
	refreshSecret []byte
	accessTTL     time.Duration
	refreshTTL    time.Duration
}

// NewManager creates a token Manager after validating secrets and positive token lifetimes.
func NewManager(accessSecret, refreshSecret string, accessTTL, refreshTTL time.Duration) (*Manager, error) {
	if strings.TrimSpace(accessSecret) == "" {
		return nil, errors.New("auth: accessSecret must not be empty")
	}
	if strings.TrimSpace(refreshSecret) == "" {
		return nil, errors.New("auth: refreshSecret must not be empty")
	}
	if accessTTL <= 0 {
		return nil, errors.New("auth: accessTTL must be positive")
	}
	if refreshTTL <= 0 {
		return nil, errors.New("auth: refreshTTL must be positive")
	}

	return &Manager{
		accessSecret:  []byte(accessSecret),
		refreshSecret: []byte(refreshSecret),
		accessTTL:     accessTTL,
		refreshTTL:    refreshTTL,
	}, nil
}

// AccessTTL returns the configured access token lifetime.
func (m *Manager) AccessTTL() time.Duration {
	return m.accessTTL
}

// RefreshTTL returns the configured refresh token lifetime.
func (m *Manager) RefreshTTL() time.Duration {
	return m.refreshTTL
}

// GenerateAccessToken signs a new HS256 JWT access token for the given user and session.
func (m *Manager) GenerateAccessToken(
	userID, sessionID uuid.UUID,
	roles []string,
	identityVerified, driverApproved bool,
) (string, error) {
	if m == nil {
		return "", errors.New("auth: manager is nil")
	}
	if userID == uuid.Nil {
		return "", errors.New("auth: userID must not be nil")
	}
	if sessionID == uuid.Nil {
		return "", errors.New("auth: sessionID must not be nil")
	}

	copiedRoles := make([]string, len(roles))
	copy(copiedRoles, roles)

	now := time.Now().UTC()
	claims := Claims{
		UserID:           userID,
		SessionID:        sessionID,
		Roles:            copiedRoles,
		IdentityVerified: identityVerified,
		DriverApproved:   driverApproved,
		RegisteredClaims: jwt.RegisteredClaims{
			ID:        uuid.NewString(),
			IssuedAt:  jwt.NewNumericDate(now),
			NotBefore: jwt.NewNumericDate(now),
			ExpiresAt: jwt.NewNumericDate(now.Add(m.accessTTL)),
		},
	}

	token := jwt.NewWithClaims(jwt.SigningMethodHS256, claims)
	signed, err := token.SignedString(m.accessSecret)
	if err != nil {
		return "", fmt.Errorf("auth: sign access token: %w", err)
	}
	return signed, nil
}

// ParseAccessToken verifies an HS256 JWT access token and returns its Claims.
// Expired tokens return ErrTokenExpired; malformed or untrusted tokens return ErrInvalidToken.
func (m *Manager) ParseAccessToken(tokenString string) (*Claims, error) {
	if m == nil {
		return nil, ErrInvalidToken
	}
	trimmed := strings.TrimSpace(tokenString)
	if trimmed == "" {
		return nil, ErrInvalidToken
	}

	claims := &Claims{}
	token, err := jwt.ParseWithClaims(
		trimmed,
		claims,
		func(_ *jwt.Token) (any, error) {
			return m.accessSecret, nil
		},
		jwt.WithValidMethods([]string{jwt.SigningMethodHS256.Alg()}),
	)
	if err != nil {
		if errors.Is(err, jwt.ErrTokenExpired) {
			return nil, fmt.Errorf("%w: %v", ErrTokenExpired, err)
		}
		return nil, fmt.Errorf("%w: %v", ErrInvalidToken, err)
	}
	if token == nil || !token.Valid || claims.UserID == uuid.Nil || claims.SessionID == uuid.Nil {
		return nil, ErrInvalidToken
	}

	claims.Roles = slices.Clone(claims.Roles)
	return claims, nil
}

// GenerateRefreshToken produces a 32-byte cryptographically random hex token
// alongside its lowercase SHA-256 hex digest for storage in usuario_sesion.
func (m *Manager) GenerateRefreshToken() (rawHex string, sha256Hex string, err error) {
	buf := make([]byte, refreshTokenByteLen)
	if _, err := rand.Read(buf); err != nil {
		return "", "", fmt.Errorf("auth: generate refresh token: %w", err)
	}
	rawHex = hex.EncodeToString(buf)
	sha256Hex = m.HashRefreshToken(rawHex)
	return rawHex, sha256Hex, nil
}

// HashRefreshToken computes the lowercase SHA-256 hex digest of a raw refresh token.
func (m *Manager) HashRefreshToken(rawHex string) string {
	sum := sha256.Sum256([]byte(rawHex))
	return hex.EncodeToString(sum[:])
}

// WithClaims returns a copy of ctx carrying the authenticated JWT Claims.
func WithClaims(ctx context.Context, claims *Claims) context.Context {
	if ctx == nil {
		ctx = context.Background()
	}
	return context.WithValue(ctx, claimsCtxKey{}, claims)
}

// ClaimsFromContext retrieves authenticated Claims stored in ctx, if present.
func ClaimsFromContext(ctx context.Context) (*Claims, bool) {
	if ctx == nil {
		return nil, false
	}
	claims, ok := ctx.Value(claimsCtxKey{}).(*Claims)
	if !ok || claims == nil {
		return nil, false
	}
	return claims, true
}

// UserIDFromContext retrieves the authenticated user UUID from ctx, if present.
func UserIDFromContext(ctx context.Context) (uuid.UUID, bool) {
	claims, ok := ClaimsFromContext(ctx)
	if !ok || claims.UserID == uuid.Nil {
		return uuid.Nil, false
	}
	return claims.UserID, true
}
