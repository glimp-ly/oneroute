package storage

import (
	"context"
	"fmt"
	"io"
	"net/url"
	"strings"
	"sync"
	"time"
)

const (
	defaultMockPublicURL     = "http://localhost:9000"
	defaultMockPublicBucket  = "rutacompartida-public"
	defaultMockPrivateBucket = "rutacompartida-private"
)

var _ StorageProvider = (*MockStorage)(nil)

type objectID struct {
	bucket string
	key    string
}

// MockObject stores a single uploaded object's metadata and payload in memory.
type MockObject struct {
	Bucket      string
	Key         string
	Data        []byte
	ContentType string
	UploadedAt  time.Time
}

// MockOption configures a MockStorage instance.
type MockOption func(*MockStorage)

// WithPublicURL overrides the base public URL used by MockStorage.
func WithPublicURL(rawURL string) MockOption {
	return func(m *MockStorage) {
		if trimmed := strings.TrimRight(strings.TrimSpace(rawURL), "/"); trimmed != "" {
			m.publicURL = trimmed
		}
	}
}

// WithBuckets overrides the public and private bucket names used by MockStorage.
func WithBuckets(publicBucket, privateBucket string) MockOption {
	return func(m *MockStorage) {
		if trimmed := strings.TrimSpace(publicBucket); trimmed != "" {
			m.publicBucket = trimmed
		}
		if trimmed := strings.TrimSpace(privateBucket); trimmed != "" {
			m.privateBucket = trimmed
		}
	}
}

// WithDefaultPresignedTTL overrides the default TTL used when ttl <= 0.
func WithDefaultPresignedTTL(ttl time.Duration) MockOption {
	return func(m *MockStorage) {
		if ttl > 0 {
			m.defaultPresignedTTL = ttl
		}
	}
}

// MockStorage is a thread-safe in-memory StorageProvider for unit tests and offline dev.
type MockStorage struct {
	mu                  sync.RWMutex
	objects             map[objectID]MockObject
	publicURL           string
	publicBucket        string
	privateBucket       string
	defaultPresignedTTL time.Duration
	healthErr           error
}

// NewMockStorage creates a thread-safe in-memory StorageProvider.
func NewMockStorage(opts ...MockOption) *MockStorage {
	m := &MockStorage{
		objects:             make(map[objectID]MockObject),
		publicURL:           defaultMockPublicURL,
		publicBucket:        defaultMockPublicBucket,
		privateBucket:       defaultMockPrivateBucket,
		defaultPresignedTTL: DefaultPresignedTTL,
	}
	for _, opt := range opts {
		if opt != nil {
			opt(m)
		}
	}
	return m
}

// PublicBucket returns the configured public bucket name.
func (m *MockStorage) PublicBucket() string {
	return m.publicBucket
}

// PrivateBucket returns the configured private bucket name.
func (m *MockStorage) PrivateBucket() string {
	return m.privateBucket
}

// UploadPublic stores an object in the public bucket and returns its public URL.
func (m *MockStorage) UploadPublic(ctx context.Context, key string, r io.Reader, size int64, contentType string) (string, error) {
	cleanKey, err := m.putObject(ctx, m.publicBucket, key, r, size, contentType)
	if err != nil {
		return "", err
	}
	return buildPublicURL(m.publicURL, m.publicBucket, cleanKey), nil
}

// UploadPrivate stores an object in the private bucket and returns its sanitized object key.
func (m *MockStorage) UploadPrivate(ctx context.Context, key string, r io.Reader, size int64, contentType string) (string, error) {
	return m.putObject(ctx, m.privateBucket, key, r, size, contentType)
}

func (m *MockStorage) putObject(ctx context.Context, bucket, key string, r io.Reader, _ int64, contentType string) (string, error) {
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
	data, err := io.ReadAll(r)
	if err != nil {
		return "", fmt.Errorf("storage: read mock upload payload: %w", err)
	}
	ct := strings.TrimSpace(contentType)
	if ct == "" {
		ct = "application/octet-stream"
	}

	m.mu.Lock()
	m.objects[objectID{bucket: bucket, key: cleanKey}] = MockObject{
		Bucket:      bucket,
		Key:         cleanKey,
		Data:        data,
		ContentType: ct,
		UploadedAt:  time.Now().UTC(),
	}
	m.mu.Unlock()

	return cleanKey, nil
}

// PresignedGetURL returns a deterministic signed-style URL for the private object key.
func (m *MockStorage) PresignedGetURL(ctx context.Context, key string, ttl time.Duration) (string, error) {
	if err := ctx.Err(); err != nil {
		return "", err
	}
	cleanKey, err := sanitizeKey(key)
	if err != nil {
		return "", err
	}
	effectiveTTL := ttl
	if effectiveTTL <= 0 {
		effectiveTTL = m.defaultPresignedTTL
	}
	q := url.Values{}
	q.Set("X-Amz-Expires", fmt.Sprintf("%d", int64(effectiveTTL.Seconds())))
	q.Set("mock-sig", "true")
	return buildPublicURL(m.publicURL, m.privateBucket, cleanKey) + "?" + q.Encode(), nil
}

// Delete removes the object at (bucket, key) from memory.
func (m *MockStorage) Delete(ctx context.Context, bucket, key string) error {
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

	m.mu.Lock()
	delete(m.objects, objectID{bucket: cleanBucket, key: cleanKey})
	m.mu.Unlock()
	return nil
}

// HealthCheck verifies whether MockStorage is configured as healthy.
func (m *MockStorage) HealthCheck(ctx context.Context) error {
	if err := ctx.Err(); err != nil {
		return err
	}
	m.mu.RLock()
	err := m.healthErr
	m.mu.RUnlock()
	if err != nil {
		return fmt.Errorf("%w: %w", ErrUnhealthy, err)
	}
	return nil
}

// SetHealthError configures an error to be returned by HealthCheck (nil restores healthy state).
func (m *MockStorage) SetHealthError(err error) {
	m.mu.Lock()
	m.healthErr = err
	m.mu.Unlock()
}

// GetObject returns a defensive copy of the stored object if present.
func (m *MockStorage) GetObject(bucket, key string) (MockObject, bool) {
	cleanBucket := strings.TrimSpace(bucket)
	cleanKey, err := sanitizeKey(key)
	if cleanBucket == "" || err != nil {
		return MockObject{}, false
	}

	m.mu.RLock()
	obj, ok := m.objects[objectID{bucket: cleanBucket, key: cleanKey}]
	m.mu.RUnlock()
	if !ok {
		return MockObject{}, false
	}

	copied := make([]byte, len(obj.Data))
	copy(copied, obj.Data)
	obj.Data = copied
	return obj, true
}

// ObjectCount returns the total number of stored objects across all buckets.
func (m *MockStorage) ObjectCount() int {
	m.mu.RLock()
	n := len(m.objects)
	m.mu.RUnlock()
	return n
}
