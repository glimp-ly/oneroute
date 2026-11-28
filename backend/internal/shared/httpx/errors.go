package httpx

import (
	"errors"
	"net/http"

	"github.com/glimp-ly/oneroute/backend/internal/shared/db"
)

// HTTPStatusCoder can be implemented by domain or storage errors so WriteError
// translates them into the API envelope without circular dependencies.
type HTTPStatusCoder interface {
	HTTPStatus() int
	ErrorCode() string
	Error() string
}

// AppError represents a structured HTTP API error matching the contract envelope.
type AppError struct {
	Status  int
	Code    string
	Message string
	Details any
	Cause   error
}

// Error implements the error interface.
func (e *AppError) Error() string {
	if e.Message != "" {
		return e.Message
	}
	return e.Code
}

// Unwrap returns the wrapped underlying error.
func (e *AppError) Unwrap() error {
	return e.Cause
}

// HTTPStatus returns the HTTP status code for this error.
func (e *AppError) HTTPStatus() int {
	return e.Status
}

// ErrorCode returns the machine-readable contract error code.
func (e *AppError) ErrorCode() string {
	return e.Code
}

// WithDetails attaches optional structured validation or context details.
func (e *AppError) WithDetails(details any) *AppError {
	e.Details = details
	return e
}

// WithCause attaches an underlying error cause for internal inspection.
func (e *AppError) WithCause(err error) *AppError {
	e.Cause = err
	return e
}

func msgOrDefault(msg, def string) string {
	if msg != "" {
		return msg
	}
	return def
}

// Unauthorized returns 401 UNAUTHORIZED.
func Unauthorized(msg string) *AppError {
	return &AppError{
		Status:  http.StatusUnauthorized,
		Code:    "UNAUTHORIZED",
		Message: msgOrDefault(msg, "No autorizado."),
	}
}

// TokenExpired returns 401 TOKEN_EXPIRED.
func TokenExpired(msg string) *AppError {
	return &AppError{
		Status:  http.StatusUnauthorized,
		Code:    "TOKEN_EXPIRED",
		Message: msgOrDefault(msg, "El token de acceso ha expirado."),
	}
}

// IdentityNotVerified returns 403 IDENTITY_NOT_VERIFIED.
func IdentityNotVerified(msg string) *AppError {
	return &AppError{
		Status:  http.StatusForbidden,
		Code:    "IDENTITY_NOT_VERIFIED",
		Message: msgOrDefault(msg, "Debes verificar tu identidad (DNI) antes de continuar."),
	}
}

// DriverNotApproved returns 403 DRIVER_NOT_APPROVED.
func DriverNotApproved(msg string) *AppError {
	return &AppError{
		Status:  http.StatusForbidden,
		Code:    "DRIVER_NOT_APPROVED",
		Message: msgOrDefault(msg, "Tu perfil de conductor o vehiculo aun no esta aprobado."),
	}
}

// UserRestricted returns 403 USER_RESTRICTED.
func UserRestricted(msg string) *AppError {
	return &AppError{
		Status:  http.StatusForbidden,
		Code:    "USER_RESTRICTED",
		Message: msgOrDefault(msg, "Tu cuenta tiene una restriccion o sancion activa."),
	}
}

// Forbidden returns 403 FORBIDDEN.
func Forbidden(msg string) *AppError {
	return &AppError{
		Status:  http.StatusForbidden,
		Code:    "FORBIDDEN",
		Message: msgOrDefault(msg, "No tienes permisos para realizar esta accion."),
	}
}

// ActiveTripLimit returns 409 ACTIVE_TRIP_LIMIT.
func ActiveTripLimit(msg string) *AppError {
	return &AppError{
		Status:  http.StatusConflict,
		Code:    "ACTIVE_TRIP_LIMIT",
		Message: msgOrDefault(msg, "Ya tienes un viaje publicado o en curso."),
	}
}

// CommissionBalanceLow returns 409 COMMISSION_BALANCE_LOW.
func CommissionBalanceLow(msg string) *AppError {
	return &AppError{
		Status:  http.StatusConflict,
		Code:    "COMMISSION_BALANCE_LOW",
		Message: msgOrDefault(msg, "Saldo insuficiente en tu billetera de comisiones."),
	}
}

// InsufficientCapacity returns 409 INSUFFICIENT_CAPACITY.
func InsufficientCapacity(msg string) *AppError {
	return &AppError{
		Status:  http.StatusConflict,
		Code:    "INSUFFICIENT_CAPACITY",
		Message: msgOrDefault(msg, "No hay asientos disponibles en el tramo solicitado."),
	}
}

// HoldExpired returns 409 HOLD_EXPIRED.
func HoldExpired(msg string) *AppError {
	return &AppError{
		Status:  http.StatusConflict,
		Code:    "HOLD_EXPIRED",
		Message: msgOrDefault(msg, "El tiempo de retencion de 60 segundos ha expirado."),
	}
}

// MaxAttemptsReached returns 409 MAX_ATTEMPTS_REACHED.
func MaxAttemptsReached(msg string) *AppError {
	return &AppError{
		Status:  http.StatusConflict,
		Code:    "MAX_ATTEMPTS_REACHED",
		Message: msgOrDefault(msg, "Alcanzaste el limite maximo de 3 intentos para este viaje."),
	}
}

// ModificationLimitReached returns 409 MODIFICATION_LIMIT_REACHED.
func ModificationLimitReached(msg string) *AppError {
	return &AppError{
		Status:  http.StatusConflict,
		Code:    "MODIFICATION_LIMIT_REACHED",
		Message: msgOrDefault(msg, "Ya utilizaste el unico cambio permitido para esta reserva."),
	}
}

// CalendarDeadlineExpired returns 422 CALENDAR_DEADLINE_EXPIRED.
func CalendarDeadlineExpired(msg string) *AppError {
	return &AppError{
		Status:  http.StatusUnprocessableEntity,
		Code:    "CALENDAR_DEADLINE_EXPIRED",
		Message: msgOrDefault(msg, "El plazo de 7 dias calendario ha vencido."),
	}
}

// Conflict returns 409 with a custom error code (defaults to CONFLICT).
func Conflict(code, msg string) *AppError {
	if code == "" {
		code = "CONFLICT"
	}
	return &AppError{
		Status:  http.StatusConflict,
		Code:    code,
		Message: msgOrDefault(msg, "La operacion entra en conflicto con el estado actual."),
	}
}

// BadRequest returns 400 BAD_REQUEST.
func BadRequest(msg string) *AppError {
	return &AppError{
		Status:  http.StatusBadRequest,
		Code:    "BAD_REQUEST",
		Message: msgOrDefault(msg, "Solicitud invalida."),
	}
}

// NotFound returns 404 NOT_FOUND.
func NotFound(msg string) *AppError {
	return &AppError{
		Status:  http.StatusNotFound,
		Code:    "NOT_FOUND",
		Message: msgOrDefault(msg, "Recurso no encontrado."),
	}
}

// Validation returns 422 VALIDATION_ERROR with optional field details.
func Validation(msg string, details any) *AppError {
	return &AppError{
		Status:  http.StatusUnprocessableEntity,
		Code:    "VALIDATION_ERROR",
		Message: msgOrDefault(msg, "Error de validacion en los datos enviados."),
		Details: details,
	}
}

// RateLimited returns 429 RATE_LIMITED.
func RateLimited(msg string) *AppError {
	return &AppError{
		Status:  http.StatusTooManyRequests,
		Code:    "RATE_LIMITED",
		Message: msgOrDefault(msg, "Demasiadas solicitudes. Intente nuevamente mas tarde."),
	}
}

// Internal returns 500 INTERNAL_ERROR without exposing underlying details.
func Internal(err error) *AppError {
	return &AppError{
		Status:  http.StatusInternalServerError,
		Code:    "INTERNAL_ERROR",
		Message: "Error interno del servidor.",
		Cause:   err,
	}
}

// WriteError maps err (including *AppError, HTTPStatusCoder, and db.MapPgError)
// into the standard JSON error envelope. Internal 5xx errors never leak internals.
func WriteError(w http.ResponseWriter, err error) {
	if err == nil {
		return
	}

	var appErr *AppError
	if errors.As(err, &appErr) {
		writeStatusCoder(w, appErr.HTTPStatus(), appErr.ErrorCode(), appErr.Error(), appErr.Details)
		return
	}

	mapped := db.MapPgError(err)
	var coder HTTPStatusCoder
	if errors.As(mapped, &coder) {
		writeStatusCoder(w, coder.HTTPStatus(), coder.ErrorCode(), coder.Error(), nil)
		return
	}

	writeStatusCoder(w, http.StatusInternalServerError, "INTERNAL_ERROR", "Error interno del servidor.", nil)
}

func writeStatusCoder(w http.ResponseWriter, status int, code, message string, details any) {
	if status < 400 || status >= 600 {
		status = http.StatusInternalServerError
	}
	if status >= http.StatusInternalServerError {
		code = "INTERNAL_ERROR"
		message = "Error interno del servidor."
		details = nil
	}
	if code == "" {
		code = "ERROR"
	}

	writeEnvelope(w, status, Envelope{
		Success: false,
		Data:    nil,
		Meta:    nil,
		Error: &ErrorBody{
			Code:    code,
			Message: message,
			Details: details,
		},
	})
}
