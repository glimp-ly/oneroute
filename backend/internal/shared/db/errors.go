package db

import (
	"errors"
	"net/http"
	"strings"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgconn"
)

// PgAppError represents a PostgreSQL constraint, trigger, or row-lookup error
// translated into a domain-safe HTTP status and machine error code.
type PgAppError struct {
	Status     int
	Code       string
	Message    string
	Constraint string
	Cause      error
}

// Error implements the error interface.
func (e *PgAppError) Error() string {
	if e.Message != "" {
		return e.Message
	}
	return e.Code
}

// Unwrap returns the underlying database error.
func (e *PgAppError) Unwrap() error {
	return e.Cause
}

// HTTPStatus returns the HTTP status code associated with this error.
func (e *PgAppError) HTTPStatus() int {
	return e.Status
}

// ErrorCode returns the API contract error code.
func (e *PgAppError) ErrorCode() string {
	return e.Code
}

// MapPgError maps known pgx / PostgreSQL errors into *PgAppError.
// Unrecognized errors are returned unchanged.
func MapPgError(err error) error {
	if err == nil {
		return nil
	}

	var existing *PgAppError
	if errors.As(err, &existing) {
		return err
	}

	if errors.Is(err, pgx.ErrNoRows) {
		return &PgAppError{
			Status:  http.StatusNotFound,
			Code:    "NOT_FOUND",
			Message: "Recurso no encontrado.",
			Cause:   err,
		}
	}

	var pgErr *pgconn.PgError
	if !errors.As(err, &pgErr) {
		return err
	}

	switch pgErr.Code {
	case "23505": // unique_violation
		return mapUniqueViolation(pgErr)
	case "23503": // foreign_key_violation
		return mapForeignKeyViolation(pgErr)
	case "23514": // check_violation
		return &PgAppError{
			Status:     http.StatusUnprocessableEntity,
			Code:       "VALIDATION_ERROR",
			Message:    "Los datos incumplen una restriccion de validacion.",
			Constraint: pgErr.ConstraintName,
			Cause:      pgErr,
		}
	case "23502": // not_null_violation
		return &PgAppError{
			Status:     http.StatusBadRequest,
			Code:       "VALIDATION_ERROR",
			Message:    "Falta un campo obligatorio.",
			Constraint: pgErr.ColumnName,
			Cause:      pgErr,
		}
	case "22P02": // invalid_text_representation
		return &PgAppError{
			Status:  http.StatusBadRequest,
			Code:    "BAD_REQUEST",
			Message: "Formato de identificador o parametro invalido.",
			Cause:   pgErr,
		}
	case "P0001": // raise_exception from triggers/functions
		return mapRaiseException(pgErr)
	default:
		return err
	}
}

func mapUniqueViolation(pgErr *pgconn.PgError) *PgAppError {
	name := pgErr.ConstraintName
	switch {
	case name == "uq_conductor_viaje_activo":
		return &PgAppError{
			Status:     http.StatusConflict,
			Code:       "ACTIVE_TRIP_LIMIT",
			Message:    "El conductor ya tiene un viaje activo.",
			Constraint: name,
			Cause:      pgErr,
		}
	case name == "uq_pasajero_reserva_activa" || name == "uq_reserva_viaje_pasajero_activa":
		return &PgAppError{
			Status:     http.StatusConflict,
			Code:       "CONFLICT",
			Message:    "El pasajero ya tiene una reserva activa.",
			Constraint: name,
			Cause:      pgErr,
		}
	case strings.HasPrefix(name, "uq_reserva_cambio_"):
		return &PgAppError{
			Status:     http.StatusConflict,
			Code:       "MODIFICATION_LIMIT_REACHED",
			Message:    "Ya se utilizo el unico cambio permitido para esta reserva.",
			Constraint: name,
			Cause:      pgErr,
		}
	case name == "uq_identidad_documento_aprobado" || name == "uq_identidad_usuario_aprobado":
		return &PgAppError{
			Status:     http.StatusConflict,
			Code:       "CONFLICT",
			Message:    "El documento o usuario ya cuenta con una verificacion de identidad aprobada.",
			Constraint: name,
			Cause:      pgErr,
		}
	default:
		return &PgAppError{
			Status:     http.StatusConflict,
			Code:       "CONFLICT",
			Message:    "El registro ya existe o entra en conflicto con uno existente.",
			Constraint: name,
			Cause:      pgErr,
		}
	}
}

func mapForeignKeyViolation(pgErr *pgconn.PgError) *PgAppError {
	detail := strings.ToLower(pgErr.Detail)
	if strings.Contains(detail, "is not present in table") {
		return &PgAppError{
			Status:     http.StatusNotFound,
			Code:       "NOT_FOUND",
			Message:    "La entidad referenciada no existe.",
			Constraint: pgErr.ConstraintName,
			Cause:      pgErr,
		}
	}
	return &PgAppError{
		Status:     http.StatusBadRequest,
		Code:       "BAD_REQUEST",
		Message:    "Referencia invalida o registro aun en uso.",
		Constraint: pgErr.ConstraintName,
		Cause:      pgErr,
	}
}

func mapRaiseException(pgErr *pgconn.PgError) *PgAppError {
	msgLower := strings.ToLower(pgErr.Message)
	msg := pgErr.Message

	switch {
	case strings.Contains(msgLower, "capacidad"):
		return &PgAppError{
			Status:  http.StatusConflict,
			Code:    "INSUFFICIENT_CAPACITY",
			Message: msg,
			Cause:   pgErr,
		}
	case strings.Contains(msgLower, "plazo") &&
		(strings.Contains(msgLower, "calendario") ||
			strings.Contains(msgLower, "vencido") ||
			strings.Contains(msgLower, "descargo") ||
			strings.Contains(msgLower, "apelaci")):
		return &PgAppError{
			Status:  http.StatusUnprocessableEntity,
			Code:    "CALENDAR_DEADLINE_EXPIRED",
			Message: msg,
			Cause:   pgErr,
		}
	case strings.Contains(msgLower, "inmutable") ||
		strings.Contains(msgLower, "no puede eliminarse") ||
		strings.Contains(msgLower, "no pueden modificarse"):
		return &PgAppError{
			Status:  http.StatusConflict,
			Code:    "IMMUTABLE_RECORD",
			Message: msg,
			Cause:   pgErr,
		}
	case strings.Contains(msgLower, "no existe"):
		return &PgAppError{
			Status:  http.StatusNotFound,
			Code:    "NOT_FOUND",
			Message: msg,
			Cause:   pgErr,
		}
	case strings.Contains(msgLower, "no está aprobado") ||
		strings.Contains(msgLower, "no esta aprobado") ||
		strings.Contains(msgLower, "perfil de conductor"):
		return &PgAppError{
			Status:  http.StatusForbidden,
			Code:    "DRIVER_NOT_APPROVED",
			Message: msg,
			Cause:   pgErr,
		}
	default:
		return &PgAppError{
			Status:  http.StatusUnprocessableEntity,
			Code:    "BUSINESS_RULE_VIOLATION",
			Message: msg,
			Cause:   pgErr,
		}
	}
}
