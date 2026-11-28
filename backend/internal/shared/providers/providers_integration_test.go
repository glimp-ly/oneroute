//go:build integration

package providers

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"log/slog"
	"net/http"
	"os"
	"strings"
	"testing"
	"time"

	"github.com/google/uuid"
)

type mailpitMessagesResponse struct {
	Total    int                     `json:"total"`
	Messages []mailpitMessageSummary `json:"messages"`
}

type mailpitMessageSummary struct {
	ID      string           `json:"ID"`
	Subject string           `json:"Subject"`
	Snippet string           `json:"Snippet"`
	To      []mailpitAddress `json:"To"`
}

type mailpitAddress struct {
	Address string `json:"Address"`
}

type mailpitMessageDetail struct {
	ID   string `json:"ID"`
	Text string `json:"Text"`
}

func TestMailpitConsoleOTPProvider_Integration(t *testing.T) {
	ctx, cancel := context.WithTimeout(context.Background(), 15*time.Second)
	defer cancel()

	smtpHost := envOrDefault("SMTP_HOST", "127.0.0.1")
	if smtpHost == "localhost" {
		smtpHost = "127.0.0.1"
	}
	smtpPort := envOrDefault("SMTP_PORT", "1025")
	mailpitAPI := envOrDefault("MAILPIT_API_URL", "http://127.0.0.1:8025")

	otpCode, err := GenerateNumericOTP(6)
	if err != nil {
		t.Fatalf("generate otp: %v", err)
	}

	recipient := fmt.Sprintf("otp-test-%s@rutacompartida.local", uuid.NewString()[:8])

	var logBuf bytes.Buffer
	logger := slog.New(slog.NewJSONHandler(&logBuf, nil))
	provider := NewMailpitConsoleOTPProvider(
		smtpHost,
		smtpPort,
		"no-reply@rutacompartida.local",
		"mailpit_console",
		logger,
	)

	err = provider.SendOTP(ctx, OTPMessage{
		Destination: recipient,
		Channel:     "correo",
		Purpose:     "verificacion_registro",
		Code:        otpCode,
		ExpiresIn:   10 * time.Minute,
	})
	if err != nil {
		t.Fatalf("SendOTP to Mailpit (%s:%s) failed: %v", smtpHost, smtpPort, err)
	}

	if !strings.Contains(logBuf.String(), "DEV_OTP_GENERATED") || !strings.Contains(logBuf.String(), otpCode) {
		t.Fatalf("expected structured log to contain DEV_OTP_GENERATED and OTP code, got: %s", logBuf.String())
	}

	httpClient := &http.Client{
		Transport: &http.Transport{Proxy: nil},
		Timeout:   5 * time.Second,
	}

	var matched *mailpitMessageSummary
	deadline := time.Now().Add(3 * time.Second)
	for time.Now().Before(deadline) {
		matched, err = findMailpitMessage(ctx, httpClient, mailpitAPI, recipient)
		if err == nil && matched != nil {
			break
		}
		time.Sleep(150 * time.Millisecond)
	}
	if err != nil {
		t.Fatalf("query Mailpit messages API failed: %v", err)
	}
	if matched == nil {
		t.Fatalf("message for recipient %s not found in Mailpit %s", recipient, mailpitAPI)
	}

	defer cleanupMailpitMessage(httpClient, mailpitAPI, matched.ID)

	detail, err := fetchMailpitMessageDetail(ctx, httpClient, mailpitAPI, matched.ID)
	if err != nil {
		t.Fatalf("fetch Mailpit message detail failed: %v", err)
	}
	if !strings.Contains(detail.Text, otpCode) {
		t.Fatalf("Mailpit message body did not contain OTP code %q: %s", otpCode, detail.Text)
	}
}

func findMailpitMessage(ctx context.Context, client *http.Client, baseURL, recipient string) (*mailpitMessageSummary, error) {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, strings.TrimRight(baseURL, "/")+"/api/v1/messages", nil)
	if err != nil {
		return nil, err
	}
	resp, err := client.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		body, _ := io.ReadAll(resp.Body)
		return nil, fmt.Errorf("unexpected Mailpit status %d: %s", resp.StatusCode, string(body))
	}

	var list mailpitMessagesResponse
	if err := json.NewDecoder(resp.Body).Decode(&list); err != nil {
		return nil, err
	}

	for i := range list.Messages {
		msg := &list.Messages[i]
		for _, to := range msg.To {
			if strings.EqualFold(to.Address, recipient) {
				return msg, nil
			}
		}
	}
	return nil, nil
}

func fetchMailpitMessageDetail(ctx context.Context, client *http.Client, baseURL, id string) (*mailpitMessageDetail, error) {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, strings.TrimRight(baseURL, "/")+"/api/v1/message/"+id, nil)
	if err != nil {
		return nil, err
	}
	resp, err := client.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		body, _ := io.ReadAll(resp.Body)
		return nil, fmt.Errorf("unexpected Mailpit detail status %d: %s", resp.StatusCode, string(body))
	}

	var detail mailpitMessageDetail
	if err := json.NewDecoder(resp.Body).Decode(&detail); err != nil {
		return nil, err
	}
	return &detail, nil
}

func cleanupMailpitMessage(client *http.Client, baseURL, id string) {
	payload := fmt.Sprintf(`{"IDs":[%q]}`, id)
	req, err := http.NewRequest(http.MethodDelete, strings.TrimRight(baseURL, "/")+"/api/v1/messages", strings.NewReader(payload))
	if err != nil {
		return
	}
	req.Header.Set("Content-Type", "application/json")
	resp, err := client.Do(req)
	if err == nil {
		_ = resp.Body.Close()
	}
}

func envOrDefault(key, fallback string) string {
	if val := strings.TrimSpace(os.Getenv(key)); val != "" {
		return val
	}
	return fallback
}
