package auth

import (
	"errors"
	"fmt"

	"golang.org/x/crypto/bcrypt"
)

// DefaultBcryptCost matches PostgreSQL pgcrypto gen_salt('bf', 10) used in seed_dev.sql.
const DefaultBcryptCost = 10

// HashPassword hashes a plaintext password or OTP using bcrypt with DefaultBcryptCost.
func HashPassword(plain string) (string, error) {
	if plain == "" {
		return "", errors.New("auth: password must not be empty")
	}
	hashed, err := bcrypt.GenerateFromPassword([]byte(plain), DefaultBcryptCost)
	if err != nil {
		return "", fmt.Errorf("auth: hash password: %w", err)
	}
	return string(hashed), nil
}

// VerifyPassword reports whether plain matches the bcrypt hash in constant time.
func VerifyPassword(hash, plain string) bool {
	if hash == "" || plain == "" {
		return false
	}
	return bcrypt.CompareHashAndPassword([]byte(hash), []byte(plain)) == nil
}
