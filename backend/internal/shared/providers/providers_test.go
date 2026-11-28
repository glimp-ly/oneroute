package providers

import (
	"bufio"
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"log/slog"
	"net"
	"strings"
	"sync"
	"testing"
	"time"

	"github.com/google/uuid"
	"golang.org/x/crypto/bcrypt"
)

func TestMockPaymentGateway_ChargeDigital(t *testing.T) {
	ctx := context.Background()

	tests := []struct {
		name           string
		defaultOutcome string
		req            ChargeRequest
		wantStatus     string
		wantReason     string
		wantRefPrefix  string
	}{
		{
			name:           "Given default success and normal amount When charging Then succeeds with MOCK-PAY reference",
			defaultOutcome: "success",
			req: ChargeRequest{
				PagoID:        uuid.New(),
				UsuarioID:     uuid.New(),
				Amount:        25.50,
				Currency:      "PEN",
				IdempotencyID: uuid.New(),
			},
			wantStatus:    "succeeded",
			wantRefPrefix: "MOCK-PAY-",
		},
		{
			name:           "Given default success and request simulated failed When charging Then fails with MOCK_CARD_DECLINED",
			defaultOutcome: "success",
			req: ChargeRequest{
				PagoID:           uuid.New(),
				UsuarioID:        uuid.New(),
				Amount:           25.50,
				Currency:         "PEN",
				IdempotencyID:    uuid.New(),
				SimulatedOutcome: "failed",
			},
			wantStatus: "failed",
			wantReason: "MOCK_CARD_DECLINED",
		},
		{
			name:           "Given default failed and empty simulated outcome When charging Then fails with MOCK_CARD_DECLINED",
			defaultOutcome: "failed",
			req: ChargeRequest{
				PagoID:        uuid.New(),
				UsuarioID:     uuid.New(),
				Amount:        18.00,
				Currency:      "PEN",
				IdempotencyID: uuid.New(),
			},
			wantStatus: "failed",
			wantReason: "MOCK_CARD_DECLINED",
		},
		{
			name:           "Given default failed and explicit simulated success When charging Then succeeds",
			defaultOutcome: "failed",
			req: ChargeRequest{
				PagoID:           uuid.New(),
				UsuarioID:        uuid.New(),
				Amount:           18.00,
				Currency:         "PEN",
				IdempotencyID:    uuid.New(),
				SimulatedOutcome: "success",
			},
			wantStatus:    "succeeded",
			wantRefPrefix: "MOCK-PAY-",
		},
		{
			name:           "Given amount ending in .99 When charging Then fails with MOCK_INSUFFICIENT_FUNDS",
			defaultOutcome: "success",
			req: ChargeRequest{
				PagoID:        uuid.New(),
				UsuarioID:     uuid.New(),
				Amount:        24.99,
				Currency:      "PEN",
				IdempotencyID: uuid.New(),
			},
			wantStatus: "failed",
			wantReason: "MOCK_INSUFFICIENT_FUNDS",
		},
		{
			name:           "Given small amount 0.99 When charging Then fails with MOCK_INSUFFICIENT_FUNDS",
			defaultOutcome: "success",
			req: ChargeRequest{
				PagoID:        uuid.New(),
				UsuarioID:     uuid.New(),
				Amount:        0.99,
				Currency:      "PEN",
				IdempotencyID: uuid.New(),
			},
			wantStatus: "failed",
			wantReason: "MOCK_INSUFFICIENT_FUNDS",
		},
		{
			name:           "Given simulated failed and amount ending in .99 When charging Then MOCK_CARD_DECLINED takes precedence",
			defaultOutcome: "success",
			req: ChargeRequest{
				PagoID:           uuid.New(),
				UsuarioID:        uuid.New(),
				Amount:           24.99,
				Currency:         "PEN",
				IdempotencyID:    uuid.New(),
				SimulatedOutcome: "failed",
			},
			wantStatus: "failed",
			wantReason: "MOCK_CARD_DECLINED",
		},
	}

	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			gw := NewMockPaymentGateway(tc.defaultOutcome)
			res, err := gw.ChargeDigital(ctx, tc.req)
			if err != nil {
				t.Fatalf("unexpected error: %v", err)
			}
			if res.Status != tc.wantStatus {
				t.Fatalf("status = %q, want %q", res.Status, tc.wantStatus)
			}
			if res.FailureReason != tc.wantReason {
				t.Fatalf("failure reason = %q, want %q", res.FailureReason, tc.wantReason)
			}
			if tc.wantRefPrefix != "" {
				if !strings.HasPrefix(res.ExternalRef, tc.wantRefPrefix) {
					t.Fatalf("external ref = %q, want prefix %q", res.ExternalRef, tc.wantRefPrefix)
				}
				rawUUID := strings.TrimPrefix(res.ExternalRef, tc.wantRefPrefix)
				if _, err := uuid.Parse(rawUUID); err != nil {
					t.Fatalf("external ref suffix %q is not a valid UUID: %v", rawUUID, err)
				}
			} else if res.ExternalRef != "" {
				t.Fatalf("external ref = %q, want empty on failure", res.ExternalRef)
			}
		})
	}
}

func TestMockPaymentGateway_RefundAndWithdrawal(t *testing.T) {
	ctx := context.Background()
	gw := NewMockPaymentGateway("success")

	t.Run("Given valid refund request When refunding Then succeeds with MOCK-REF reference", func(t *testing.T) {
		res, err := gw.RefundDigital(ctx, "MOCK-PAY-123", 15.00)
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		if res.Status != "succeeded" {
			t.Fatalf("status = %q, want succeeded", res.Status)
		}
		if !strings.HasPrefix(res.ExternalRef, "MOCK-REF-") {
			t.Fatalf("external ref = %q, want MOCK-REF- prefix", res.ExternalRef)
		}
	})

	t.Run("Given valid withdrawal request When disbursing Then succeeds with MOCK-WDR reference", func(t *testing.T) {
		res, err := gw.DisburseWithdrawal(ctx, WithdrawalRequest{
			RetiroID:      uuid.New(),
			UsuarioID:     uuid.New(),
			Amount:        80.00,
			Currency:      "PEN",
			Method:        "yape",
			Bank:          "BCP",
			AccountMasked: "999***123",
		})
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		if res.Status != "succeeded" {
			t.Fatalf("status = %q, want succeeded", res.Status)
		}
		if !strings.HasPrefix(res.ExternalRef, "MOCK-WDR-") {
			t.Fatalf("external ref = %q, want MOCK-WDR- prefix", res.ExternalRef)
		}
	})

	t.Run("Given cancelled context When calling payment methods Then returns context error", func(t *testing.T) {
		cancelledCtx, cancel := context.WithCancel(ctx)
		cancel()

		if _, err := gw.ChargeDigital(cancelledCtx, ChargeRequest{}); err == nil {
			t.Fatal("expected error for cancelled context in ChargeDigital")
		}
		if _, err := gw.RefundDigital(cancelledCtx, "MOCK-PAY-1", 10); err == nil {
			t.Fatal("expected error for cancelled context in RefundDigital")
		}
		if _, err := gw.DisburseWithdrawal(cancelledCtx, WithdrawalRequest{}); err == nil {
			t.Fatal("expected error for cancelled context in DisburseWithdrawal")
		}
	})
}

func TestMockIdentityProvider_Verify(t *testing.T) {
	ctx := context.Background()

	tests := []struct {
		name          string
		autoApprove   bool
		req           IdentityVerificationRequest
		wantStatus    string
		wantRefPrefix string
		wantRejected  bool
	}{
		{
			name:        "Given test negative DNI 00000000 with auto approve When verifying Then rejects",
			autoApprove: true,
			req: IdentityVerificationRequest{
				UserID:         uuid.New(),
				DocumentType:   "DNI",
				DocumentNumber: "00000000",
				CountryCode:    "PE",
			},
			wantStatus:   "rechazado",
			wantRejected: true,
		},
		{
			name:        "Given short DNI When verifying Then rejects",
			autoApprove: true,
			req: IdentityVerificationRequest{
				UserID:         uuid.New(),
				DocumentType:   "DNI",
				DocumentNumber: "1234567",
			},
			wantStatus:   "rechazado",
			wantRejected: true,
		},
		{
			name:        "Given non-numeric DNI When verifying Then rejects",
			autoApprove: true,
			req: IdentityVerificationRequest{
				UserID:         uuid.New(),
				DocumentType:   "DNI",
				DocumentNumber: "1234567A",
			},
			wantStatus:   "rechazado",
			wantRejected: true,
		},
		{
			name:        "Given empty document number with default type When verifying Then rejects",
			autoApprove: false,
			req: IdentityVerificationRequest{
				UserID: uuid.New(),
			},
			wantStatus:   "rechazado",
			wantRejected: true,
		},
		{
			name:        "Given valid 8-digit DNI and AutoApprove true When verifying Then approves with MOCK-DNI reference",
			autoApprove: true,
			req: IdentityVerificationRequest{
				UserID:         uuid.New(),
				DocumentType:   "DNI",
				DocumentNumber: "45678912",
				CountryCode:    "PE",
			},
			wantStatus:    "aprobado",
			wantRefPrefix: "MOCK-DNI-8912-",
		},
		{
			name:        "Given valid 8-digit DNI and empty DocumentType with AutoApprove true When verifying Then defaults to DNI and approves",
			autoApprove: true,
			req: IdentityVerificationRequest{
				UserID:         uuid.New(),
				DocumentNumber: "71234567",
			},
			wantStatus:    "aprobado",
			wantRefPrefix: "MOCK-DNI-4567-",
		},
		{
			name:        "Given valid 8-digit DNI and AutoApprove false When verifying Then returns en_revision",
			autoApprove: false,
			req: IdentityVerificationRequest{
				UserID:         uuid.New(),
				DocumentType:   "DNI",
				DocumentNumber: "45678912",
				CountryCode:    "PE",
			},
			wantStatus:    "en_revision",
			wantRefPrefix: "MOCK-DNI-8912-",
		},
		{
			name:        "Given valid CE document and AutoApprove true When verifying Then approves",
			autoApprove: true,
			req: IdentityVerificationRequest{
				UserID:         uuid.New(),
				DocumentType:   "CE",
				DocumentNumber: "001234567",
				CountryCode:    "PE",
			},
			wantStatus:    "aprobado",
			wantRefPrefix: "MOCK-CE-4567-",
		},
	}

	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			provider := NewMockIdentityProvider(tc.autoApprove)
			res, err := provider.Verify(ctx, tc.req)
			if err != nil {
				t.Fatalf("unexpected error: %v", err)
			}
			if res.Status != tc.wantStatus {
				t.Fatalf("status = %q, want %q", res.Status, tc.wantStatus)
			}
			if res.Provider != "mock" {
				t.Fatalf("provider = %q, want mock", res.Provider)
			}
			if tc.wantRejected {
				if res.RejectionReason == "" {
					t.Fatal("expected non-empty rejection reason")
				}
				if res.Reference != "" {
					t.Fatalf("expected empty reference on rejection, got %q", res.Reference)
				}
			} else {
				if res.RejectionReason != "" {
					t.Fatalf("unexpected rejection reason %q", res.RejectionReason)
				}
				if !strings.HasPrefix(res.Reference, tc.wantRefPrefix) {
					t.Fatalf("reference = %q, want prefix %q", res.Reference, tc.wantRefPrefix)
				}
			}
		})
	}

	t.Run("Given cancelled context When verifying Then returns context error", func(t *testing.T) {
		cancelledCtx, cancel := context.WithCancel(ctx)
		cancel()
		provider := NewMockIdentityProvider(true)
		if _, err := provider.Verify(cancelledCtx, IdentityVerificationRequest{DocumentNumber: "45678912"}); err == nil {
			t.Fatal("expected error for cancelled context")
		}
	})
}

func TestOTPHelpers(t *testing.T) {
	t.Run("Given zero or negative digits When generating OTP Then defaults to 6 numeric digits", func(t *testing.T) {
		for _, digits := range []int{0, -3, 6} {
			code, err := GenerateNumericOTP(digits)
			if err != nil {
				t.Fatalf("digits=%d unexpected error: %v", digits, err)
			}
			if len(code) != 6 {
				t.Fatalf("digits=%d len(code) = %d, want 6", digits, len(code))
			}
			for i := 0; i < len(code); i++ {
				if code[i] < '0' || code[i] > '9' {
					t.Fatalf("non-digit char %q in code %q", code[i], code)
				}
			}
		}
	})

	t.Run("Given 8 digits When generating OTP Then returns 8 digits with randomness", func(t *testing.T) {
		seen := make(map[string]struct{})
		for i := 0; i < 20; i++ {
			code, err := GenerateNumericOTP(8)
			if err != nil {
				t.Fatalf("unexpected error: %v", err)
			}
			if len(code) != 8 {
				t.Fatalf("len(code) = %d, want 8", len(code))
			}
			seen[code] = struct{}{}
		}
		if len(seen) < 2 {
			t.Fatalf("expected multiple distinct OTP values, got %d", len(seen))
		}
	})

	t.Run("Given OTP code When hashing and verifying Then uses bcrypt cost 10", func(t *testing.T) {
		code := "482910"
		hash, err := HashOTP(code)
		if err != nil {
			t.Fatalf("unexpected hash error: %v", err)
		}
		cost, err := bcrypt.Cost([]byte(hash))
		if err != nil {
			t.Fatalf("unexpected bcrypt cost error: %v", err)
		}
		if cost != 10 {
			t.Fatalf("bcrypt cost = %d, want 10", cost)
		}
		if !VerifyOTP(hash, code) {
			t.Fatal("expected VerifyOTP to return true for matching code")
		}
		if VerifyOTP(hash, "000000") {
			t.Fatal("expected VerifyOTP to return false for wrong code")
		}
		if VerifyOTP("", code) || VerifyOTP(hash, "") {
			t.Fatal("expected VerifyOTP to return false for empty inputs")
		}
	})

	t.Run("Given empty OTP code When hashing Then returns error", func(t *testing.T) {
		if _, err := HashOTP(""); err == nil {
			t.Fatal("expected error for empty OTP code")
		}
	})
}

func TestMailpitConsoleOTPProvider_SendOTP(t *testing.T) {
	ctx := context.Background()

	t.Run("Given SMS channel When sending OTP Then logs DEV_OTP_GENERATED without dialing SMTP", func(t *testing.T) {
		var logBuf bytes.Buffer
		logger := slog.New(slog.NewJSONHandler(&logBuf, nil))

		provider := NewMailpitConsoleOTPProvider("127.0.0.1", "1", "no-reply@rutacompartida.local", "mailpit_console", logger)
		err := provider.SendOTP(ctx, OTPMessage{
			Destination: "+51987654321",
			Channel:     "sms",
			Purpose:     "verificacion_telefono",
			Code:        "654321",
			ExpiresIn:   10 * time.Minute,
		})
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}

		var entry map[string]any
		if err := json.Unmarshal(logBuf.Bytes(), &entry); err != nil {
			t.Fatalf("failed to parse structured log JSON: %v (raw: %s)", err, logBuf.String())
		}
		if entry["msg"] != "DEV_OTP_GENERATED" {
			t.Fatalf("log msg = %v, want DEV_OTP_GENERATED", entry["msg"])
		}
		if entry["destino"] != "+51987654321" {
			t.Fatalf("log destino = %v, want +51987654321", entry["destino"])
		}
		if entry["code"] != "654321" {
			t.Fatalf("log code = %v, want 654321", entry["code"])
		}
	})

	t.Run("Given correo channel and local SMTP server When sending OTP Then logs and delivers plain-text email without auth", func(t *testing.T) {
		captured, addr, shutdown := startTestSMTPServer(t)
		defer shutdown()

		host, port, err := net.SplitHostPort(addr)
		if err != nil {
			t.Fatalf("split host port: %v", err)
		}

		var logBuf bytes.Buffer
		logger := slog.New(slog.NewJSONHandler(&logBuf, nil))

		provider := NewMailpitConsoleOTPProvider(host, port, "no-reply@rutacompartida.local", "mailpit_console", logger)
		err = provider.SendOTP(ctx, OTPMessage{
			Destination: "pasajero@rutacompartida.pe",
			Channel:     "correo",
			Purpose:     "registro",
			Code:        "918273",
			ExpiresIn:   10 * time.Minute,
		})
		if err != nil {
			t.Fatalf("unexpected SendOTP error: %v", err)
		}

		if !strings.Contains(logBuf.String(), "DEV_OTP_GENERATED") || !strings.Contains(logBuf.String(), "918273") {
			t.Fatalf("expected structured log to contain DEV_OTP_GENERATED and OTP code, got: %s", logBuf.String())
		}

		msg := captured.snapshot()
		if msg.mailFrom != "<no-reply@rutacompartida.local>" {
			t.Fatalf("MAIL FROM = %q, want <no-reply@rutacompartida.local>", msg.mailFrom)
		}
		if msg.rcptTo != "<pasajero@rutacompartida.pe>" {
			t.Fatalf("RCPT TO = %q, want <pasajero@rutacompartida.pe>", msg.rcptTo)
		}
		if !strings.Contains(msg.body, "918273") {
			t.Fatalf("SMTP body did not contain OTP code 918273: %s", msg.body)
		}
		if !strings.Contains(msg.body, "Subject: Codigo de verificacion OneRoute") {
			t.Fatalf("SMTP body missing expected subject: %s", msg.body)
		}
	})

	t.Run("Given missing destination or code or cancelled context When sending OTP Then returns error", func(t *testing.T) {
		provider := NewMailpitConsoleOTPProvider("127.0.0.1", "1025", "", "console", nil)
		if err := provider.SendOTP(ctx, OTPMessage{Channel: "sms", Code: "123456"}); err == nil {
			t.Fatal("expected error for missing destination")
		}
		if err := provider.SendOTP(ctx, OTPMessage{Destination: "a@b.pe", Channel: "sms"}); err == nil {
			t.Fatal("expected error for missing code")
		}

		cancelledCtx, cancel := context.WithCancel(ctx)
		cancel()
		if err := provider.SendOTP(cancelledCtx, OTPMessage{Destination: "a@b.pe", Channel: "sms", Code: "123456"}); err == nil {
			t.Fatal("expected error for cancelled context")
		}
	})
}

type capturedSMTPMessage struct {
	mu       sync.Mutex
	mailFrom string
	rcptTo   string
	body     string
}

func (c *capturedSMTPMessage) setFrom(v string) {
	c.mu.Lock()
	defer c.mu.Unlock()
	c.mailFrom = v
}

func (c *capturedSMTPMessage) setTo(v string) {
	c.mu.Lock()
	defer c.mu.Unlock()
	c.rcptTo = v
}

func (c *capturedSMTPMessage) setBody(v string) {
	c.mu.Lock()
	defer c.mu.Unlock()
	c.body = v
}

func (c *capturedSMTPMessage) snapshot() (out struct {
	mailFrom string
	rcptTo   string
	body     string
}) {
	c.mu.Lock()
	defer c.mu.Unlock()
	out.mailFrom = c.mailFrom
	out.rcptTo = c.rcptTo
	out.body = c.body
	return out
}

func startTestSMTPServer(t *testing.T) (*capturedSMTPMessage, string, func()) {
	t.Helper()

	ln, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatalf("listen tcp: %v", err)
	}

	captured := &capturedSMTPMessage{}
	var wg sync.WaitGroup
	wg.Add(1)

	go func() {
		defer wg.Done()
		conn, err := ln.Accept()
		if err != nil {
			return
		}
		defer conn.Close()

		reader := bufio.NewReader(conn)
		_, _ = conn.Write([]byte("220 localhost ESMTP Mock\r\n"))

		for {
			line, err := reader.ReadString('\n')
			if err != nil {
				return
			}
			trimmed := strings.TrimRight(line, "\r\n")
			upper := strings.ToUpper(trimmed)

			switch {
			case strings.HasPrefix(upper, "EHLO"):
				_, _ = conn.Write([]byte("250-localhost\r\n250 OK\r\n"))
			case strings.HasPrefix(upper, "HELO"):
				_, _ = conn.Write([]byte("250 OK\r\n"))
			case strings.HasPrefix(upper, "MAIL FROM:"):
				captured.setFrom(strings.TrimSpace(trimmed[len("MAIL FROM:"):]))
				_, _ = conn.Write([]byte("250 OK\r\n"))
			case strings.HasPrefix(upper, "RCPT TO:"):
				captured.setTo(strings.TrimSpace(trimmed[len("RCPT TO:"):]))
				_, _ = conn.Write([]byte("250 OK\r\n"))
			case upper == "DATA":
				_, _ = conn.Write([]byte("354 End data with <CR><LF>.<CR><LF>\r\n"))
				var bodyLines []string
				for {
					dataLine, err := reader.ReadString('\n')
					if err != nil {
						return
					}
					clean := strings.TrimRight(dataLine, "\r\n")
					if clean == "." {
						break
					}
					bodyLines = append(bodyLines, clean)
				}
				captured.setBody(strings.Join(bodyLines, "\n"))
				_, _ = conn.Write([]byte("250 OK\r\n"))
			case upper == "QUIT":
				_, _ = conn.Write([]byte("221 Bye\r\n"))
				return
			default:
				_, _ = conn.Write([]byte(fmt.Sprintf("250 OK (%s)\r\n", upper)))
			}
		}
	}()

	shutdown := func() {
		_ = ln.Close()
		wg.Wait()
	}

	return captured, ln.Addr().String(), shutdown
}
