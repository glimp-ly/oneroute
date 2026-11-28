package realtime

import (
	"context"
	"encoding/json"
	"log/slog"
	"net/http"
	"strings"
	"sync"
	"time"

	"github.com/google/uuid"
	"github.com/gorilla/websocket"
)

const (
	defaultWriteWait      = 10 * time.Second
	defaultPongWait       = 60 * time.Second
	defaultPingPeriod     = (defaultPongWait * 9) / 10
	defaultMaxMessageSize = 64 * 1024
	defaultSendBufferSize = 256
)

// EventEnvelope wraps all server-to-client WebSocket messages.
type EventEnvelope struct {
	Type      string    `json:"type"`
	Room      string    `json:"room,omitempty"`
	Payload   any       `json:"payload"`
	Timestamp time.Time `json:"timestamp"`
}

// ClientMessage represents an incoming client-to-server WebSocket frame.
type ClientMessage struct {
	Type          string  `json:"type"`
	Room          string  `json:"room,omitempty"`
	ViajeID       string  `json:"viaje_id,omitempty"`
	ReservaID     string  `json:"reserva_id,omitempty"`
	ClientEventID string  `json:"client_event_id,omitempty"`
	MsgType       string  `json:"msg_type,omitempty"`
	Content       string  `json:"content,omitempty"`
	RecordedAt    string  `json:"recorded_at,omitempty"`
	Lat           float64 `json:"lat,omitempty"`
	Lng           float64 `json:"lng,omitempty"`
	SpeedKmh      float64 `json:"speed_kmh,omitempty"`
	Heading       float64 `json:"heading,omitempty"`
	Battery       int     `json:"battery,omitempty"`
}

// TokenValidator validates a JWT token and returns the authenticated user ID.
type TokenValidator func(ctx context.Context, token string) (uuid.UUID, error)

// RoomAuthorizer verifies whether a user is allowed to subscribe to a given room.
type RoomAuthorizer func(ctx context.Context, userID uuid.UUID, room string) error

// ClientMessageHandler dispatches domain client messages such as gps.ping and chat.send.
type ClientMessageHandler func(ctx context.Context, userID uuid.UUID, msg ClientMessage)

// UserRoom returns the canonical WebSocket room name for a user.
func UserRoom(userID uuid.UUID) string {
	return "user:" + userID.String()
}

// TripRoom returns the canonical WebSocket room name for a trip.
func TripRoom(tripID uuid.UUID) string {
	return "trip:" + tripID.String()
}

// ReservationRoom returns the canonical WebSocket room name for a reservation.
func ReservationRoom(reservationID uuid.UUID) string {
	return "reservation:" + reservationID.String()
}

// Client represents a single authenticated WebSocket connection managed by Hub.
type Client struct {
	hub    *Hub
	conn   *websocket.Conn
	userID uuid.UUID

	send   chan []byte
	sendMu sync.RWMutex
	closed bool

	roomsMu sync.Mutex
	rooms   map[string]struct{}

	cancel context.CancelFunc
}

func newClient(hub *Hub, conn *websocket.Conn, userID uuid.UUID, sendBuf int, cancel context.CancelFunc) *Client {
	if sendBuf <= 0 {
		sendBuf = defaultSendBufferSize
	}
	return &Client{
		hub:    hub,
		conn:   conn,
		userID: userID,
		send:   make(chan []byte, sendBuf),
		rooms:  make(map[string]struct{}),
		cancel: cancel,
	}
}

// UserID returns the authenticated user ID associated with this client.
func (c *Client) UserID() uuid.UUID {
	return c.userID
}

func (c *Client) trySend(data []byte) bool {
	c.sendMu.RLock()
	defer c.sendMu.RUnlock()
	if c.closed {
		return false
	}
	select {
	case c.send <- data:
		return true
	default:
		return false
	}
}

func (c *Client) close() {
	c.sendMu.Lock()
	if !c.closed {
		c.closed = true
		close(c.send)
	}
	c.sendMu.Unlock()
	if c.cancel != nil {
		c.cancel()
	}
}

func (c *Client) sendError(room, code, message string) {
	env := EventEnvelope{
		Type: "error",
		Room: room,
		Payload: map[string]string{
			"code":    code,
			"message": message,
		},
		Timestamp: time.Now().UTC(),
	}
	data, err := json.Marshal(env)
	if err != nil {
		return
	}
	c.trySend(data)
}

func (c *Client) writePump() {
	defer c.hub.wg.Done()
	ticker := time.NewTicker(c.hub.pingPeriod)
	defer func() {
		ticker.Stop()
		_ = c.conn.Close()
	}()

	for {
		select {
		case msg, ok := <-c.send:
			_ = c.conn.SetWriteDeadline(time.Now().Add(c.hub.writeWait))
			if !ok {
				_ = c.conn.WriteMessage(
					websocket.CloseMessage,
					websocket.FormatCloseMessage(websocket.CloseNormalClosure, ""),
				)
				return
			}
			if err := c.conn.WriteMessage(websocket.TextMessage, msg); err != nil {
				return
			}
		case <-ticker.C:
			_ = c.conn.SetWriteDeadline(time.Now().Add(c.hub.writeWait))
			if err := c.conn.WriteMessage(websocket.PingMessage, nil); err != nil {
				return
			}
		}
	}
}

func (c *Client) readPump(ctx context.Context, authorizer RoomAuthorizer, onMessage ClientMessageHandler) {
	defer func() {
		c.hub.wg.Done()
		c.hub.unregisterClient(c)
		_ = c.conn.Close()
	}()

	c.conn.SetReadLimit(c.hub.maxMessageSize)
	_ = c.conn.SetReadDeadline(time.Now().Add(c.hub.pongWait))
	c.conn.SetPongHandler(func(string) error {
		return c.conn.SetReadDeadline(time.Now().Add(c.hub.pongWait))
	})

	for {
		_, raw, err := c.conn.ReadMessage()
		if err != nil {
			if websocket.IsUnexpectedCloseError(err, websocket.CloseGoingAway, websocket.CloseNormalClosure) {
				c.hub.logger.Debug("websocket read closed",
					slog.String("user_id", c.userID.String()),
					slog.String("error", err.Error()),
				)
			}
			return
		}
		_ = c.conn.SetReadDeadline(time.Now().Add(c.hub.pongWait))

		var msg ClientMessage
		if err := json.Unmarshal(raw, &msg); err != nil {
			c.hub.logger.Warn("invalid websocket message json",
				slog.String("user_id", c.userID.String()),
				slog.String("error", err.Error()),
			)
			c.sendError("", "INVALID_MESSAGE", "invalid JSON message")
			continue
		}

		c.handleMessage(ctx, msg, authorizer, onMessage)
	}
}

func (c *Client) handleMessage(ctx context.Context, msg ClientMessage, authorizer RoomAuthorizer, onMessage ClientMessageHandler) {
	switch msg.Type {
	case "subscribe":
		if msg.Room == "" {
			c.sendError("", "INVALID_ROOM", "room is required")
			return
		}
		if authorizer != nil {
			if err := authorizer(ctx, c.userID, msg.Room); err != nil {
				c.hub.logger.Warn("websocket room subscription denied",
					slog.String("user_id", c.userID.String()),
					slog.String("room", msg.Room),
					slog.String("error", err.Error()),
				)
				c.sendError(msg.Room, "FORBIDDEN", err.Error())
				return
			}
		}
		c.hub.Subscribe(c, msg.Room)
	case "unsubscribe":
		if msg.Room == "" {
			c.sendError("", "INVALID_ROOM", "room is required")
			return
		}
		c.hub.Unsubscribe(c, msg.Room)
	case "gps.ping", "chat.send":
		if onMessage != nil {
			onMessage(ctx, c.userID, msg)
		}
	default:
		c.hub.logger.Debug("unknown websocket message type",
			slog.String("user_id", c.userID.String()),
			slog.String("type", msg.Type),
		)
		c.sendError(msg.Room, "UNKNOWN_MESSAGE_TYPE", "unsupported message type")
	}
}

// Hub manages WebSocket clients and room-based event broadcasting.
type Hub struct {
	logger *slog.Logger

	mu      sync.RWMutex
	clients map[*Client]struct{}
	rooms   map[string]map[*Client]struct{}
	closed  bool

	wg sync.WaitGroup

	upgrader       websocket.Upgrader
	writeWait      time.Duration
	pongWait       time.Duration
	pingPeriod     time.Duration
	maxMessageSize int64
	sendBufferSize int
}

// NewHub constructs a new WebSocket Hub with default keepalive and buffer settings.
func NewHub(logger *slog.Logger) *Hub {
	if logger == nil {
		logger = slog.Default()
	}
	return &Hub{
		logger:  logger,
		clients: make(map[*Client]struct{}),
		rooms:   make(map[string]map[*Client]struct{}),
		upgrader: websocket.Upgrader{
			ReadBufferSize:  1024,
			WriteBufferSize: 4096,
			CheckOrigin: func(r *http.Request) bool {
				return true
			},
		},
		writeWait:      defaultWriteWait,
		pongWait:       defaultPongWait,
		pingPeriod:     defaultPingPeriod,
		maxMessageSize: defaultMaxMessageSize,
		sendBufferSize: defaultSendBufferSize,
	}
}

// Run blocks until ctx is cancelled, then closes all active WebSocket clients cleanly.
func (h *Hub) Run(ctx context.Context) {
	<-ctx.Done()
	h.shutdown()
	h.wg.Wait()
}

func (h *Hub) shutdown() {
	h.mu.Lock()
	if h.closed {
		h.mu.Unlock()
		return
	}
	h.closed = true
	active := make([]*Client, 0, len(h.clients))
	for client := range h.clients {
		active = append(active, client)
		client.roomsMu.Lock()
		clear(client.rooms)
		client.roomsMu.Unlock()
	}
	clear(h.clients)
	clear(h.rooms)
	h.mu.Unlock()

	for _, client := range active {
		client.close()
	}
}

func (h *Hub) isClosed() bool {
	h.mu.RLock()
	closed := h.closed
	h.mu.RUnlock()
	return closed
}

func (h *Hub) registerClient(client *Client, initialRooms ...string) bool {
	h.mu.Lock()
	if h.closed {
		h.mu.Unlock()
		return false
	}
	h.clients[client] = struct{}{}
	for _, room := range initialRooms {
		if room == "" {
			continue
		}
		subs, ok := h.rooms[room]
		if !ok {
			subs = make(map[*Client]struct{})
			h.rooms[room] = subs
		}
		subs[client] = struct{}{}
		client.roomsMu.Lock()
		client.rooms[room] = struct{}{}
		client.roomsMu.Unlock()
	}
	h.wg.Add(2)
	h.mu.Unlock()
	return true
}

func (h *Hub) unregisterClient(client *Client) {
	if client == nil {
		return
	}
	h.mu.Lock()
	delete(h.clients, client)
	client.roomsMu.Lock()
	for room := range client.rooms {
		if subs, ok := h.rooms[room]; ok {
			delete(subs, client)
			if len(subs) == 0 {
				delete(h.rooms, room)
			}
		}
	}
	clear(client.rooms)
	client.roomsMu.Unlock()
	h.mu.Unlock()

	client.close()
}

// Subscribe adds client to the given room.
func (h *Hub) Subscribe(client *Client, room string) {
	if client == nil || room == "" {
		return
	}
	h.mu.Lock()
	defer h.mu.Unlock()
	if h.closed {
		return
	}
	subs, ok := h.rooms[room]
	if !ok {
		subs = make(map[*Client]struct{})
		h.rooms[room] = subs
	}
	subs[client] = struct{}{}

	client.roomsMu.Lock()
	client.rooms[room] = struct{}{}
	client.roomsMu.Unlock()
}

// Unsubscribe removes client from the given room.
func (h *Hub) Unsubscribe(client *Client, room string) {
	if client == nil || room == "" {
		return
	}
	h.mu.Lock()
	defer h.mu.Unlock()
	if subs, ok := h.rooms[room]; ok {
		delete(subs, client)
		if len(subs) == 0 {
			delete(h.rooms, room)
		}
	}
	client.roomsMu.Lock()
	delete(client.rooms, room)
	client.roomsMu.Unlock()
}

// RoomSubscriberCount returns the current number of subscribed clients in room.
func (h *Hub) RoomSubscriberCount(room string) int {
	h.mu.RLock()
	defer h.mu.RUnlock()
	return len(h.rooms[room])
}

// Publish broadcasts an EventEnvelope to all clients subscribed to room without blocking on slow clients.
func (h *Hub) Publish(room string, event string, payload any) {
	if room == "" || event == "" {
		return
	}
	env := EventEnvelope{
		Type:      event,
		Room:      room,
		Payload:   payload,
		Timestamp: time.Now().UTC(),
	}
	data, err := json.Marshal(env)
	if err != nil {
		h.logger.Error("failed to marshal websocket event",
			slog.String("room", room),
			slog.String("event", event),
			slog.String("error", err.Error()),
		)
		return
	}

	h.mu.RLock()
	if h.closed {
		h.mu.RUnlock()
		return
	}
	subs := h.rooms[room]
	if len(subs) == 0 {
		h.mu.RUnlock()
		return
	}
	targets := make([]*Client, 0, len(subs))
	for client := range subs {
		targets = append(targets, client)
	}
	h.mu.RUnlock()

	for _, client := range targets {
		if !client.trySend(data) {
			h.logger.Warn("websocket client send buffer full, dropping message",
				slog.String("user_id", client.userID.String()),
				slog.String("room", room),
				slog.String("event", event),
			)
		}
	}
}

// ServeWS returns an http.HandlerFunc that authenticates via ?token=<jwt> or Authorization: Bearer <jwt>,
// upgrades the connection to WebSocket, joins user:{userID}, and processes room subscriptions and domain frames.
func (h *Hub) ServeWS(validator TokenValidator, authorizer RoomAuthorizer, onMessage ClientMessageHandler) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		if h.isClosed() {
			writeHTTPError(w, http.StatusServiceUnavailable, "SERVICE_UNAVAILABLE", "realtime hub is shutting down")
			return
		}

		token := extractToken(r)
		if token == "" {
			writeHTTPError(w, http.StatusUnauthorized, "UNAUTHORIZED", "missing authentication token")
			return
		}

		if validator == nil {
			writeHTTPError(w, http.StatusUnauthorized, "UNAUTHORIZED", "token validator is not configured")
			return
		}

		userID, err := validator(r.Context(), token)
		if err != nil || userID == uuid.Nil {
			h.logger.Debug("websocket authentication failed",
				slog.Any("error", err),
			)
			writeHTTPError(w, http.StatusUnauthorized, "UNAUTHORIZED", "invalid or expired token")
			return
		}

		conn, err := h.upgrader.Upgrade(w, r, nil)
		if err != nil {
			h.logger.Warn("websocket upgrade failed",
				slog.String("user_id", userID.String()),
				slog.String("error", err.Error()),
			)
			return
		}

		connCtx, connCancel := context.WithCancel(context.Background())
		client := newClient(h, conn, userID, h.sendBufferSize, connCancel)
		if !h.registerClient(client, UserRoom(userID)) {
			connCancel()
			_ = conn.Close()
			return
		}

		go client.writePump()
		go client.readPump(connCtx, authorizer, onMessage)
	}
}

func extractToken(r *http.Request) string {
	if token := strings.TrimSpace(r.URL.Query().Get("token")); token != "" {
		return token
	}
	authHeader := strings.TrimSpace(r.Header.Get("Authorization"))
	if len(authHeader) > 7 && strings.EqualFold(authHeader[:7], "Bearer ") {
		return strings.TrimSpace(authHeader[7:])
	}
	return ""
}

func writeHTTPError(w http.ResponseWriter, status int, code, message string) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(map[string]any{
		"success": false,
		"data":    nil,
		"meta":    nil,
		"error": map[string]any{
			"code":    code,
			"message": message,
			"details": map[string]any{},
		},
	})
}
