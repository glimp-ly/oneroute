package config

import (
	"errors"
	"fmt"
	"net/url"
	"os"
	"strconv"
	"strings"
	"time"

	"github.com/joho/godotenv"
)

// Config holds all validated runtime configuration loaded from environment variables.
type Config struct {
	App       AppConfig
	DB        DBConfig
	JWT       JWTConfig
	Storage   StorageConfig
	Routing   RoutingConfig
	Providers ProvidersConfig
	Scheduler SchedulerConfig
}

// AppConfig defines HTTP server, environment, localization, and CORS settings.
type AppConfig struct {
	Env                string
	Port               int
	BaseURL            string
	Timezone           string
	CORSAllowedOrigins []string
	CloudPublicAPIURL  string
}

// IsProduction reports whether the application is running in production mode.
func (a AppConfig) IsProduction() bool {
	return a.Env == "production"
}

// IsDevelopment reports whether the application is running in development mode.
func (a AppConfig) IsDevelopment() bool {
	return a.Env == "development"
}

// DBConfig holds PostgreSQL connection settings.
type DBConfig struct {
	Host        string
	Port        int
	Name        string
	User        string
	Password    string
	DatabaseURL string
}

// JWTConfig holds signing secrets and token lifetimes.
type JWTConfig struct {
	AccessSecret  string
	RefreshSecret string
	AccessTTL     time.Duration
	RefreshTTL    time.Duration
}

// StorageConfig holds MinIO / S3 object storage configuration.
type StorageConfig struct {
	Provider      string
	Endpoint      string
	PublicURL     string
	AccessKey     string
	SecretKey     string
	BucketPublic  string
	BucketPrivate string
	UseSSL        bool
	PresignedTTL  time.Duration
}

// RoutingConfig holds OSRM / mock routing provider settings.
type RoutingConfig struct {
	Provider     string
	OSRMBaseURL  string
	OSRMLocalURL string
	Timeout      time.Duration
}

// ProvidersConfig holds payment, identity, OTP, SMTP, and push notification settings.
type ProvidersConfig struct {
	PaymentProvider          string
	PaymentMockDefaultResult string
	IdentityProvider         string
	IdentityMockAutoApprove  bool
	OTPProvider              string
	SMTPHost                 string
	SMTPPort                 int
	SMTPUser                 string
	SMTPPassword             string
	SMTPFrom                 string
	OTPTTL                   time.Duration
	PushProvider             string
	FCMCredentialsJSON       string
}

// SchedulerConfig holds background worker intervals.
type SchedulerConfig struct {
	Enabled                  bool
	HoldPollInterval         time.Duration
	CreditsPollInterval      time.Duration
	RatingsPollInterval      time.Duration
	GPSRetentionPollInterval time.Duration
}

// Load reads optional .env files via godotenv (ignoring missing files),
// applies defaults for non-secret settings, and validates the resulting Config.
func Load(envPaths ...string) (*Config, error) {
	paths := envPaths
	if len(paths) == 0 {
		paths = []string{".env"}
	}
	for _, p := range paths {
		if err := godotenv.Load(p); err != nil && !errors.Is(err, os.ErrNotExist) {
			return nil, fmt.Errorf("config: load env file %q: %w", p, err)
		}
	}

	var errs []error
	cfg := &Config{}

	// App
	cfg.App.Env = strings.ToLower(getEnvDefault("APP_ENV", "development"))
	cfg.App.Port = parseIntEnv("APP_PORT", 8080, &errs)
	cfg.App.BaseURL = getEnvDefault("APP_BASE_URL", "http://localhost:8080")
	cfg.App.Timezone = getEnvDefault("APP_TIMEZONE", "America/Lima")
	cfg.App.CORSAllowedOrigins = parseCSVEnv("CORS_ALLOWED_ORIGINS", []string{"http://localhost:3000", "http://localhost:8080"})
	cfg.App.CloudPublicAPIURL = strings.TrimSpace(os.Getenv("CLOUD_PUBLIC_API_URL"))

	// DB
	cfg.DB.Host = strings.TrimSpace(os.Getenv("POSTGRES_HOST"))
	cfg.DB.Port = parseIntEnv("POSTGRES_PORT", 5432, &errs)
	cfg.DB.Name = strings.TrimSpace(os.Getenv("POSTGRES_DB"))
	cfg.DB.User = strings.TrimSpace(os.Getenv("POSTGRES_USER"))
	cfg.DB.Password = os.Getenv("POSTGRES_PASSWORD")
	cfg.DB.DatabaseURL = strings.TrimSpace(os.Getenv("DATABASE_URL"))

	// JWT
	cfg.JWT.AccessSecret = os.Getenv("JWT_ACCESS_SECRET")
	cfg.JWT.RefreshSecret = os.Getenv("JWT_REFRESH_SECRET")
	accessTTLMin := parseIntEnv("JWT_ACCESS_TTL_MINUTES", 15, &errs)
	refreshTTLDays := parseIntEnv("JWT_REFRESH_TTL_DAYS", 30, &errs)
	cfg.JWT.AccessTTL = time.Duration(accessTTLMin) * time.Minute
	cfg.JWT.RefreshTTL = time.Duration(refreshTTLDays) * 24 * time.Hour

	// Storage
	cfg.Storage.Provider = strings.ToLower(getEnvDefault("STORAGE_PROVIDER", "minio"))
	cfg.Storage.Endpoint = getEnvDefault("STORAGE_ENDPOINT", "localhost:9000")
	cfg.Storage.PublicURL = getEnvDefault("STORAGE_PUBLIC_URL", "http://localhost:9000")
	cfg.Storage.AccessKey = strings.TrimSpace(os.Getenv("STORAGE_ACCESS_KEY"))
	cfg.Storage.SecretKey = os.Getenv("STORAGE_SECRET_KEY")
	cfg.Storage.BucketPublic = getEnvDefault("STORAGE_BUCKET_PUBLIC", "rutacompartida-public")
	cfg.Storage.BucketPrivate = getEnvDefault("STORAGE_BUCKET_PRIVATE", "rutacompartida-private")
	cfg.Storage.UseSSL = parseBoolEnv("STORAGE_USE_SSL", false, &errs)
	presignedTTLSec := parseIntEnv("STORAGE_PRESIGNED_TTL_SECONDS", 300, &errs)
	cfg.Storage.PresignedTTL = time.Duration(presignedTTLSec) * time.Second

	// Routing
	cfg.Routing.Provider = strings.ToLower(getEnvDefault("ROUTING_PROVIDER", "osrm_public"))
	cfg.Routing.OSRMBaseURL = getEnvDefault("OSRM_BASE_URL", "https://router.project-osrm.org")
	cfg.Routing.OSRMLocalURL = getEnvDefault("OSRM_LOCAL_URL", "http://localhost:5000")
	osrmTimeoutSec := parseIntEnv("OSRM_TIMEOUT_SECONDS", 8, &errs)
	cfg.Routing.Timeout = time.Duration(osrmTimeoutSec) * time.Second

	// Providers
	cfg.Providers.PaymentProvider = strings.ToLower(getEnvDefault("PAYMENT_PROVIDER", "mock"))
	cfg.Providers.PaymentMockDefaultResult = strings.ToLower(getEnvDefault("PAYMENT_MOCK_DEFAULT_RESULT", "success"))
	cfg.Providers.IdentityProvider = strings.ToLower(getEnvDefault("IDENTITY_PROVIDER", "mock"))
	cfg.Providers.IdentityMockAutoApprove = parseBoolEnv("IDENTITY_MOCK_AUTO_APPROVE", false, &errs)
	cfg.Providers.OTPProvider = strings.ToLower(getEnvDefault("OTP_PROVIDER", "mailpit_console"))
	cfg.Providers.SMTPHost = getEnvDefault("SMTP_HOST", "localhost")
	cfg.Providers.SMTPPort = parseIntEnv("SMTP_PORT", 1025, &errs)
	cfg.Providers.SMTPUser = strings.TrimSpace(os.Getenv("SMTP_USER"))
	cfg.Providers.SMTPPassword = os.Getenv("SMTP_PASSWORD")
	cfg.Providers.SMTPFrom = getEnvDefault("SMTP_FROM", "no-reply@rutacompartida.local")
	otpTTLMin := parseIntEnv("OTP_TTL_MINUTES", 10, &errs)
	cfg.Providers.OTPTTL = time.Duration(otpTTLMin) * time.Minute
	cfg.Providers.PushProvider = strings.ToLower(getEnvDefault("PUSH_PROVIDER", "mock"))
	cfg.Providers.FCMCredentialsJSON = strings.TrimSpace(os.Getenv("FCM_CREDENTIALS_JSON"))

	// Scheduler
	cfg.Scheduler.Enabled = parseBoolEnv("SCHEDULER_ENABLED", true, &errs)
	holdPollSec := parseIntEnv("SCHEDULER_HOLD_POLL_SECONDS", 5, &errs)
	creditsPollSec := parseIntEnv("SCHEDULER_CREDITS_POLL_SECONDS", 60, &errs)
	ratingsPollSec := parseIntEnv("SCHEDULER_RATINGS_POLL_SECONDS", 60, &errs)
	gpsRetentionMin := parseIntEnv("SCHEDULER_GPS_RETENTION_POLL_MINUTES", 60, &errs)
	cfg.Scheduler.HoldPollInterval = time.Duration(holdPollSec) * time.Second
	cfg.Scheduler.CreditsPollInterval = time.Duration(creditsPollSec) * time.Second
	cfg.Scheduler.RatingsPollInterval = time.Duration(ratingsPollSec) * time.Second
	cfg.Scheduler.GPSRetentionPollInterval = time.Duration(gpsRetentionMin) * time.Minute

	validateConfig(cfg, &errs)
	if len(errs) > 0 {
		return nil, fmt.Errorf("config validation failed: %w", errors.Join(errs...))
	}

	return cfg, nil
}

func validateConfig(cfg *Config, errs *[]error) {
	if !isOneOf(cfg.App.Env, "development", "staging", "production", "test") {
		*errs = append(*errs, fmt.Errorf("APP_ENV %q must be one of development, staging, production, test", cfg.App.Env))
	}
	if cfg.App.Port < 1 || cfg.App.Port > 65535 {
		*errs = append(*errs, fmt.Errorf("APP_PORT %d must be between 1 and 65535", cfg.App.Port))
	}
	if cfg.App.Timezone == "" {
		*errs = append(*errs, errors.New("APP_TIMEZONE must not be empty"))
	} else if _, err := time.LoadLocation(cfg.App.Timezone); err != nil {
		*errs = append(*errs, fmt.Errorf("APP_TIMEZONE %q is invalid: %w", cfg.App.Timezone, err))
	}

	// Database validation and URL synthesis.
	if cfg.DB.DatabaseURL == "" {
		if cfg.DB.Host == "" {
			cfg.DB.Host = "localhost"
		}
		if cfg.DB.Port < 1 || cfg.DB.Port > 65535 {
			*errs = append(*errs, fmt.Errorf("POSTGRES_PORT %d must be between 1 and 65535", cfg.DB.Port))
		}
		if cfg.DB.Name == "" || cfg.DB.User == "" {
			*errs = append(*errs, errors.New("DATABASE_URL or POSTGRES_DB and POSTGRES_USER must be configured"))
		} else {
			var userInfo *url.Userinfo
			if cfg.DB.Password != "" {
				userInfo = url.UserPassword(cfg.DB.User, cfg.DB.Password)
			} else {
				userInfo = url.User(cfg.DB.User)
			}
			u := &url.URL{
				Scheme:   "postgres",
				User:     userInfo,
				Host:     fmt.Sprintf("%s:%d", cfg.DB.Host, cfg.DB.Port),
				Path:     "/" + cfg.DB.Name,
				RawQuery: "sslmode=disable",
			}
			cfg.DB.DatabaseURL = u.String()
		}
	} else {
		parsed, err := url.Parse(cfg.DB.DatabaseURL)
		if err != nil || (parsed.Scheme != "postgres" && parsed.Scheme != "postgresql") || parsed.Host == "" {
			*errs = append(*errs, errors.New("DATABASE_URL must be a valid postgres:// or postgresql:// URL"))
		}
	}

	// JWT secrets and TTLs.
	minSecretLen := 16
	if cfg.App.IsProduction() {
		minSecretLen = 32
	}
	if len(cfg.JWT.AccessSecret) < minSecretLen {
		*errs = append(*errs, fmt.Errorf("JWT_ACCESS_SECRET must be at least %d characters", minSecretLen))
	}
	if len(cfg.JWT.RefreshSecret) < minSecretLen {
		*errs = append(*errs, fmt.Errorf("JWT_REFRESH_SECRET must be at least %d characters", minSecretLen))
	}
	if cfg.JWT.AccessTTL <= 0 {
		*errs = append(*errs, errors.New("JWT_ACCESS_TTL_MINUTES must be positive"))
	}
	if cfg.JWT.RefreshTTL <= 0 {
		*errs = append(*errs, errors.New("JWT_REFRESH_TTL_DAYS must be positive"))
	}

	// Storage.
	if !isOneOf(cfg.Storage.Provider, "minio", "s3") {
		*errs = append(*errs, fmt.Errorf("STORAGE_PROVIDER %q must be minio or s3", cfg.Storage.Provider))
	}
	if cfg.Storage.PresignedTTL <= 0 {
		*errs = append(*errs, errors.New("STORAGE_PRESIGNED_TTL_SECONDS must be positive"))
	}

	// Routing.
	if !isOneOf(cfg.Routing.Provider, "osrm_public", "osrm_local", "mock") {
		*errs = append(*errs, fmt.Errorf("ROUTING_PROVIDER %q must be osrm_public, osrm_local, or mock", cfg.Routing.Provider))
	}
	if cfg.Routing.Timeout <= 0 {
		*errs = append(*errs, errors.New("OSRM_TIMEOUT_SECONDS must be positive"))
	}

	// Providers.
	if !isOneOf(cfg.Providers.PaymentProvider, "mock", "culqi", "mercadopago") {
		*errs = append(*errs, fmt.Errorf("PAYMENT_PROVIDER %q must be mock, culqi, or mercadopago", cfg.Providers.PaymentProvider))
	}
	if !isOneOf(cfg.Providers.PaymentMockDefaultResult, "success", "failed") {
		*errs = append(*errs, fmt.Errorf("PAYMENT_MOCK_DEFAULT_RESULT %q must be success or failed", cfg.Providers.PaymentMockDefaultResult))
	}
	if !isOneOf(cfg.Providers.IdentityProvider, "mock", "reniec_kyc") {
		*errs = append(*errs, fmt.Errorf("IDENTITY_PROVIDER %q must be mock or reniec_kyc", cfg.Providers.IdentityProvider))
	}
	if !isOneOf(cfg.Providers.OTPProvider, "mailpit_console", "smtp", "twilio") {
		*errs = append(*errs, fmt.Errorf("OTP_PROVIDER %q must be mailpit_console, smtp, or twilio", cfg.Providers.OTPProvider))
	}
	if cfg.Providers.SMTPPort < 1 || cfg.Providers.SMTPPort > 65535 {
		*errs = append(*errs, fmt.Errorf("SMTP_PORT %d must be between 1 and 65535", cfg.Providers.SMTPPort))
	}
	if !isOneOf(cfg.Providers.PushProvider, "mock", "fcm") {
		*errs = append(*errs, fmt.Errorf("PUSH_PROVIDER %q must be mock or fcm", cfg.Providers.PushProvider))
	}
	if cfg.Providers.OTPTTL <= 0 {
		*errs = append(*errs, errors.New("OTP_TTL_MINUTES must be positive"))
	}

	// Scheduler.
	if cfg.Scheduler.HoldPollInterval <= 0 {
		*errs = append(*errs, errors.New("SCHEDULER_HOLD_POLL_SECONDS must be positive"))
	}
	if cfg.Scheduler.CreditsPollInterval <= 0 {
		*errs = append(*errs, errors.New("SCHEDULER_CREDITS_POLL_SECONDS must be positive"))
	}
	if cfg.Scheduler.RatingsPollInterval <= 0 {
		*errs = append(*errs, errors.New("SCHEDULER_RATINGS_POLL_SECONDS must be positive"))
	}
	if cfg.Scheduler.GPSRetentionPollInterval <= 0 {
		*errs = append(*errs, errors.New("SCHEDULER_GPS_RETENTION_POLL_MINUTES must be positive"))
	}
}

func getEnvDefault(key, def string) string {
	val, ok := os.LookupEnv(key)
	if !ok || strings.TrimSpace(val) == "" {
		return def
	}
	return strings.TrimSpace(val)
}

func parseIntEnv(key string, def int, errs *[]error) int {
	val, ok := os.LookupEnv(key)
	if !ok || strings.TrimSpace(val) == "" {
		return def
	}
	n, err := strconv.Atoi(strings.TrimSpace(val))
	if err != nil {
		*errs = append(*errs, fmt.Errorf("%s=%q must be an integer: %w", key, val, err))
		return def
	}
	return n
}

func parseBoolEnv(key string, def bool, errs *[]error) bool {
	val, ok := os.LookupEnv(key)
	if !ok || strings.TrimSpace(val) == "" {
		return def
	}
	b, err := strconv.ParseBool(strings.TrimSpace(val))
	if err != nil {
		*errs = append(*errs, fmt.Errorf("%s=%q must be a boolean: %w", key, val, err))
		return def
	}
	return b
}

func parseCSVEnv(key string, def []string) []string {
	val, ok := os.LookupEnv(key)
	if !ok || strings.TrimSpace(val) == "" {
		out := make([]string, len(def))
		copy(out, def)
		return out
	}
	parts := strings.Split(val, ",")
	out := make([]string, 0, len(parts))
	for _, p := range parts {
		trimmed := strings.TrimSpace(p)
		if trimmed != "" {
			out = append(out, trimmed)
		}
	}
	return out
}

func isOneOf(val string, allowed ...string) bool {
	for _, a := range allowed {
		if val == a {
			return true
		}
	}
	return false
}
