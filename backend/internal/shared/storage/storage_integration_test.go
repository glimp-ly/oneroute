//go:build integration

package storage

import (
	"bytes"
	"context"
	"fmt"
	"io"
	"net/http"
	"os"
	"strings"
	"testing"
	"time"
)

func envOrDefault(key, fallback string) string {
	if v := strings.TrimSpace(os.Getenv(key)); v != "" {
		return v
	}
	return fallback
}

func newTestMinIOStorage(t *testing.T) *MinIOStorage {
	t.Helper()

	endpoint := envOrDefault("STORAGE_ENDPOINT", "127.0.0.1:9000")
	publicURL := envOrDefault("STORAGE_PUBLIC_URL", "http://127.0.0.1:9000")
	accessKey := envOrDefault("STORAGE_ACCESS_KEY", "minioadmin")
	secretKey := envOrDefault("STORAGE_SECRET_KEY", "minioadmin123")
	publicBucket := envOrDefault("STORAGE_BUCKET_PUBLIC", "rutacompartida-public")
	privateBucket := envOrDefault("STORAGE_BUCKET_PRIVATE", "rutacompartida-private")

	store, err := NewMinIOStorage(MinIOConfig{
		Endpoint:            endpoint,
		PublicURL:           publicURL,
		AccessKey:           accessKey,
		SecretKey:           secretKey,
		PublicBucket:        publicBucket,
		PrivateBucket:       privateBucket,
		UseSSL:              false,
		DefaultPresignedTTL: 5 * time.Minute,
	})
	if err != nil {
		t.Fatalf("failed to create MinIOStorage: %v", err)
	}
	return store
}

func httpGetBytes(t *testing.T, targetURL string) (int, []byte) {
	t.Helper()

	client := &http.Client{Timeout: 10 * time.Second}
	resp, err := client.Get(targetURL)
	if err != nil {
		t.Fatalf("HTTP GET %q failed: %v", targetURL, err)
	}
	defer resp.Body.Close()

	body, err := io.ReadAll(resp.Body)
	if err != nil {
		t.Fatalf("read HTTP response body from %q failed: %v", targetURL, err)
	}
	return resp.StatusCode, body
}

func TestMinIOStorage_Integration_HealthPublicPrivateAndDelete(t *testing.T) {
	store := newTestMinIOStorage(t)
	ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
	defer cancel()

	if err := store.HealthCheck(ctx); err != nil {
		t.Fatalf("HealthCheck failed: %v", err)
	}

	uniqueSuffix := fmt.Sprintf("%d", time.Now().UnixNano())
	publicKey := fmt.Sprintf("integration-tests/public-%s.txt", uniqueSuffix)
	publicPayload := []byte("oneroute-public-integration-payload-" + uniqueSuffix)

	t.Cleanup(func() {
		cleanupCtx, cleanupCancel := context.WithTimeout(context.Background(), 10*time.Second)
		defer cleanupCancel()
		_ = store.Delete(cleanupCtx, store.PublicBucket(), publicKey)
	})

	publicURL, err := store.UploadPublic(ctx, "/"+publicKey, bytes.NewReader(publicPayload), int64(len(publicPayload)), "text/plain")
	if err != nil {
		t.Fatalf("UploadPublic failed: %v", err)
	}
	wantSuffix := "/" + store.PublicBucket() + "/" + publicKey
	if !strings.HasSuffix(publicURL, wantSuffix) {
		t.Fatalf("expected public URL suffix %q, got %q", wantSuffix, publicURL)
	}

	status, fetchedPublic := httpGetBytes(t, publicURL)
	if status != http.StatusOK {
		t.Fatalf("expected 200 OK for public object %q, got %d", publicURL, status)
	}
	if !bytes.Equal(fetchedPublic, publicPayload) {
		t.Fatalf("public payload mismatch: got %q, want %q", string(fetchedPublic), string(publicPayload))
	}

	if err := store.Delete(ctx, store.PublicBucket(), publicKey); err != nil {
		t.Fatalf("Delete public object failed: %v", err)
	}
	statusAfterDelete, _ := httpGetBytes(t, publicURL)
	if statusAfterDelete == http.StatusOK {
		t.Fatalf("expected deleted public object %q not to return 200 OK", publicURL)
	}

	privateKey := fmt.Sprintf("integration-tests/private-%s.bin", uniqueSuffix)
	privatePayload := []byte("oneroute-private-integration-payload-" + uniqueSuffix)

	t.Cleanup(func() {
		cleanupCtx, cleanupCancel := context.WithTimeout(context.Background(), 10*time.Second)
		defer cleanupCancel()
		_ = store.Delete(cleanupCtx, store.PrivateBucket(), privateKey)
	})

	storedKey, err := store.UploadPrivate(ctx, "/"+privateKey, bytes.NewReader(privatePayload), int64(len(privatePayload)), "application/octet-stream")
	if err != nil {
		t.Fatalf("UploadPrivate failed: %v", err)
	}
	if storedKey != privateKey {
		t.Fatalf("expected sanitized private key %q, got %q", privateKey, storedKey)
	}

	unsignedPrivateURL := buildPublicURL(store.publicURL, store.PrivateBucket(), storedKey)
	unsignedStatus, _ := httpGetBytes(t, unsignedPrivateURL)
	if unsignedStatus == http.StatusOK {
		t.Fatalf("expected unsigned GET on private object %q to be denied, got 200 OK", unsignedPrivateURL)
	}

	presignedURL, err := store.PresignedGetURL(ctx, storedKey, 0)
	if err != nil {
		t.Fatalf("PresignedGetURL failed: %v", err)
	}
	presignedStatus, fetchedPrivate := httpGetBytes(t, presignedURL)
	if presignedStatus != http.StatusOK {
		t.Fatalf("expected 200 OK for presigned URL, got %d", presignedStatus)
	}
	if !bytes.Equal(fetchedPrivate, privatePayload) {
		t.Fatalf("private payload mismatch: got %q, want %q", string(fetchedPrivate), string(privatePayload))
	}

	if err := store.Delete(ctx, store.PrivateBucket(), storedKey); err != nil {
		t.Fatalf("Delete private object failed: %v", err)
	}
	statusPrivateAfterDelete, _ := httpGetBytes(t, presignedURL)
	if statusPrivateAfterDelete == http.StatusOK {
		t.Fatalf("expected deleted private object not to return 200 OK")
	}
}
