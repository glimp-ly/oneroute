package storage

import (
	"bytes"
	"context"
	"errors"
	"fmt"
	"net/url"
	"strings"
	"sync"
	"testing"
	"time"
)

func TestMockStorage_UploadPublic(t *testing.T) {
	cases := []struct {
		name        string
		key         string
		payload     string
		contentType string
		wantKey     string
		wantURL     string
		wantErr     error
	}{
		{
			name:        "valid key with leading slash",
			key:         "/avatars/user-1.png",
			payload:     "png-bytes",
			contentType: "image/png",
			wantKey:     "avatars/user-1.png",
			wantURL:     "http://localhost:9000/rutacompartida-public/avatars/user-1.png",
		},
		{
			name:        "valid nested key without leading slash",
			key:         "vehicles/veh-9/photo.jpg",
			payload:     "jpg-bytes",
			contentType: "image/jpeg",
			wantKey:     "vehicles/veh-9/photo.jpg",
			wantURL:     "http://localhost:9000/rutacompartida-public/vehicles/veh-9/photo.jpg",
		},
		{
			name:        "empty key fails",
			key:         "",
			payload:     "data",
			contentType: "text/plain",
			wantErr:     ErrInvalidKey,
		},
		{
			name:        "only slashes fails",
			key:         "///",
			payload:     "data",
			contentType: "text/plain",
			wantErr:     ErrInvalidKey,
		},
		{
			name:        "path traversal fails",
			key:         "avatars/../secret.txt",
			payload:     "data",
			contentType: "text/plain",
			wantErr:     ErrInvalidKey,
		},
	}

	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			store := NewMockStorage()
			ctx := context.Background()

			gotURL, err := store.UploadPublic(ctx, tc.key, strings.NewReader(tc.payload), int64(len(tc.payload)), tc.contentType)
			if tc.wantErr != nil {
				if !errors.Is(err, tc.wantErr) {
					t.Fatalf("expected error %v, got %v", tc.wantErr, err)
				}
				return
			}
			if err != nil {
				t.Fatalf("unexpected error: %v", err)
			}
			if gotURL != tc.wantURL {
				t.Fatalf("expected url %q, got %q", tc.wantURL, gotURL)
			}

			obj, ok := store.GetObject(store.PublicBucket(), tc.wantKey)
			if !ok {
				t.Fatalf("expected object at key %q in public bucket", tc.wantKey)
			}
			if string(obj.Data) != tc.payload {
				t.Fatalf("expected payload %q, got %q", tc.payload, string(obj.Data))
			}
			if obj.ContentType != tc.contentType {
				t.Fatalf("expected content type %q, got %q", tc.contentType, obj.ContentType)
			}
		})
	}
}

func TestMockStorage_UploadPrivateAndNilReader(t *testing.T) {
	store := NewMockStorage()
	ctx := context.Background()

	if _, err := store.UploadPrivate(ctx, "dni/user-1.jpg", nil, 0, "image/jpeg"); !errors.Is(err, ErrNilReader) {
		t.Fatalf("expected ErrNilReader, got %v", err)
	}

	payload := []byte("private-dni-document")
	objKey, err := store.UploadPrivate(ctx, "/dni/user-1.jpg", bytes.NewReader(payload), int64(len(payload)), "")
	if err != nil {
		t.Fatalf("unexpected upload error: %v", err)
	}
	if objKey != "dni/user-1.jpg" {
		t.Fatalf("expected sanitized key %q, got %q", "dni/user-1.jpg", objKey)
	}

	obj, ok := store.GetObject(store.PrivateBucket(), objKey)
	if !ok {
		t.Fatal("expected private object to exist")
	}
	if !bytes.Equal(obj.Data, payload) {
		t.Fatalf("stored bytes mismatch")
	}
	if obj.ContentType != "application/octet-stream" {
		t.Fatalf("expected default content type application/octet-stream, got %q", obj.ContentType)
	}
}

func TestMockStorage_PresignedGetURL(t *testing.T) {
	store := NewMockStorage(WithDefaultPresignedTTL(5 * time.Minute))
	ctx := context.Background()

	if _, err := store.PresignedGetURL(ctx, "///", time.Minute); !errors.Is(err, ErrInvalidKey) {
		t.Fatalf("expected ErrInvalidKey, got %v", err)
	}

	defaultURL, err := store.PresignedGetURL(ctx, "/reports/ev-1.png", 0)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	parsedDefault, err := url.Parse(defaultURL)
	if err != nil {
		t.Fatalf("invalid presigned url %q: %v", defaultURL, err)
	}
	if !strings.Contains(parsedDefault.Path, "/rutacompartida-private/reports/ev-1.png") {
		t.Fatalf("unexpected presigned path %q", parsedDefault.Path)
	}
	if got := parsedDefault.Query().Get("X-Amz-Expires"); got != "300" {
		t.Fatalf("expected default X-Amz-Expires=300, got %q", got)
	}

	customURL, err := store.PresignedGetURL(ctx, "reports/ev-1.png", 90*time.Second)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	parsedCustom, err := url.Parse(customURL)
	if err != nil {
		t.Fatalf("invalid presigned url %q: %v", customURL, err)
	}
	if got := parsedCustom.Query().Get("X-Amz-Expires"); got != "90" {
		t.Fatalf("expected custom X-Amz-Expires=90, got %q", got)
	}
}

func TestMockStorage_Delete(t *testing.T) {
	store := NewMockStorage()
	ctx := context.Background()

	if err := store.Delete(ctx, "", "a/b.txt"); !errors.Is(err, ErrInvalidBucket) {
		t.Fatalf("expected ErrInvalidBucket, got %v", err)
	}
	if err := store.Delete(ctx, store.PublicBucket(), ""); !errors.Is(err, ErrInvalidKey) {
		t.Fatalf("expected ErrInvalidKey, got %v", err)
	}

	if _, err := store.UploadPublic(ctx, "/tmp/test.txt", strings.NewReader("hello"), 5, "text/plain"); err != nil {
		t.Fatalf("upload failed: %v", err)
	}
	if store.ObjectCount() != 1 {
		t.Fatalf("expected 1 object, got %d", store.ObjectCount())
	}

	if err := store.Delete(ctx, store.PublicBucket(), "/tmp/test.txt"); err != nil {
		t.Fatalf("delete failed: %v", err)
	}
	if store.ObjectCount() != 0 {
		t.Fatalf("expected 0 objects after delete, got %d", store.ObjectCount())
	}
	if _, ok := store.GetObject(store.PublicBucket(), "tmp/test.txt"); ok {
		t.Fatal("expected object to be deleted")
	}
}

func TestMockStorage_HealthCheckAndContextCancel(t *testing.T) {
	store := NewMockStorage()
	if err := store.HealthCheck(context.Background()); err != nil {
		t.Fatalf("expected healthy mock storage, got %v", err)
	}

	expectedErr := errors.New("connection refused")
	store.SetHealthError(expectedErr)
	err := store.HealthCheck(context.Background())
	if !errors.Is(err, ErrUnhealthy) || !errors.Is(err, expectedErr) {
		t.Fatalf("expected ErrUnhealthy wrapping %v, got %v", expectedErr, err)
	}

	canceledCtx, cancel := context.WithCancel(context.Background())
	cancel()
	if _, err := store.UploadPublic(canceledCtx, "a/b.txt", strings.NewReader("x"), 1, "text/plain"); !errors.Is(err, context.Canceled) {
		t.Fatalf("expected context.Canceled, got %v", err)
	}
}

func TestMockStorage_ConcurrentAccess(t *testing.T) {
	store := NewMockStorage()
	ctx := context.Background()

	const workers = 24
	var wg sync.WaitGroup
	errs := make(chan error, workers*4)

	for i := 0; i < workers; i++ {
		wg.Add(1)
		go func(id int) {
			defer wg.Done()
			pubKey := fmt.Sprintf("avatars/user-%d.png", id)
			privKey := fmt.Sprintf("docs/doc-%d.pdf", id)
			body := fmt.Sprintf("payload-%d", id)

			if _, err := store.UploadPublic(ctx, pubKey, strings.NewReader(body), int64(len(body)), "image/png"); err != nil {
				errs <- err
				return
			}
			if _, err := store.UploadPrivate(ctx, privKey, strings.NewReader(body), int64(len(body)), "application/pdf"); err != nil {
				errs <- err
				return
			}
			if _, err := store.PresignedGetURL(ctx, privKey, 2*time.Minute); err != nil {
				errs <- err
				return
			}
			if err := store.HealthCheck(ctx); err != nil {
				errs <- err
				return
			}
			if obj, ok := store.GetObject(store.PublicBucket(), pubKey); !ok || string(obj.Data) != body {
				errs <- fmt.Errorf("unexpected public object for worker %d", id)
				return
			}
			if err := store.Delete(ctx, store.PublicBucket(), pubKey); err != nil {
				errs <- err
				return
			}
		}(i)
	}

	wg.Wait()
	close(errs)

	for err := range errs {
		if err != nil {
			t.Fatalf("concurrent operation failed: %v", err)
		}
	}

	if got := store.ObjectCount(); got != workers {
		t.Fatalf("expected %d remaining private objects, got %d", workers, got)
	}
}

func TestNewMinIOStorage_ConfigValidation(t *testing.T) {
	valid := MinIOConfig{
		Endpoint:      "127.0.0.1:9000",
		PublicURL:     "http://127.0.0.1:9000/",
		AccessKey:     "minioadmin",
		SecretKey:     "minioadmin123",
		PublicBucket:  "rutacompartida-public",
		PrivateBucket: "rutacompartida-private",
		UseSSL:        false,
	}

	cases := []struct {
		name    string
		mutate  func(*MinIOConfig)
		wantErr bool
	}{
		{
			name:   "valid config",
			mutate: func(c *MinIOConfig) {},
		},
		{
			name:    "missing endpoint",
			mutate:  func(c *MinIOConfig) { c.Endpoint = "" },
			wantErr: true,
		},
		{
			name:    "missing access key",
			mutate:  func(c *MinIOConfig) { c.AccessKey = "" },
			wantErr: true,
		},
		{
			name:    "missing secret key",
			mutate:  func(c *MinIOConfig) { c.SecretKey = "" },
			wantErr: true,
		},
		{
			name:    "missing public bucket",
			mutate:  func(c *MinIOConfig) { c.PublicBucket = "" },
			wantErr: true,
		},
		{
			name:    "missing private bucket",
			mutate:  func(c *MinIOConfig) { c.PrivateBucket = "" },
			wantErr: true,
		},
	}

	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			cfg := valid
			tc.mutate(&cfg)
			s, err := NewMinIOStorage(cfg)
			if tc.wantErr {
				if !errors.Is(err, ErrInvalidConfig) {
					t.Fatalf("expected ErrInvalidConfig, got %v", err)
				}
				return
			}
			if err != nil {
				t.Fatalf("unexpected error: %v", err)
			}
			if s.PublicBucket() != "rutacompartida-public" {
				t.Fatalf("unexpected public bucket: %q", s.PublicBucket())
			}
			if s.PrivateBucket() != "rutacompartida-private" {
				t.Fatalf("unexpected private bucket: %q", s.PrivateBucket())
			}
			if s.defaultPresignedTTL != DefaultPresignedTTL {
				t.Fatalf("expected default TTL %v, got %v", DefaultPresignedTTL, s.defaultPresignedTTL)
			}
			if s.publicURL != "http://127.0.0.1:9000" {
				t.Fatalf("expected trimmed publicURL, got %q", s.publicURL)
			}
		})
	}
}
