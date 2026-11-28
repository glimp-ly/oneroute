package providers

import (
	"context"
	"crypto/rand"
	"errors"
	"fmt"
	"log/slog"
	"math/big"
	"net"
	"net/smtp"
	"strings"
	"time"

	"golang.org/x/crypto/bcrypt"
)

const (
	defaultOTPDigits = 6
	otpBcryptCost    = 10
	defaultSMTPHost  = "127.0.0.1"
	defaultSMTPPort  = "1025"
	defaultSMTPFrom  = "no-reply@rutacompartida.local"
	smtpDialTimeout  = 5 * time.Second
)

// OTPMessage describes a one-time verification code delivery request.
type OTPMessage struct {
	Destination string
	Channel     string
	Purpose     string
	Code        string
	ExpiresIn   time.Duration
}

// OTPProvider abstracts sending one-time verification codes over email or SMS.
type OTPProvider interface {
	SendOTP(ctx context.Context, msg OTPMessage) error
}

// GenerateNumericOTP generates a cryptographically random numeric OTP of the requested length (defaults to 6).
func GenerateNumericOTP(digits int) (string, error) {
	if digits <= 0 {
		digits = defaultOTPDigits
	}

	buf := make([]byte, digits)
	ten := big.NewInt(10)
	for i := 0; i < digits; i++ {
		n, err := rand.Int(rand.Reader, ten)
		if err != nil {
			return "", fmt.Errorf("generate otp digit: %w", err)
		}
		buf[i] = '0' + byte(n.Int64())
	}
	return string(buf), nil
}

// HashOTP hashes an OTP code using bcrypt with cost 10.
func HashOTP(code string) (string, error) {
	if code == "" {
		return "", errors.New("otp code is required")
	}
	hashed, err := bcrypt.GenerateFromPassword([]byte(code), otpBcryptCost)
	if err != nil {
		return "", fmt.Errorf("hash otp: %w", err)
	}
	return string(hashed), nil
}

// VerifyOTP compares a bcrypt hash against a plaintext OTP code.
func VerifyOTP(hash, code string) bool {
	if hash == "" || code == "" {
		return false
	}
	return bcrypt.CompareHashAndPassword([]byte(hash), []byte(code)) == nil
}

// MailpitConsoleOTPProvider logs OTP codes in structured logs and sends emails via SMTP without auth for Mailpit.
type MailpitConsoleOTPProvider struct {
	SMTPHost string
	SMTPPort string
	From     string
	Mode     string
	Logger   *slog.Logger
}

var _ OTPProvider = (*MailpitConsoleOTPProvider)(nil)

// NewMailpitConsoleOTPProvider constructs a MailpitConsoleOTPProvider.
func NewMailpitConsoleOTPProvider(host, port, from, mode string, logger *slog.Logger) *MailpitConsoleOTPProvider {
	return &MailpitConsoleOTPProvider{
		SMTPHost: host,
		SMTPPort: port,
		From:     from,
		Mode:     mode,
		Logger:   logger,
	}
}

// SendOTP logs the generated OTP and delivers a plain-text SMTP email when Channel is "correo".
func (p *MailpitConsoleOTPProvider) SendOTP(ctx context.Context, msg OTPMessage) error {
	if err := ctx.Err(); err != nil {
		return err
	}

	dest := sanitizeHeaderValue(strings.TrimSpace(msg.Destination))
	if dest == "" {
		return errors.New("otp destination is required")
	}
	code := sanitizeHeaderValue(strings.TrimSpace(msg.Code))
	if code == "" {
		return errors.New("otp code is required")
	}

	channel := strings.ToLower(strings.TrimSpace(msg.Channel))
	purpose := sanitizeHeaderValue(strings.TrimSpace(msg.Purpose))

	logger := p.Logger
	if logger == nil {
		logger = slog.Default()
	}
	logger.Info(
		"DEV_OTP_GENERATED",
		"destino", dest,
		"channel", channel,
		"purpose", purpose,
		"code", code,
		"expires_in", msg.ExpiresIn.String(),
	)

	if channel != "correo" || strings.EqualFold(strings.TrimSpace(p.Mode), "console") {
		return nil
	}

	return p.sendPlainTextSMTP(ctx, dest, purpose, code, msg.ExpiresIn)
}

func (p *MailpitConsoleOTPProvider) sendPlainTextSMTP(ctx context.Context, to, purpose, code string, expiresIn time.Duration) error {
	host := strings.TrimSpace(p.SMTPHost)
	if host == "" {
		host = defaultSMTPHost
	}
	port := strings.TrimSpace(p.SMTPPort)
	if port == "" {
		port = defaultSMTPPort
	}
	from := sanitizeHeaderValue(strings.TrimSpace(p.From))
	if from == "" {
		from = defaultSMTPFrom
	}

	addr := net.JoinHostPort(host, port)
	dialer := &net.Dialer{Timeout: smtpDialTimeout}
	conn, err := dialer.DialContext(ctx, "tcp", addr)
	if err != nil {
		return fmt.Errorf("smtp dial %s: %w", addr, err)
	}

	client, err := smtp.NewClient(conn, host)
	if err != nil {
		_ = conn.Close()
		return fmt.Errorf("smtp client %s: %w", addr, err)
	}
	defer func() {
		_ = client.Close()
	}()

	if err := client.Mail(from); err != nil {
		return fmt.Errorf("smtp mail from: %w", err)
	}
	if err := client.Rcpt(to); err != nil {
		return fmt.Errorf("smtp rcpt to: %w", err)
	}

	writer, err := client.Data()
	if err != nil {
		return fmt.Errorf("smtp data: %w", err)
	}

	body := buildOTPEmailBody(from, to, purpose, code, expiresIn)
	if _, err := writer.Write([]byte(body)); err != nil {
		_ = writer.Close()
		return fmt.Errorf("smtp write body: %w", err)
	}
	if err := writer.Close(); err != nil {
		return fmt.Errorf("smtp close data: %w", err)
	}

	if err := client.Quit(); err != nil {
		return fmt.Errorf("smtp quit: %w", err)
	}
	return nil
}

func buildOTPEmailBody(from, to, purpose, code string, expiresIn time.Duration) string {
	var b strings.Builder
	b.WriteString("From: ")
	b.WriteString(from)
	b.WriteString("\r\n")
	b.WriteString("To: ")
	b.WriteString(to)
	b.WriteString("\r\n")
	b.WriteString("Subject: Codigo de verificacion OneRoute\r\n")
	b.WriteString("MIME-Version: 1.0\r\n")
	b.WriteString("Content-Type: text/plain; charset=UTF-8\r\n")
	b.WriteString("\r\n")
	b.WriteString("Tu codigo de verificacion OneRoute es: ")
	b.WriteString(code)
	b.WriteString("\r\n")
	if purpose != "" {
		b.WriteString("Proposito: ")
		b.WriteString(purpose)
		b.WriteString("\r\n")
	}
	if expiresIn > 0 {
		b.WriteString(fmt.Sprintf("Valido por: %d minutos.\r\n", max(1, int(expiresIn.Minutes()))))
	}
	return b.String()
}

func sanitizeHeaderValue(value string) string {
	r := strings.NewReplacer("\r", "", "\n", "")
	return r.Replace(value)
}
