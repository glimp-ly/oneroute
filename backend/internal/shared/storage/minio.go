package storage

import (
	"context"
	"fmt"
	"io"
	"strings"
	"time"

	"github.com/minio/minio-go/v7"
	"github.com/minio/minio-go/v7/pkg/credentials"
)

var _ StorageProvider = (*MinIOStorage)(nil)

// MinIOConfig holds connection and bucket settings for MinIO / S3-compatible storage.
type MinIOConfig struct {
	Endpoint            string
	PublicURL           string
	AccessKey           string
	SecretKey           string
	PublicBucket        string
	PrivateBucket       string
	UseSSL              bool
	DefaultPresignedTTL time.Duration
}

// MinIOStorage implements StorageProvider backed by MinIO / S3.
type MinIOStorage struct {
	client              *minio.Client
	publicURL           string
	publicBucket        string
	privateBucket       string
	defaultPresignedTTL time.Duration
}

// NewMinIOStorage validates cfg and initializes a MinIOStorage client.
func NewMinIOStorage(cfg MinIOConfig) (*MinIOStorage, error) {
	endpoint := strings.TrimSpace(cfg.Endpoint)
	accessKey := strings.TrimSpace(cfg.AccessKey)
	secretKey := strings.TrimSpace(cfg.SecretKey)
	publicBucket := strings.TrimSpace(cfg.PublicBucket)
	privateBucket := strings.TrimSpace(cfg.PrivateBucket)

	if endpoint == "" || accessKey == "" || secretKey == "" || publicBucket == "" || privateBucket == "" {
		return nil, fmt.Errorf("%w: endpoint, credentials, public bucket, and private bucket are required", ErrInvalidConfig)
	}

	publicURL := strings.TrimRight(strings.TrimSpace(cfg.PublicURL), "/")
	if publicURL == "" {
		scheme := "http"
		if cfg.UseSSL {
			scheme = "https"
		}
		publicURL = scheme + "://" + endpoint
	}

	ttl := cfg.DefaultPresignedTTL
	if ttl <= 0 {
		ttl = DefaultPresignedTTL
	}

	client, err := minio.New(endpoint, &minio.Options{
		Creds:  credentials.NewStaticV4(accessKey, secretKey, ""),
		Secure: cfg.UseSSL,
	})
	if err != nil {
		return nil, fmt.Errorf("%w: initialize minio client: %v", ErrInvalidConfig, err)
	}

	return &MinIOStorage{
		client:              client,
		publicURL:           publicURL,
		publicBucket:        publicBucket,
		privateBucket:       privateBucket,
		defaultPresignedTTL: ttl,
	}, nil
}

// PublicBucket returns the configured public bucket name.
func (s *MinIOStorage) PublicBucket() string {
	return s.publicBucket
}

// PrivateBucket returns the configured private bucket name.
func (s *MinIOStorage) PrivateBucket() string {
	return s.privateBucket
}

// UploadPublic uploads an object to the public bucket and returns its public URL.
func (s *MinIOStorage) UploadPublic(ctx context.Context, key string, r io.Reader, size int64, contentType string) (string, error) {
	cleanKey, err := s.putObject(ctx, s.publicBucket, key, r, size, contentType)
	if err != nil {
		return "", err
	}
	return buildPublicURL(s.publicURL, s.publicBucket, cleanKey), nil
}

// UploadPrivate uploads an object to the private bucket and returns its sanitized object key.
func (s *MinIOStorage) UploadPrivate(ctx context.Context, key string, r io.Reader, size int64, contentType string) (string, error) {
	return s.putObject(ctx, s.privateBucket, key, r, size, contentType)
}

func (s *MinIOStorage) putObject(ctx context.Context, bucket, key string, r io.Reader, size int64, contentType string) (string, error) {
	if err := ctx.Err(); err != nil {
		return "", err
	}
	if r == nil {
		return "", ErrNilReader
	}
	cleanKey, err := sanitizeKey(key)
	if err != nil {
		return "", err
	}
	ct := strings.TrimSpace(contentType)
	if ct == "" {
		ct = "application/octet-stream"
	}
	if size < 0 {
		size = -1
	}

	if _, err := s.client.PutObject(ctx, bucket, cleanKey, r, size, minio.PutObjectOptions{
		ContentType: ct,
	}); err != nil {
		return "", fmt.Errorf("storage: put object %s/%s: %w", bucket, cleanKey, err)
	}
	return cleanKey, nil
}

// PresignedGetURL generates a time-limited GET URL for an object in the private bucket.
func (s *MinIOStorage) PresignedGetURL(ctx context.Context, key string, ttl time.Duration) (string, error) {
	if err := ctx.Err(); err != nil {
		return "", err
	}
	cleanKey, err := sanitizeKey(key)
	if err != nil {
		return "", err
	}
	effectiveTTL := ttl
	if effectiveTTL <= 0 {
		effectiveTTL = s.defaultPresignedTTL
	}

	u, err := s.client.PresignedGetObject(ctx, s.privateBucket, cleanKey, effectiveTTL, nil)
	if err != nil {
		return "", fmt.Errorf("storage: presign get %s/%s: %w", s.privateBucket, cleanKey, err)
	}
	return u.String(), nil
}

// Delete removes an object from the specified bucket.
func (s *MinIOStorage) Delete(ctx context.Context, bucket, key string) error {
	if err := ctx.Err(); err != nil {
		return err
	}
	cleanBucket := strings.TrimSpace(bucket)
	if cleanBucket == "" {
		return ErrInvalidBucket
	}
	cleanKey, err := sanitizeKey(key)
	if err != nil {
		return err
	}

	if err := s.client.RemoveObject(ctx, cleanBucket, cleanKey, minio.RemoveObjectOptions{}); err != nil {
		return fmt.Errorf("storage: delete object %s/%s: %w", cleanBucket, cleanKey, err)
	}
	return nil
}

// HealthCheck verifies that both public and private buckets exist and are reachable.
func (s *MinIOStorage) HealthCheck(ctx context.Context) error {
	if err := ctx.Err(); err != nil {
		return err
	}
	for _, bucket := range []string{s.publicBucket, s.privateBucket} {
		exists, err := s.client.BucketExists(ctx, bucket)
		if err != nil {
			return fmt.Errorf("%w: check bucket %q: %v", ErrUnhealthy, bucket, err)
		}
		if !exists {
			return fmt.Errorf("%w: bucket %q does not exist", ErrUnhealthy, bucket)
		}
	}
	return nil
}
