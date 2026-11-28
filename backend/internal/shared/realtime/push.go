package realtime

import (
	"context"
	"errors"
	"log/slog"

	"github.com/google/uuid"
)

// PushProvider delivers push notifications to user devices.
type PushProvider interface {
	SendPush(ctx context.Context, userID uuid.UUID, title, body, category string, data map[string]any) error
}

// PushNotificationPayload is the JSON payload emitted to WebSocket room user:{id}
// when MockPushProvider delivers a notification in development.
type PushNotificationPayload struct {
	UserID   uuid.UUID      `json:"user_id"`
	Title    string         `json:"title"`
	Body     string         `json:"body"`
	Category string         `json:"category"`
	Data     map[string]any `json:"data,omitempty"`
}

// MockPushProvider logs push deliveries via slog and publishes "notification.new"
// to the target user's WebSocket room on the configured Hub.
type MockPushProvider struct {
	hub    *Hub
	logger *slog.Logger
}

var _ PushProvider = (*MockPushProvider)(nil)

// NewMockPushProvider creates a development PushProvider backed by Hub and slog.
func NewMockPushProvider(hub *Hub, logger *slog.Logger) *MockPushProvider {
	if logger == nil {
		logger = slog.Default()
	}
	return &MockPushProvider{
		hub:    hub,
		logger: logger,
	}
}

// SendPush logs the notification and emits a "notification.new" event to user:{userID}.
func (p *MockPushProvider) SendPush(ctx context.Context, userID uuid.UUID, title, body, category string, data map[string]any) error {
	if err := ctx.Err(); err != nil {
		return err
	}
	if userID == uuid.Nil {
		return errors.New("user_id is required")
	}

	p.logger.InfoContext(ctx, "mock push notification delivered",
		slog.String("user_id", userID.String()),
		slog.String("title", title),
		slog.String("body", body),
		slog.String("category", category),
		slog.Any("data", data),
	)

	if p.hub != nil {
		p.hub.Publish(UserRoom(userID), "notification.new", PushNotificationPayload{
			UserID:   userID,
			Title:    title,
			Body:     body,
			Category: category,
			Data:     data,
		})
	}

	return nil
}
