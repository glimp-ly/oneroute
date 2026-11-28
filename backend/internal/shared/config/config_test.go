package config_test

import (
	"os"
	"path/filepath"
	"testing"
	"time"

	"github.com/glimp-ly/oneroute/backend/internal/shared/config"
)

func setBaseValidEnv(t *testing.T) {
	t.Helper()
	t.Setenv("APP_ENV", "development")
	t.Setenv("DATABASE_URL", "postgres://rutacompartida:secret@localhost:5432/rutacompartida?sslmode=disable")
	t.Setenv("JWT_ACCESS_SECRET", "dev_access_secret_16+")
	t.Setenv("JWT_REFRESH_SECRET", "dev_refresh_secret_16+")
}

func clearAllConfigEnv(t *testing.T) {
	t.Helper()
	keys := []string{
		"APP_ENV", "APP_PORT", "APP_BASE_URL", "APP_TIMEZONE", "CORS_ALLOWED_ORIGINS", "CLOUD_PUBLIC_API_URL",
		"POSTGRES_HOST", "POSTGRES_PORT", "POSTGRES_DB", "POSTGRES_USER", "POSTGRES_PASSWORD", "DATABASE_URL",
		"JWT_ACCESS_SECRET", "JWT_REFRESH_SECRET", "JWT_ACCESS_TTL_MINUTES", "JWT_REFRESH_TTL_DAYS",
		"STORAGE_PROVIDER", "STORAGE_ENDPOINT", "STORAGE_PUBLIC_URL", "STORAGE_ACCESS_KEY", "STORAGE_SECRET_KEY",
		"STORAGE_BUCKET_PUBLIC", "STORAGE_BUCKET_PRIVATE", "STORAGE_USE_SSL", "STORAGE_PRESIGNED_TTL_SECONDS",
		"ROUTING_PROVIDER", "OSRM_BASE_URL", "OSRM_LOCAL_URL", "OSRM_TIMEOUT_SECONDS",
		"PAYMENT_PROVIDER", "PAYMENT_MOCK_DEFAULT_RESULT",
		"IDENTITY_PROVIDER", "IDENTITY_MOCK_AUTO_APPROVE",
		"OTP_PROVIDER", "SMTP_HOST", "SMTP_PORT", "SMTP_USER", "SMTP_PASSWORD", "SMTP_FROM", "OTP_TTL_MINUTES",
		"PUSH_PROVIDER", "FCM_CREDENTIALS_JSON",
		"SCHEDULER_ENABLED", "SCHEDULER_HOLD_POLL_SECONDS", "SCHEDULER_CREDITS_POLL_SECONDS",
		"SCHEDULER_RATINGS_POLL_SECONDS", "SCHEDULER_GPS_RETENTION_POLL_MINUTES",
	}
	for _, k := range keys {
		t.Setenv(k, "")
		_ = os.Unsetenv(k)
	}
}

func TestLoadFromEnvExample(t *testing.T) {
	clearAllConfigEnv(t)

	envExamplePath := filepath.Join("..", "..", "..", ".env.example")
	cfg, err := config.Load(envExamplePath)
	if err != nil {
		t.Fatalf("expected .env.example to load without error, got: %v", err)
	}

	if cfg.App.Env != "development" {
		t.Errorf("App.Env = %q, want development", cfg.App.Env)
	}
	if cfg.App.Port != 8080 {
		t.Errorf("App.Port = %d, want 8080", cfg.App.Port)
	}
	if cfg.App.BaseURL != "http://localhost:8080" {
		t.Errorf("App.BaseURL = %q, want http://localhost:8080", cfg.App.BaseURL)
	}
	if cfg.App.Timezone != "America/Lima" {
		t.Errorf("App.Timezone = %q, want America/Lima", cfg.App.Timezone)
	}
	if len(cfg.App.CORSAllowedOrigins) != 2 || cfg.App.CORSAllowedOrigins[0] != "http://localhost:3000" {
		t.Errorf("App.CORSAllowedOrigins = %v, want [http://localhost:3000 http://localhost:8080]", cfg.App.CORSAllowedOrigins)
	}
	if cfg.DB.Host != "localhost" || cfg.DB.Port != 5432 || cfg.DB.Name != "rutacompartida" || cfg.DB.User != "rutacompartida" {
		t.Errorf("unexpected DB fields: %+v", cfg.DB)
	}
	if cfg.DB.DatabaseURL == "" {
		t.Errorf("DB.DatabaseURL should not be empty")
	}
	if cfg.JWT.AccessTTL != 15*time.Minute {
		t.Errorf("JWT.AccessTTL = %v, want 15m", cfg.JWT.AccessTTL)
	}
	if cfg.JWT.RefreshTTL != 30*24*time.Hour {
		t.Errorf("JWT.RefreshTTL = %v, want 720h", cfg.JWT.RefreshTTL)
	}
	if cfg.Storage.Provider != "minio" || cfg.Storage.UseSSL {
		t.Errorf("unexpected Storage fields: %+v", cfg.Storage)
	}
	if cfg.Storage.PresignedTTL != 300*time.Second {
		t.Errorf("Storage.PresignedTTL = %v, want 300s", cfg.Storage.PresignedTTL)
	}
	if cfg.Routing.Provider != "osrm_public" || cfg.Routing.Timeout != 8*time.Second {
		t.Errorf("unexpected Routing fields: %+v", cfg.Routing)
	}
	if cfg.Providers.PaymentProvider != "mock" || cfg.Providers.PaymentMockDefaultResult != "success" {
		t.Errorf("unexpected Payment provider fields: %+v", cfg.Providers)
	}
	if cfg.Providers.IdentityProvider != "mock" || cfg.Providers.IdentityMockAutoApprove {
		t.Errorf("unexpected Identity provider fields: %+v", cfg.Providers)
	}
	if cfg.Providers.OTPProvider != "mailpit_console" || cfg.Providers.SMTPPort != 1025 || cfg.Providers.OTPTTL != 10*time.Minute {
		t.Errorf("unexpected OTP provider fields: %+v", cfg.Providers)
	}
	if cfg.Providers.PushProvider != "mock" {
		t.Errorf("Providers.PushProvider = %q, want mock", cfg.Providers.PushProvider)
	}
	if !cfg.Scheduler.Enabled || cfg.Scheduler.HoldPollInterval != 5*time.Second ||
		cfg.Scheduler.CreditsPollInterval != 60*time.Second ||
		cfg.Scheduler.RatingsPollInterval != 60*time.Second ||
		cfg.Scheduler.GPSRetentionPollInterval != 60*time.Minute {
		t.Errorf("unexpected Scheduler fields: %+v", cfg.Scheduler)
	}
}

func TestLoadIgnoresMissingDotEnvAndAppliesDefaults(t *testing.T) {
	clearAllConfigEnv(t)
	setBaseValidEnv(t)

	cfg, err := config.Load(filepath.Join(t.TempDir(), "does-not-exist.env"))
	if err != nil {
		t.Fatalf("expected missing .env to be ignored, got error: %v", err)
	}

	if cfg.App.Port != 8080 {
		t.Errorf("default App.Port = %d, want 8080", cfg.App.Port)
	}
	if cfg.App.Timezone != "America/Lima" {
		t.Errorf("default App.Timezone = %q, want America/Lima", cfg.App.Timezone)
	}
	if cfg.Routing.Provider != "osrm_public" {
		t.Errorf("default Routing.Provider = %q, want osrm_public", cfg.Routing.Provider)
	}
	if cfg.Scheduler.Enabled != true {
		t.Errorf("default Scheduler.Enabled = %v, want true", cfg.Scheduler.Enabled)
	}
}

func TestLoadSynthesizesDatabaseURLFromPostgresVars(t *testing.T) {
	clearAllConfigEnv(t)
	t.Setenv("POSTGRES_HOST", "db.internal")
	t.Setenv("POSTGRES_PORT", "5433")
	t.Setenv("POSTGRES_DB", "oneroute")
	t.Setenv("POSTGRES_USER", "appuser")
	t.Setenv("POSTGRES_PASSWORD", "p@ss/word#1")
	t.Setenv("JWT_ACCESS_SECRET", "access_secret_at_least_16_chars")
	t.Setenv("JWT_REFRESH_SECRET", "refresh_secret_at_least_16_chars")

	cfg, err := config.Load(filepath.Join(t.TempDir(), "missing.env"))
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	want := "postgres://appuser:p%40ss%2Fword%231@db.internal:5433/oneroute?sslmode=disable"
	if cfg.DB.DatabaseURL != want {
		t.Errorf("DB.DatabaseURL = %q, want %q", cfg.DB.DatabaseURL, want)
	}
}

func TestLoadValidationErrors(t *testing.T) {
	cases := []struct {
		name  string
		setup func(t *testing.T)
	}{
		{
			name: "missing database url and postgres vars",
			setup: func(t *testing.T) {
				t.Setenv("JWT_ACCESS_SECRET", "access_secret_16chars")
				t.Setenv("JWT_REFRESH_SECRET", "refresh_secret_16chars")
			},
		},
		{
			name: "invalid database url scheme",
			setup: func(t *testing.T) {
				setBaseValidEnv(t)
				t.Setenv("DATABASE_URL", "mysql://user:pass@localhost:3306/db")
			},
		},
		{
			name: "invalid APP_ENV",
			setup: func(t *testing.T) {
				setBaseValidEnv(t)
				t.Setenv("APP_ENV", "local_invalid")
			},
		},
		{
			name: "invalid APP_PORT non-numeric",
			setup: func(t *testing.T) {
				setBaseValidEnv(t)
				t.Setenv("APP_PORT", "abc")
			},
		},
		{
			name: "invalid APP_PORT out of range",
			setup: func(t *testing.T) {
				setBaseValidEnv(t)
				t.Setenv("APP_PORT", "70000")
			},
		},
		{
			name: "invalid APP_TIMEZONE",
			setup: func(t *testing.T) {
				setBaseValidEnv(t)
				t.Setenv("APP_TIMEZONE", "Mars/Olympus_Mons")
			},
		},
		{
			name: "jwt access secret too short in dev",
			setup: func(t *testing.T) {
				setBaseValidEnv(t)
				t.Setenv("JWT_ACCESS_SECRET", "short")
			},
		},
		{
			name: "jwt access secret too short in production",
			setup: func(t *testing.T) {
				setBaseValidEnv(t)
				t.Setenv("APP_ENV", "production")
				t.Setenv("JWT_ACCESS_SECRET", "twenty_chars_long_12")
				t.Setenv("JWT_REFRESH_SECRET", "refresh_secret_32_characters_minimum")
			},
		},
		{
			name: "jwt refresh secret too short in production",
			setup: func(t *testing.T) {
				setBaseValidEnv(t)
				t.Setenv("APP_ENV", "production")
				t.Setenv("JWT_ACCESS_SECRET", "access_secret_32_characters_minimum")
				t.Setenv("JWT_REFRESH_SECRET", "twenty_chars_long_12")
			},
		},
		{
			name: "non-positive JWT access ttl",
			setup: func(t *testing.T) {
				setBaseValidEnv(t)
				t.Setenv("JWT_ACCESS_TTL_MINUTES", "0")
			},
		},
		{
			name: "non-positive JWT refresh ttl",
			setup: func(t *testing.T) {
				setBaseValidEnv(t)
				t.Setenv("JWT_REFRESH_TTL_DAYS", "-5")
			},
		},
		{
			name: "non-positive presigned ttl",
			setup: func(t *testing.T) {
				setBaseValidEnv(t)
				t.Setenv("STORAGE_PRESIGNED_TTL_SECONDS", "0")
			},
		},
		{
			name: "non-positive osrm timeout",
			setup: func(t *testing.T) {
				setBaseValidEnv(t)
				t.Setenv("OSRM_TIMEOUT_SECONDS", "0")
			},
		},
		{
			name: "non-positive otp ttl",
			setup: func(t *testing.T) {
				setBaseValidEnv(t)
				t.Setenv("OTP_TTL_MINUTES", "0")
			},
		},
		{
			name: "non-positive scheduler hold poll",
			setup: func(t *testing.T) {
				setBaseValidEnv(t)
				t.Setenv("SCHEDULER_HOLD_POLL_SECONDS", "0")
			},
		},
		{
			name: "invalid STORAGE_PROVIDER",
			setup: func(t *testing.T) {
				setBaseValidEnv(t)
				t.Setenv("STORAGE_PROVIDER", "gcs")
			},
		},
		{
			name: "invalid STORAGE_USE_SSL bool",
			setup: func(t *testing.T) {
				setBaseValidEnv(t)
				t.Setenv("STORAGE_USE_SSL", "maybe")
			},
		},
		{
			name: "invalid ROUTING_PROVIDER",
			setup: func(t *testing.T) {
				setBaseValidEnv(t)
				t.Setenv("ROUTING_PROVIDER", "google_maps")
			},
		},
		{
			name: "invalid PAYMENT_PROVIDER",
			setup: func(t *testing.T) {
				setBaseValidEnv(t)
				t.Setenv("PAYMENT_PROVIDER", "stripe")
			},
		},
		{
			name: "invalid PAYMENT_MOCK_DEFAULT_RESULT",
			setup: func(t *testing.T) {
				setBaseValidEnv(t)
				t.Setenv("PAYMENT_MOCK_DEFAULT_RESULT", "pending")
			},
		},
		{
			name: "invalid IDENTITY_PROVIDER",
			setup: func(t *testing.T) {
				setBaseValidEnv(t)
				t.Setenv("IDENTITY_PROVIDER", "manual")
			},
		},
		{
			name: "invalid OTP_PROVIDER",
			setup: func(t *testing.T) {
				setBaseValidEnv(t)
				t.Setenv("OTP_PROVIDER", "sendgrid")
			},
		},
		{
			name: "invalid PUSH_PROVIDER",
			setup: func(t *testing.T) {
				setBaseValidEnv(t)
				t.Setenv("PUSH_PROVIDER", "apns")
			},
		},
	}

	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			clearAllConfigEnv(t)
			tc.setup(t)
			_, err := config.Load(filepath.Join(t.TempDir(), "missing.env"))
			if err == nil {
				t.Fatalf("expected validation error for case %q, got nil", tc.name)
			}
		})
	}
}

func TestLoadProductionValidSecretsPass(t *testing.T) {
	clearAllConfigEnv(t)
	setBaseValidEnv(t)
	t.Setenv("APP_ENV", "production")
	t.Setenv("JWT_ACCESS_SECRET", "prod_access_secret_with_at_least_32_characters")
	t.Setenv("JWT_REFRESH_SECRET", "prod_refresh_secret_with_at_least_32_characters")

	cfg, err := config.Load(filepath.Join(t.TempDir(), "missing.env"))
	if err != nil {
		t.Fatalf("expected valid production config, got: %v", err)
	}
	if !cfg.App.IsProduction() || cfg.App.IsDevelopment() {
		t.Errorf("expected IsProduction=true, IsDevelopment=false")
	}
}

func TestLoadMalformedEnvFileReturnsError(t *testing.T) {
	clearAllConfigEnv(t)
	setBaseValidEnv(t)

	badEnv := filepath.Join(t.TempDir(), "bad.env")
	if err := os.WriteFile(badEnv, []byte("INVALID LINE WITHOUT EQUALS \"unclosed\n"), 0o600); err != nil {
		t.Fatalf("write bad env: %v", err)
	}
	if _, err := config.Load(badEnv); err == nil {
		t.Fatalf("expected error for malformed .env file, got nil")
	}
}

func TestLoadDefaultNoArgsIgnoresMissingDotEnv(t *testing.T) {
	clearAllConfigEnv(t)
	setBaseValidEnv(t)

	origDir, err := os.Getwd()
	if err != nil {
		t.Fatalf("getwd: %v", err)
	}
	tmpDir := t.TempDir()
	if err := os.Chdir(tmpDir); err != nil {
		t.Fatalf("chdir: %v", err)
	}
	t.Cleanup(func() { _ = os.Chdir(origDir) })

	if _, err := config.Load(); err != nil {
		t.Fatalf("expected Load() with no args and missing .env to succeed, got: %v", err)
	}
}
