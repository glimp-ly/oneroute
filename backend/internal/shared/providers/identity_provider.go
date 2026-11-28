package providers

import (
	"context"
	"fmt"
	"strings"

	"github.com/google/uuid"
)

// IdentityVerificationRequest represents an identity document verification request.
type IdentityVerificationRequest struct {
	UserID         uuid.UUID
	DocumentType   string
	DocumentNumber string
	CountryCode    string
}

// IdentityVerificationResult represents the decision returned by an identity provider.
type IdentityVerificationResult struct {
	Status          string
	Provider        string
	Reference       string
	RejectionReason string
}

// IdentityProvider abstracts user identity verification against mock or KYC/RENIEC backends.
type IdentityProvider interface {
	Verify(ctx context.Context, req IdentityVerificationRequest) (*IdentityVerificationResult, error)
}

// MockIdentityProvider validates document numbers deterministically for development and tests.
type MockIdentityProvider struct {
	AutoApprove bool
}

var _ IdentityProvider = (*MockIdentityProvider)(nil)

// NewMockIdentityProvider returns a MockIdentityProvider configured with auto-approval behavior.
func NewMockIdentityProvider(autoApprove bool) *MockIdentityProvider {
	return &MockIdentityProvider{AutoApprove: autoApprove}
}

// Verify validates the submitted identity document and returns aprobado, en_revision, or rechazado.
func (p *MockIdentityProvider) Verify(ctx context.Context, req IdentityVerificationRequest) (*IdentityVerificationResult, error) {
	if err := ctx.Err(); err != nil {
		return nil, err
	}

	docType := strings.ToUpper(strings.TrimSpace(req.DocumentType))
	if docType == "" {
		docType = "DNI"
	}
	docNumber := strings.TrimSpace(req.DocumentNumber)

	if docNumber == "00000000" {
		return &IdentityVerificationResult{
			Status:          "rechazado",
			Provider:        "mock",
			RejectionReason: "Documento invalido de prueba",
		}, nil
	}

	if docType == "DNI" {
		if !isValidDNI(docNumber) {
			return &IdentityVerificationResult{
				Status:          "rechazado",
				Provider:        "mock",
				RejectionReason: "DNI invalido: debe contener exactamente 8 digitos",
			}, nil
		}
	} else if docNumber == "" {
		return &IdentityVerificationResult{
			Status:          "rechazado",
			Provider:        "mock",
			RejectionReason: "Numero de documento requerido",
		}, nil
	}

	last4 := docNumber
	if len(docNumber) > 4 {
		last4 = docNumber[len(docNumber)-4:]
	}
	shortID := strings.ReplaceAll(uuid.NewString(), "-", "")[:8]
	ref := fmt.Sprintf("MOCK-%s-%s-%s", docType, last4, shortID)

	if p.AutoApprove {
		return &IdentityVerificationResult{
			Status:    "aprobado",
			Provider:  "mock",
			Reference: ref,
		}, nil
	}

	return &IdentityVerificationResult{
		Status:    "en_revision",
		Provider:  "mock",
		Reference: ref,
	}, nil
}

func isValidDNI(value string) bool {
	if len(value) != 8 {
		return false
	}
	for i := 0; i < len(value); i++ {
		if value[i] < '0' || value[i] > '9' {
			return false
		}
	}
	return true
}
