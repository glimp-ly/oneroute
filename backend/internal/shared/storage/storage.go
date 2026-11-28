package storage

import (
	"context"
	"errors"
	"io"
	"strings"
	"time"
)

// DefaultPresignedTTL is used when a caller does not specify a positive TTL.
const DefaultPresignedTTL = 5 * time.Minute

var (
	// ErrInvalidConfig indicates missing or malformed storage configuration.
	ErrInvalidConfig = errors.New("storage: invalid configuration")
	// ErrInvalidBucket indicates an empty or invalid bucket name.
	ErrInvalidBucket = errors.New("storage: bucket name is required")
	// ErrInvalidKey indicates an empty or unsafe object key.
	ErrInvalidKey = errors.New("storage: object key is invalid or empty")
	// ErrNilReader indicates a nil io.Reader was passed to an upload method.
	ErrNilReader = errors.New("storage: reader must not be nil")
	// ErrUnhealthy indicates the storage backend failed its health check.
	ErrUnhealthy = errors.New("storage: storage backend is unhealthy")
)

// StorageProvider abstracts object storage operations for public and private buckets.
type StorageProvider interface {
	UploadPublic(ctx context.Context, key string, r io.Reader, size int64, contentType string) (publicURL string, err error)
	UploadPrivate(ctx context.Context, key string, r io.Reader, size int64, contentType string) (objectKey string, err error)
	PresignedGetURL(ctx context.Context, key string, ttl time.Duration) (string, error)
	Delete(ctx context.Context, bucket, key string) error
	HealthCheck(ctx context.Context) error
}

// sanitizeKey trims leading slashes and validates that the object key is non-empty and safe.
func sanitizeKey(key string) (string, error) {
	clean := strings.TrimSpace(key)
	for strings.HasPrefix(clean, "/") {
		clean = strings.TrimPrefix(clean, "/")
	}
	clean = strings.TrimSpace(clean)
	if clean == "" || strings.ContainsRune(clean, '\\') {
		return "", ErrInvalidKey
	}
	for _, seg := range strings.Split(clean, "/") {
		if seg == "" || seg == "." || seg == ".." {
			return "", ErrInvalidKey
		}
	}
	return clean, nil
}

func buildPublicURL(publicURL, bucket, key string) string {
	base := strings.TrimRight(strings.TrimSpace(publicURL), "/")
	return base + "/" + bucket + "/" + key
}
