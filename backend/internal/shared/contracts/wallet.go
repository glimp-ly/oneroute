// Package contracts defines the inter-module port interfaces and shared DTOs
// used to enforce module boundaries across the OneRoute backend.
package contracts

import (
	"context"
	"time"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5"
)

// WalletPort defines the cross-module operations exposed by payments_wallets.
type WalletPort interface {
	// EnsureUserWallets creates any missing wallets for the given user within tx.
	EnsureUserWallets(ctx context.Context, tx pgx.Tx, userID uuid.UUID) error
	// HasSufficientCommissionBalance checks whether the driver's commission wallet meets the minimum threshold to publish a trip.
	HasSufficientCommissionBalance(ctx context.Context, userID uuid.UUID) (bool, error)
	// CreditPassengerRefund immediately credits a refund to the passenger wallet and records an immutable ledger entry.
	CreditPassengerRefund(ctx context.Context, tx pgx.Tx, userID uuid.UUID, refType string, refID uuid.UUID, amount float64, desc string) error
	// CreditPassengerBalance credits the passenger wallet (used by the 24h delayed credit scheduler and refunds).
	CreditPassengerBalance(ctx context.Context, tx pgx.Tx, userID uuid.UUID, refType string, refID uuid.UUID, amount float64, desc string) error
	// ScheduleDelayedCredit registers a deferred refund credit (e.g. 24h hold on seat reduction) to be released at availableAt.
	ScheduleDelayedCredit(ctx context.Context, tx pgx.Tx, reservaID, modID, pagoID, userID uuid.UUID, amount float64, availableAt time.Time, reason string) error
	// SettleRidePayment settles a ride charge upon passenger boarding, applying promotions, wallet splits, and commissions.
	SettleRidePayment(ctx context.Context, tx pgx.Tx, cmd SettlePaymentCmd) (*SettlePaymentResult, error)
	// ProcessTipPayment processes an optional tip payment attached to a user rating.
	ProcessTipPayment(ctx context.Context, tx pgx.Tx, cmd TipCmd) (pagoID uuid.UUID, err error)
}

// SettlePaymentCmd carries the parameters required to settle a ride payment.
type SettlePaymentCmd struct {
	ReservaID        uuid.UUID `json:"reserva_id"`
	PasajeroID       uuid.UUID `json:"pasajero_id"`
	ConductorID      uuid.UUID `json:"conductor_id"`
	ViajeID          uuid.UUID `json:"viaje_id"`
	MetodoOriginal   string    `json:"metodo_original"`
	PromoCode        string    `json:"promo_code,omitempty"`
	Amount           float64   `json:"amount"`
	Currency         string    `json:"currency"`
	ClientEventID    uuid.UUID `json:"client_event_id"`
	SimulatedOutcome string    `json:"simulated_outcome,omitempty"`
}

// SettlePaymentResult summarizes the outcome and breakdown of a settled ride payment.
type SettlePaymentResult struct {
	PagoID         uuid.UUID `json:"pago_id"`
	Estado         string    `json:"estado"`
	MontoTotal     float64   `json:"monto_total"`
	MontoBilletera float64   `json:"monto_billetera"`
	MontoDigital   float64   `json:"monto_digital"`
	MontoEfectivo  float64   `json:"monto_efectivo"`
	ComisionMonto  float64   `json:"comision_monto"`
}

// TipCmd carries the parameters required to process a tip payment for a completed ride.
type TipCmd struct {
	ReservaID        uuid.UUID `json:"reserva_id"`
	EvaluadorID      uuid.UUID `json:"evaluador_id"`
	EvaluadoID       uuid.UUID `json:"evaluado_id"`
	Amount           float64   `json:"amount"`
	Metodo           string    `json:"metodo"`
	ClientEventID    uuid.UUID `json:"client_event_id"`
	SimulatedOutcome string    `json:"simulated_outcome,omitempty"`
}
