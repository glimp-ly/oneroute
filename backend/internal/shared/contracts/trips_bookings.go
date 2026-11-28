package contracts

import (
	"context"

	"github.com/glimp-ly/oneroute/backend/internal/shared/geo"
	"github.com/google/uuid"
	"github.com/jackc/pgx/v5"
)

// TripPort defines the cross-module operations exposed by routes_trips.
type TripPort interface {
	// GetTripForBooking returns a snapshot of the trip state, pricing, and operational route for booking workflows.
	GetTripForBooking(ctx context.Context, viajeID uuid.UUID) (*TripSnapshot, error)
	// UpdateOperationalRoute updates the trip's operational PostGIS linestring EWKT and distance in kilometers.
	UpdateOperationalRoute(ctx context.Context, tx pgx.Tx, viajeID uuid.UUID, newGeoEWKT string, newDistKm float64) error
	// AddOperationalStop inserts a new operational stop on the trip and returns its generated UUID.
	AddOperationalStop(ctx context.Context, tx pgx.Tx, stop AddStopCmd) (uuid.UUID, error)
	// CancelAllTripBookingsTrigger triggers cancellation of all active reservations associated with a cancelled trip.
	CancelAllTripBookingsTrigger(ctx context.Context, tx pgx.Tx, viajeID, actorID uuid.UUID, reason string) error
}

// TripSnapshot provides a read-only projection of a trip needed by search_bookings and active_trip_gps.
type TripSnapshot struct {
	ViajeID               uuid.UUID `json:"viaje_id"`
	ConductorID           uuid.UUID `json:"conductor_id"`
	Estado                string    `json:"estado"`
	CapacidadTotal        int       `json:"capacidad_total"`
	AceptaReservas        bool      `json:"acepta_reservas"`
	AceptacionAutomatica  bool      `json:"aceptacion_automatica"`
	GPSConfiable          bool      `json:"gps_confiable"`
	FraccionAvanceRuta    float64   `json:"fraccion_avance_ruta"`
	TipoTarifa            string    `json:"tipo_tarifa"`
	PrecioAsientoCompleto float64   `json:"precio_asiento_completo"`
	TarifaKm              float64   `json:"tarifa_km"`
	DesvioMaximoKm        float64   `json:"desvio_maximo_km"`
	DistanciaOperativaKm  float64   `json:"distancia_operativa_km"`
	RutaOperativaEWKT     string    `json:"ruta_operativa_ewkt"`
}

// AddStopCmd carries the parameters required to insert an operational stop into viaje_parada.
type AddStopCmd struct {
	ViajeID              uuid.UUID `json:"viaje_id"`
	Tipo                 string    `json:"tipo"`
	Nombre               string    `json:"nombre"`
	Ubicacion            geo.Point `json:"ubicacion"`
	FraccionRuta         float64   `json:"fraccion_ruta"`
	DistanciaAcumuladaKm float64   `json:"distancia_acumulada_km"`
	DesvioKm             float64   `json:"desvio_km"`
}

// BookingPort defines the cross-module operations exposed by search_bookings.
type BookingPort interface {
	// CancelReservationsByTrip cancels all active reservations of a trip and returns metadata for refund processing.
	CancelReservationsByTrip(ctx context.Context, tx pgx.Tx, viajeID, actorID uuid.UUID, reason string) ([]CancelledBookingInfo, error)
	// CompleteEarlyByPassenger finalizes a reservation early at the passenger's real dropoff point with recalculated metrics.
	CompleteEarlyByPassenger(ctx context.Context, tx pgx.Tx, reservaID, userID uuid.UUID, dropoff geo.Point, newFrac, newDistKm, newPrice float64) error
	// MarkReservationNoShow marks an unboarded reservation as no_show and releases its reserved segments.
	MarkReservationNoShow(ctx context.Context, tx pgx.Tx, reservaID, driverID uuid.UUID) error
	// MarkReservationBoarded transitions a confirmed reservation to en_curso when the driver confirms pickup.
	MarkReservationBoarded(ctx context.Context, tx pgx.Tx, reservaID, driverID uuid.UUID) error
	// MarkReservationCompleted transitions an active reservation to completada upon trip or segment completion.
	MarkReservationCompleted(ctx context.Context, tx pgx.Tx, reservaID uuid.UUID) error
}

// CancelledBookingInfo describes a reservation cancelled as part of a trip-level cancellation.
type CancelledBookingInfo struct {
	ReservaID      uuid.UUID `json:"reserva_id"`
	PasajeroID     uuid.UUID `json:"pasajero_id"`
	PrecioTotal    float64   `json:"precio_total"`
	PagoID         uuid.UUID `json:"pago_id"`
	WasPaidDigital bool      `json:"was_paid_digital"`
}
