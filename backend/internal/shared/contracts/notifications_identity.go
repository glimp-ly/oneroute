package contracts

import (
	"context"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5"
)

// NotificationPort defines the cross-module operations exposed by messaging_safety_support.
type NotificationPort interface {
	// Notify persists an in-app notification and optionally dispatches a push/WebSocket alert.
	Notify(ctx context.Context, cmd NotifyCmd) error
	// CreateReservationChat initializes the private chat channel for a confirmed reservation within tx.
	CreateReservationChat(ctx context.Context, tx pgx.Tx, reservaID uuid.UUID) error
	// CloseReservationChat closes the private chat channel for a finished or cancelled reservation within tx.
	CloseReservationChat(ctx context.Context, tx pgx.Tx, reservaID uuid.UUID) error
}

// NotifyCmd carries the payload for dispatching a user notification.
type NotifyCmd struct {
	UserID   uuid.UUID      `json:"user_id"`
	Category string         `json:"category"`
	Title    string         `json:"title"`
	Message  string         `json:"message"`
	Priority string         `json:"priority"`
	Data     map[string]any `json:"data,omitempty"`
	SendPush bool           `json:"send_push"`
}

// IdentityPort defines the cross-module operations exposed by auth_identity.
type IdentityPort interface {
	// GetUserSummary returns a compact user profile, verification status, rating summary, and active roles.
	GetUserSummary(ctx context.Context, userID uuid.UUID) (*UserSummary, error)
	// GetEmergencyContacts returns the user's configured emergency contacts ordered by priority.
	GetEmergencyContacts(ctx context.Context, userID uuid.UUID) ([]EmergencyContactDTO, error)
	// RecalculateRatingStats updates aggregate rating averages and trip counts for the evaluated role within tx.
	RecalculateRatingStats(ctx context.Context, tx pgx.Tx, userID uuid.UUID, rolEvaluado string) error
}

// UserSummary represents the shared public/cross-module profile summary of a user.
type UserSummary struct {
	ID                    uuid.UUID `json:"id"`
	Nombres               string    `json:"nombres"`
	Apellidos             string    `json:"apellidos"`
	Correo                string    `json:"correo"`
	Telefono              string    `json:"telefono"`
	IdentidadVerificada   bool      `json:"identidad_verificada"`
	DriverApproved        bool      `json:"driver_approved"`
	CalificacionPasajero  float64   `json:"calificacion_pasajero"`
	CalificacionConductor float64   `json:"calificacion_conductor"`
	TotalViajesPasajero   int       `json:"total_viajes_pasajero"`
	TotalViajesConductor  int       `json:"total_viajes_conductor"`
	Roles                 []string  `json:"roles"`
}

// EmergencyContactDTO represents an emergency contact belonging to a user.
type EmergencyContactDTO struct {
	ID          uuid.UUID `json:"id"`
	Nombre      string    `json:"nombre"`
	Parentesco  string    `json:"parentesco"`
	Telefono    string    `json:"telefono"`
	EsPrincipal bool      `json:"es_principal"`
}
