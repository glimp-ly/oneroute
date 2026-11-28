package httpx

import (
	"encoding/json"
	"errors"
	"io"
	"net/http"
	"strings"
)

const defaultMaxBodyBytes int64 = 1 << 20 // 1 MiB

// Meta holds pagination metadata for list responses.
type Meta struct {
	Page  int   `json:"page"`
	Limit int   `json:"limit"`
	Total int64 `json:"total"`
}

// ErrorBody represents the standardized error payload inside Envelope.
type ErrorBody struct {
	Code    string `json:"code"`
	Message string `json:"message"`
	Details any    `json:"details,omitempty"`
}

// Envelope is the uniform JSON response wrapper used across the entire API.
type Envelope struct {
	Success bool       `json:"success"`
	Data    any        `json:"data"`
	Meta    *Meta      `json:"meta"`
	Error   *ErrorBody `json:"error"`
}

// WriteJSON serializes data and optional pagination meta inside the standard Envelope.
func WriteJSON(w http.ResponseWriter, status int, data any, meta *Meta) {
	env := Envelope{
		Success: status >= http.StatusOK && status < http.StatusBadRequest,
		Data:    data,
		Meta:    meta,
		Error:   nil,
	}
	writeEnvelope(w, status, env)
}

func writeEnvelope(w http.ResponseWriter, status int, env Envelope) {
	payload, err := json.Marshal(env)
	if err != nil {
		fallback := Envelope{
			Success: false,
			Data:    nil,
			Meta:    nil,
			Error: &ErrorBody{
				Code:    "INTERNAL_ERROR",
				Message: "Error interno del servidor.",
			},
		}
		payload, _ = json.Marshal(fallback)
		status = http.StatusInternalServerError
	}

	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	w.WriteHeader(status)
	_, _ = w.Write(payload)
}

type decodeConfig struct {
	maxBytes              int64
	disallowUnknownFields bool
}

// DecodeOption configures JSON request decoding behavior.
type DecodeOption func(*decodeConfig)

// WithMaxBytes overrides the default 1 MiB request body size limit.
func WithMaxBytes(n int64) DecodeOption {
	return func(c *decodeConfig) {
		if n > 0 {
			c.maxBytes = n
		}
	}
}

// WithDisallowUnknownFields toggles strict rejection of unknown JSON fields.
func WithDisallowUnknownFields(disallow bool) DecodeOption {
	return func(c *decodeConfig) {
		c.disallowUnknownFields = disallow
	}
}

// DecodeJSON decodes a single JSON object from r.Body with size limits and
// strict field validation by default.
func DecodeJSON(r *http.Request, dst any, opts ...DecodeOption) error {
	if r == nil || r.Body == nil {
		return BadRequest("El cuerpo de la solicitud es obligatorio.")
	}

	cfg := decodeConfig{
		maxBytes:              defaultMaxBodyBytes,
		disallowUnknownFields: true,
	}
	for _, opt := range opts {
		if opt != nil {
			opt(&cfg)
		}
	}

	limited := http.MaxBytesReader(nil, r.Body, cfg.maxBytes)
	dec := json.NewDecoder(limited)
	if cfg.disallowUnknownFields {
		dec.DisallowUnknownFields()
	}

	if err := dec.Decode(dst); err != nil {
		return mapDecodeError(err)
	}

	// Ensure there is no trailing JSON data after the first object.
	var extra any
	if err := dec.Decode(&extra); err != io.EOF {
		if err == nil {
			return BadRequest("El cuerpo debe contener un unico objeto JSON.")
		}
		return mapDecodeError(err)
	}

	return nil
}

func mapDecodeError(err error) error {
	var maxBytesErr *http.MaxBytesError
	if errors.As(err, &maxBytesErr) {
		return &AppError{
			Status:  http.StatusRequestEntityTooLarge,
			Code:    "PAYLOAD_TOO_LARGE",
			Message: "El cuerpo de la solicitud excede el tamano maximo permitido.",
			Cause:   err,
		}
	}

	if errors.Is(err, io.EOF) {
		return BadRequest("El cuerpo de la solicitud es obligatorio.")
	}

	if errors.Is(err, io.ErrUnexpectedEOF) {
		return BadRequest("JSON incompleto o malformado.")
	}

	var syntaxErr *json.SyntaxError
	if errors.As(err, &syntaxErr) {
		return BadRequest("JSON malformado.")
	}

	var typeErr *json.UnmarshalTypeError
	if errors.As(err, &typeErr) {
		if typeErr.Field != "" {
			return BadRequest("Tipo de dato invalido para el campo '" + typeErr.Field + "'.")
		}
		return BadRequest("Tipo de dato JSON invalido.")
	}

	msg := err.Error()
	const unknownPrefix = "json: unknown field "
	if strings.HasPrefix(msg, unknownPrefix) {
		field := strings.TrimPrefix(msg, unknownPrefix)
		return BadRequest("Campo no permitido en el cuerpo JSON: " + field + ".")
	}

	return BadRequest("Cuerpo JSON invalido.")
}
