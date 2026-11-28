package providers

import (
	"context"
	"fmt"
	"math"
	"strings"

	"github.com/google/uuid"
)

// ChargeRequest describes a digital payment charge request.
type ChargeRequest struct {
	PagoID           uuid.UUID
	UsuarioID        uuid.UUID
	Amount           float64
	Currency         string
	IdempotencyID    uuid.UUID
	SimulatedOutcome string
}

// ChargeResult describes the outcome of a digital charge attempt.
type ChargeResult struct {
	Status        string
	ExternalRef   string
	FailureReason string
}

// RefundResult describes the outcome of a digital refund attempt.
type RefundResult struct {
	Status        string
	ExternalRef   string
	FailureReason string
}

// WithdrawalRequest describes a payout disbursement request to a driver or user account.
type WithdrawalRequest struct {
	RetiroID      uuid.UUID
	UsuarioID     uuid.UUID
	Amount        float64
	Currency      string
	Method        string
	Bank          string
	AccountMasked string
}

// WithdrawalResult describes the outcome of a withdrawal disbursement.
type WithdrawalResult struct {
	Status        string
	ExternalRef   string
	FailureReason string
}

// PaymentGateway abstracts external digital charges, refunds, and withdrawal disbursements.
type PaymentGateway interface {
	ChargeDigital(ctx context.Context, req ChargeRequest) (*ChargeResult, error)
	RefundDigital(ctx context.Context, externalRef string, amount float64) (*RefundResult, error)
	DisburseWithdrawal(ctx context.Context, req WithdrawalRequest) (*WithdrawalResult, error)
}

// MockPaymentGateway provides deterministic payment outcomes for local development and tests.
type MockPaymentGateway struct {
	DefaultOutcome string
}

var _ PaymentGateway = (*MockPaymentGateway)(nil)

// NewMockPaymentGateway returns a MockPaymentGateway configured with "success" or "failed" as default outcome.
func NewMockPaymentGateway(defaultOutcome string) *MockPaymentGateway {
	normalized := strings.ToLower(strings.TrimSpace(defaultOutcome))
	if normalized != "failed" {
		normalized = "success"
	}
	return &MockPaymentGateway{DefaultOutcome: normalized}
}

// ChargeDigital simulates a digital payment using deterministic rules for dev and QA flows.
func (g *MockPaymentGateway) ChargeDigital(ctx context.Context, req ChargeRequest) (*ChargeResult, error) {
	if err := ctx.Err(); err != nil {
		return nil, err
	}

	reqOutcome := strings.ToLower(strings.TrimSpace(req.SimulatedOutcome))
	defaultOutcome := strings.ToLower(strings.TrimSpace(g.DefaultOutcome))

	if reqOutcome == "failed" || (reqOutcome == "" && defaultOutcome == "failed") {
		return &ChargeResult{
			Status:        "failed",
			FailureReason: "MOCK_CARD_DECLINED",
		}, nil
	}

	cents := int64(math.Round(req.Amount * 100))
	if cents >= 0 && cents%100 == 99 {
		return &ChargeResult{
			Status:        "failed",
			FailureReason: "MOCK_INSUFFICIENT_FUNDS",
		}, nil
	}

	return &ChargeResult{
		Status:      "succeeded",
		ExternalRef: fmt.Sprintf("MOCK-PAY-%s", uuid.NewString()),
	}, nil
}

// RefundDigital simulates a digital payment refund.
func (g *MockPaymentGateway) RefundDigital(ctx context.Context, externalRef string, amount float64) (*RefundResult, error) {
	if err := ctx.Err(); err != nil {
		return nil, err
	}

	return &RefundResult{
		Status:      "succeeded",
		ExternalRef: fmt.Sprintf("MOCK-REF-%s", uuid.NewString()),
	}, nil
}

// DisburseWithdrawal simulates disbursing a withdrawal to a bank account or digital wallet.
func (g *MockPaymentGateway) DisburseWithdrawal(ctx context.Context, req WithdrawalRequest) (*WithdrawalResult, error) {
	if err := ctx.Err(); err != nil {
		return nil, err
	}

	return &WithdrawalResult{
		Status:      "succeeded",
		ExternalRef: fmt.Sprintf("MOCK-WDR-%s", uuid.NewString()),
	}, nil
}
