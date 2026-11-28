package contracts

import (
	"context"
	"encoding/json"
	"reflect"
	"regexp"
	"strings"
	"testing"
	"time"

	"github.com/glimp-ly/oneroute/backend/internal/shared/geo"
	"github.com/google/uuid"
	"github.com/jackc/pgx/v5"
)

var snakeCaseKeyPattern = regexp.MustCompile(`^[a-z][a-z0-9_]*$`)

// Compile-time interface satisfaction stubs.

type walletPortStub struct{}

func (s *walletPortStub) EnsureUserWallets(ctx context.Context, tx pgx.Tx, userID uuid.UUID) error {
	return nil
}

func (s *walletPortStub) HasSufficientCommissionBalance(ctx context.Context, userID uuid.UUID) (bool, error) {
	return true, nil
}

func (s *walletPortStub) CreditPassengerRefund(ctx context.Context, tx pgx.Tx, userID uuid.UUID, refType string, refID uuid.UUID, amount float64, desc string) error {
	return nil
}

func (s *walletPortStub) CreditPassengerBalance(ctx context.Context, tx pgx.Tx, userID uuid.UUID, refType string, refID uuid.UUID, amount float64, desc string) error {
	return nil
}

func (s *walletPortStub) ScheduleDelayedCredit(ctx context.Context, tx pgx.Tx, reservaID, modID, pagoID, userID uuid.UUID, amount float64, availableAt time.Time, reason string) error {
	return nil
}

func (s *walletPortStub) SettleRidePayment(ctx context.Context, tx pgx.Tx, cmd SettlePaymentCmd) (*SettlePaymentResult, error) {
	return &SettlePaymentResult{
		PagoID:     uuid.New(),
		Estado:     "pagado",
		MontoTotal: cmd.Amount,
	}, nil
}

func (s *walletPortStub) ProcessTipPayment(ctx context.Context, tx pgx.Tx, cmd TipCmd) (uuid.UUID, error) {
	return uuid.New(), nil
}

var _ WalletPort = (*walletPortStub)(nil)

type tripPortStub struct{}

func (s *tripPortStub) GetTripForBooking(ctx context.Context, viajeID uuid.UUID) (*TripSnapshot, error) {
	return &TripSnapshot{ViajeID: viajeID, Estado: "publicado"}, nil
}

func (s *tripPortStub) UpdateOperationalRoute(ctx context.Context, tx pgx.Tx, viajeID uuid.UUID, newGeoEWKT string, newDistKm float64) error {
	return nil
}

func (s *tripPortStub) AddOperationalStop(ctx context.Context, tx pgx.Tx, stop AddStopCmd) (uuid.UUID, error) {
	return uuid.New(), nil
}

func (s *tripPortStub) CancelAllTripBookingsTrigger(ctx context.Context, tx pgx.Tx, viajeID, actorID uuid.UUID, reason string) error {
	return nil
}

var _ TripPort = (*tripPortStub)(nil)

type bookingPortStub struct{}

func (s *bookingPortStub) CancelReservationsByTrip(ctx context.Context, tx pgx.Tx, viajeID, actorID uuid.UUID, reason string) ([]CancelledBookingInfo, error) {
	return nil, nil
}

func (s *bookingPortStub) CompleteEarlyByPassenger(ctx context.Context, tx pgx.Tx, reservaID, userID uuid.UUID, dropoff geo.Point, newFrac, newDistKm, newPrice float64) error {
	return nil
}

func (s *bookingPortStub) MarkReservationNoShow(ctx context.Context, tx pgx.Tx, reservaID, driverID uuid.UUID) error {
	return nil
}

func (s *bookingPortStub) MarkReservationBoarded(ctx context.Context, tx pgx.Tx, reservaID, driverID uuid.UUID) error {
	return nil
}

func (s *bookingPortStub) MarkReservationCompleted(ctx context.Context, tx pgx.Tx, reservaID uuid.UUID) error {
	return nil
}

var _ BookingPort = (*bookingPortStub)(nil)

type notificationPortStub struct{}

func (s *notificationPortStub) Notify(ctx context.Context, cmd NotifyCmd) error {
	return nil
}

func (s *notificationPortStub) CreateReservationChat(ctx context.Context, tx pgx.Tx, reservaID uuid.UUID) error {
	return nil
}

func (s *notificationPortStub) CloseReservationChat(ctx context.Context, tx pgx.Tx, reservaID uuid.UUID) error {
	return nil
}

var _ NotificationPort = (*notificationPortStub)(nil)

type identityPortStub struct{}

func (s *identityPortStub) GetUserSummary(ctx context.Context, userID uuid.UUID) (*UserSummary, error) {
	return &UserSummary{ID: userID}, nil
}

func (s *identityPortStub) GetEmergencyContacts(ctx context.Context, userID uuid.UUID) ([]EmergencyContactDTO, error) {
	return nil, nil
}

func (s *identityPortStub) RecalculateRatingStats(ctx context.Context, tx pgx.Tx, userID uuid.UUID, rolEvaluado string) error {
	return nil
}

var _ IdentityPort = (*identityPortStub)(nil)

func TestPortStubsExecuteContracts(t *testing.T) {
	ctx := context.Background()
	userID := uuid.New()
	reservaID := uuid.New()
	viajeID := uuid.New()
	modID := uuid.New()
	pagoID := uuid.New()
	actorID := uuid.New()

	var wp WalletPort = &walletPortStub{}
	if err := wp.EnsureUserWallets(ctx, nil, userID); err != nil {
		t.Fatalf("EnsureUserWallets failed: %v", err)
	}
	ok, err := wp.HasSufficientCommissionBalance(ctx, userID)
	if err != nil || !ok {
		t.Fatalf("HasSufficientCommissionBalance = (%v, %v), want (true, nil)", ok, err)
	}
	if err := wp.CreditPassengerRefund(ctx, nil, userID, "reserva", reservaID, 12.5, "reembolso"); err != nil {
		t.Fatalf("CreditPassengerRefund failed: %v", err)
	}
	if err := wp.CreditPassengerBalance(ctx, nil, userID, "reembolso", modID, 5.0, "credito 24h"); err != nil {
		t.Fatalf("CreditPassengerBalance failed: %v", err)
	}
	if err := wp.ScheduleDelayedCredit(ctx, nil, reservaID, modID, pagoID, userID, 5.0, time.Now().Add(24*time.Hour), "reduccion asientos"); err != nil {
		t.Fatalf("ScheduleDelayedCredit failed: %v", err)
	}
	res, err := wp.SettleRidePayment(ctx, nil, SettlePaymentCmd{ReservaID: reservaID, Amount: 18.0, Currency: "PEN"})
	if err != nil || res == nil || res.MontoTotal != 18.0 {
		t.Fatalf("SettleRidePayment = (%+v, %v), want MontoTotal 18.0", res, err)
	}
	if _, err := wp.ProcessTipPayment(ctx, nil, TipCmd{ReservaID: reservaID, Amount: 3.0}); err != nil {
		t.Fatalf("ProcessTipPayment failed: %v", err)
	}

	var tp TripPort = &tripPortStub{}
	snap, err := tp.GetTripForBooking(ctx, viajeID)
	if err != nil || snap == nil || snap.ViajeID != viajeID {
		t.Fatalf("GetTripForBooking = (%+v, %v)", snap, err)
	}
	if err := tp.UpdateOperationalRoute(ctx, nil, viajeID, "SRID=4326;LINESTRING(-77.04 -12.04, -77.02 -12.12)", 10.5); err != nil {
		t.Fatalf("UpdateOperationalRoute failed: %v", err)
	}
	if _, err := tp.AddOperationalStop(ctx, nil, AddStopCmd{ViajeID: viajeID, Tipo: "recogida", Ubicacion: geo.Point{Lat: -12.05, Lng: -77.04}}); err != nil {
		t.Fatalf("AddOperationalStop failed: %v", err)
	}
	if err := tp.CancelAllTripBookingsTrigger(ctx, nil, viajeID, actorID, "motivo"); err != nil {
		t.Fatalf("CancelAllTripBookingsTrigger failed: %v", err)
	}

	var bp BookingPort = &bookingPortStub{}
	if _, err := bp.CancelReservationsByTrip(ctx, nil, viajeID, actorID, "cancelacion conductor"); err != nil {
		t.Fatalf("CancelReservationsByTrip failed: %v", err)
	}
	if err := bp.CompleteEarlyByPassenger(ctx, nil, reservaID, userID, geo.Point{Lat: -12.08, Lng: -77.03}, 0.65, 6.4, 12.8); err != nil {
		t.Fatalf("CompleteEarlyByPassenger failed: %v", err)
	}
	if err := bp.MarkReservationNoShow(ctx, nil, reservaID, actorID); err != nil {
		t.Fatalf("MarkReservationNoShow failed: %v", err)
	}
	if err := bp.MarkReservationBoarded(ctx, nil, reservaID, actorID); err != nil {
		t.Fatalf("MarkReservationBoarded failed: %v", err)
	}
	if err := bp.MarkReservationCompleted(ctx, nil, reservaID); err != nil {
		t.Fatalf("MarkReservationCompleted failed: %v", err)
	}

	var np NotificationPort = &notificationPortStub{}
	if err := np.Notify(ctx, NotifyCmd{UserID: userID, Category: "reserva", Title: "Confirmada", Message: "Tu reserva fue confirmada"}); err != nil {
		t.Fatalf("Notify failed: %v", err)
	}
	if err := np.CreateReservationChat(ctx, nil, reservaID); err != nil {
		t.Fatalf("CreateReservationChat failed: %v", err)
	}
	if err := np.CloseReservationChat(ctx, nil, reservaID); err != nil {
		t.Fatalf("CloseReservationChat failed: %v", err)
	}

	var ip IdentityPort = &identityPortStub{}
	if _, err := ip.GetUserSummary(ctx, userID); err != nil {
		t.Fatalf("GetUserSummary failed: %v", err)
	}
	if _, err := ip.GetEmergencyContacts(ctx, userID); err != nil {
		t.Fatalf("GetEmergencyContacts failed: %v", err)
	}
	if err := ip.RecalculateRatingStats(ctx, nil, userID, "conductor"); err != nil {
		t.Fatalf("RecalculateRatingStats failed: %v", err)
	}
}

func TestDTOJSONTagsAreSnakeCase(t *testing.T) {
	dtoTypes := []any{
		SettlePaymentCmd{},
		SettlePaymentResult{},
		TipCmd{},
		TripSnapshot{},
		AddStopCmd{},
		CancelledBookingInfo{},
		NotifyCmd{},
		UserSummary{},
		EmergencyContactDTO{},
	}

	for _, dto := range dtoTypes {
		rt := reflect.TypeOf(dto)
		t.Run(rt.Name(), func(t *testing.T) {
			for i := 0; i < rt.NumField(); i++ {
				field := rt.Field(i)
				tag := field.Tag.Get("json")
				if tag == "" || tag == "-" {
					t.Fatalf("field %s.%s missing json tag", rt.Name(), field.Name)
				}
				key := strings.Split(tag, ",")[0]
				if !snakeCaseKeyPattern.MatchString(key) {
					t.Fatalf("field %s.%s json key %q is not valid snake_case", rt.Name(), field.Name, key)
				}
			}
		})
	}
}

func TestDTOJSONRoundTripAndSnakeCaseKeys(t *testing.T) {
	id1 := uuid.MustParse("11111111-1111-1111-1111-111111111111")
	id2 := uuid.MustParse("22222222-2222-2222-2222-222222222222")
	id3 := uuid.MustParse("33333333-3333-3333-3333-333333333333")
	id4 := uuid.MustParse("44444444-4444-4444-4444-444444444444")
	id5 := uuid.MustParse("55555555-5555-5555-5555-555555555555")

	tests := []struct {
		name         string
		value        any
		newTarget    func() any
		expectedKeys []string
	}{
		{
			name: "SettlePaymentCmd",
			value: &SettlePaymentCmd{
				ReservaID:        id1,
				PasajeroID:       id2,
				ConductorID:      id3,
				ViajeID:          id4,
				MetodoOriginal:   "mixto",
				PromoCode:        "LIMA10",
				Amount:           24.50,
				Currency:         "PEN",
				ClientEventID:    id5,
				SimulatedOutcome: "success",
			},
			newTarget: func() any { return &SettlePaymentCmd{} },
			expectedKeys: []string{
				"reserva_id", "pasajero_id", "conductor_id", "viaje_id",
				"metodo_original", "promo_code", "amount", "currency",
				"client_event_id", "simulated_outcome",
			},
		},
		{
			name: "SettlePaymentResult",
			value: &SettlePaymentResult{
				PagoID:         id1,
				Estado:         "pagado",
				MontoTotal:     24.50,
				MontoBilletera: 4.50,
				MontoDigital:   20.00,
				MontoEfectivo:  0.00,
				ComisionMonto:  2.45,
			},
			newTarget: func() any { return &SettlePaymentResult{} },
			expectedKeys: []string{
				"pago_id", "estado", "monto_total", "monto_billetera",
				"monto_digital", "monto_efectivo", "comision_monto",
			},
		},
		{
			name: "TipCmd",
			value: &TipCmd{
				ReservaID:        id1,
				EvaluadorID:      id2,
				EvaluadoID:       id3,
				Amount:           5.00,
				Metodo:           "digital",
				ClientEventID:    id4,
				SimulatedOutcome: "success",
			},
			newTarget: func() any { return &TipCmd{} },
			expectedKeys: []string{
				"reserva_id", "evaluador_id", "evaluado_id",
				"amount", "metodo", "client_event_id", "simulated_outcome",
			},
		},
		{
			name: "TripSnapshot",
			value: &TripSnapshot{
				ViajeID:               id1,
				ConductorID:           id2,
				Estado:                "en_curso",
				CapacidadTotal:        4,
				AceptaReservas:        true,
				AceptacionAutomatica:  true,
				GPSConfiable:          true,
				FraccionAvanceRuta:    0.35,
				TipoTarifa:            "por_km",
				PrecioAsientoCompleto: 0,
				TarifaKm:              2.00,
				DesvioMaximoKm:        1.50,
				DistanciaOperativaKm:  18.40,
				RutaOperativaEWKT:     "SRID=4326;LINESTRING(-77.0428 -12.0464, -77.0282 -12.1191)",
			},
			newTarget: func() any { return &TripSnapshot{} },
			expectedKeys: []string{
				"viaje_id", "conductor_id", "estado", "capacidad_total",
				"acepta_reservas", "aceptacion_automatica", "gps_confiable",
				"fraccion_avance_ruta", "tipo_tarifa", "precio_asiento_completo",
				"tarifa_km", "desvio_maximo_km", "distancia_operativa_km",
				"ruta_operativa_ewkt",
			},
		},
		{
			name: "AddStopCmd",
			value: &AddStopCmd{
				ViajeID:              id1,
				Tipo:                 "recogida",
				Nombre:               "Ovalo Miraflores",
				Ubicacion:            geo.Point{Lat: -12.1191, Lng: -77.0282},
				FraccionRuta:         0.42,
				DistanciaAcumuladaKm: 7.25,
				DesvioKm:             0.45,
			},
			newTarget: func() any { return &AddStopCmd{} },
			expectedKeys: []string{
				"viaje_id", "tipo", "nombre", "ubicacion",
				"fraccion_ruta", "distancia_acumulada_km", "desvio_km",
			},
		},
		{
			name: "CancelledBookingInfo",
			value: &CancelledBookingInfo{
				ReservaID:      id1,
				PasajeroID:     id2,
				PrecioTotal:    16.00,
				PagoID:         id3,
				WasPaidDigital: true,
			},
			newTarget: func() any { return &CancelledBookingInfo{} },
			expectedKeys: []string{
				"reserva_id", "pasajero_id", "precio_total",
				"pago_id", "was_paid_digital",
			},
		},
		{
			name: "NotifyCmd",
			value: &NotifyCmd{
				UserID:   id1,
				Category: "reserva_confirmada",
				Title:    "Reserva confirmada",
				Message:  "Tu viaje ha sido confirmado.",
				Priority: "alta",
				Data: map[string]any{
					"reserva_id": id2.String(),
					"monto_pen":  16.5,
				},
				SendPush: true,
			},
			newTarget: func() any { return &NotifyCmd{} },
			expectedKeys: []string{
				"user_id", "category", "title", "message",
				"priority", "data", "send_push",
			},
		},
		{
			name: "UserSummary",
			value: &UserSummary{
				ID:                    id1,
				Nombres:               "Carlos",
				Apellidos:             "Mendoza",
				Correo:                "carlos@oneroute.pe",
				Telefono:              "+51999888777",
				IdentidadVerificada:   true,
				DriverApproved:        true,
				CalificacionPasajero:  4.90,
				CalificacionConductor: 4.95,
				TotalViajesPasajero:   12,
				TotalViajesConductor:  48,
				Roles:                 []string{"pasajero", "conductor"},
			},
			newTarget: func() any { return &UserSummary{} },
			expectedKeys: []string{
				"id", "nombres", "apellidos", "correo", "telefono",
				"identidad_verificada", "driver_approved",
				"calificacion_pasajero", "calificacion_conductor",
				"total_viajes_pasajero", "total_viajes_conductor", "roles",
			},
		},
		{
			name: "EmergencyContactDTO",
			value: &EmergencyContactDTO{
				ID:          id1,
				Nombre:      "Lucia Mendoza",
				Parentesco:  "Hermana",
				Telefono:    "+51988777666",
				EsPrincipal: true,
			},
			newTarget: func() any { return &EmergencyContactDTO{} },
			expectedKeys: []string{
				"id", "nombre", "parentesco", "telefono", "es_principal",
			},
		},
	}

	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			raw, err := json.Marshal(tc.value)
			if err != nil {
				t.Fatalf("json.Marshal(%s) failed: %v", tc.name, err)
			}

			var decodedMap map[string]any
			if err := json.Unmarshal(raw, &decodedMap); err != nil {
				t.Fatalf("json.Unmarshal into map failed: %v", err)
			}

			if len(decodedMap) != len(tc.expectedKeys) {
				t.Fatalf("key count = %d, want %d; got keys from %s", len(decodedMap), len(tc.expectedKeys), string(raw))
			}

			for _, key := range tc.expectedKeys {
				if !snakeCaseKeyPattern.MatchString(key) {
					t.Fatalf("expected key %q is not snake_case", key)
				}
				if _, ok := decodedMap[key]; !ok {
					t.Fatalf("missing expected snake_case key %q in %s", key, string(raw))
				}
			}

			target := tc.newTarget()
			if err := json.Unmarshal(raw, target); err != nil {
				t.Fatalf("json.Unmarshal(%s) failed: %v", tc.name, err)
			}

			if !reflect.DeepEqual(target, tc.value) {
				t.Fatalf("round-trip mismatch:\ngot:  %+v\nwant: %+v", target, tc.value)
			}
		})
	}
}
