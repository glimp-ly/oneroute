package realtime

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"io"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"strings"
	"sync"
	"testing"
	"time"

	"github.com/google/uuid"
	"github.com/gorilla/websocket"
)

func testLogger() *slog.Logger {
	return slog.New(slog.NewTextHandler(io.Discard, nil))
}

func wsURL(httpURL string, query string) string {
	u := "ws" + strings.TrimPrefix(httpURL, "http")
	if query != "" {
		return u + "?" + query
	}
	return u
}

func waitForCondition(t *testing.T, timeout time.Duration, cond func() bool, msg string) {
	t.Helper()
	deadline := time.Now().Add(timeout)
	for time.Now().Before(deadline) {
		if cond() {
			return
		}
		time.Sleep(5 * time.Millisecond)
	}
	t.Fatalf("timed out waiting for condition: %s", msg)
}

func TestServeWSAuthRejection(t *testing.T) {
	validUser := uuid.New()
	validator := func(ctx context.Context, token string) (uuid.UUID, error) {
		if token == "valid-token" {
			return validUser, nil
		}
		return uuid.Nil, errors.New("invalid token")
	}

	cases := []struct {
		name      string
		query     string
		header    http.Header
		validator TokenValidator
	}{
		{
			name:      "missing token",
			query:     "",
			header:    nil,
			validator: validator,
		},
		{
			name:      "invalid query token",
			query:     "token=bad-token",
			header:    nil,
			validator: validator,
		},
		{
			name:  "invalid bearer header",
			query: "",
			header: http.Header{
				"Authorization": []string{"Bearer wrong-token"},
			},
			validator: validator,
		},
		{
			name:  "non-bearer authorization scheme",
			query: "",
			header: http.Header{
				"Authorization": []string{"Basic dXNlcjpwYXNz"},
			},
			validator: validator,
		},
		{
			name:      "nil validator",
			query:     "token=valid-token",
			header:    nil,
			validator: nil,
		},
	}

	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			hub := NewHub(testLogger())
			srv := httptest.NewServer(hub.ServeWS(tc.validator, nil, nil))
			defer srv.Close()

			conn, resp, err := websocket.DefaultDialer.Dial(wsURL(srv.URL, tc.query), tc.header)
			if err == nil {
				_ = conn.Close()
				t.Fatalf("expected handshake failure, got connected")
			}
			if resp == nil {
				t.Fatalf("expected HTTP response on handshake rejection, got nil (err=%v)", err)
			}
			defer resp.Body.Close()

			if resp.StatusCode != http.StatusUnauthorized {
				t.Fatalf("expected status 401, got %d", resp.StatusCode)
			}

			var body struct {
				Success bool `json:"success"`
				Error   struct {
					Code string `json:"code"`
				} `json:"error"`
			}
			if decErr := json.NewDecoder(resp.Body).Decode(&body); decErr != nil {
				t.Fatalf("expected JSON error body, decode error: %v", decErr)
			}
			if body.Success {
				t.Fatalf("expected success=false in error envelope")
			}
			if body.Error.Code != "UNAUTHORIZED" {
				t.Fatalf("expected error code UNAUTHORIZED, got %q", body.Error.Code)
			}
			if got := hub.RoomSubscriberCount(UserRoom(validUser)); got != 0 {
				t.Fatalf("expected 0 subscribers after rejected auth, got %d", got)
			}
		})
	}
}

func TestServeWSAuthMethodsAndUserRoomBroadcast(t *testing.T) {
	userA := uuid.New()
	userB := uuid.New()
	validator := func(ctx context.Context, token string) (uuid.UUID, error) {
		switch token {
		case "token-a":
			return userA, nil
		case "token-b":
			return userB, nil
		default:
			return uuid.Nil, errors.New("unknown token")
		}
	}

	hub := NewHub(testLogger())
	ctx, cancel := context.WithCancel(context.Background())
	runDone := make(chan struct{})
	go func() {
		hub.Run(ctx)
		close(runDone)
	}()
	defer func() {
		cancel()
		<-runDone
	}()

	srv := httptest.NewServer(hub.ServeWS(validator, nil, nil))
	defer srv.Close()

	// Connect userA via query token.
	connA, respA, err := websocket.DefaultDialer.Dial(wsURL(srv.URL, "token=token-a"), nil)
	if err != nil {
		t.Fatalf("dial userA failed: %v", err)
	}
	if respA != nil {
		respA.Body.Close()
	}
	defer connA.Close()

	// Connect userB via Authorization Bearer header.
	connB, respB, err := websocket.DefaultDialer.Dial(wsURL(srv.URL, ""), http.Header{
		"Authorization": []string{"Bearer token-b"},
	})
	if err != nil {
		t.Fatalf("dial userB failed: %v", err)
	}
	if respB != nil {
		respB.Body.Close()
	}
	defer connB.Close()

	waitForCondition(t, time.Second, func() bool {
		return hub.RoomSubscriberCount(UserRoom(userA)) == 1 && hub.RoomSubscriberCount(UserRoom(userB)) == 1
	}, "auto-join user rooms")

	hub.Publish(UserRoom(userA), "request.incoming", map[string]any{
		"solicitud_id": "sol-1",
		"seats":        2,
	})
	hub.Publish(UserRoom(userB), "request.accepted_hold", map[string]any{
		"reserva_id": "res-2",
	})

	_ = connA.SetReadDeadline(time.Now().Add(2 * time.Second))
	var envA EventEnvelope
	if err := connA.ReadJSON(&envA); err != nil {
		t.Fatalf("userA ReadJSON failed: %v", err)
	}
	if envA.Type != "request.incoming" || envA.Room != UserRoom(userA) || envA.Timestamp.IsZero() {
		t.Fatalf("unexpected envelope for userA: %+v", envA)
	}

	_ = connB.SetReadDeadline(time.Now().Add(2 * time.Second))
	var envB EventEnvelope
	if err := connB.ReadJSON(&envB); err != nil {
		t.Fatalf("userB ReadJSON failed: %v", err)
	}
	if envB.Type != "request.accepted_hold" || envB.Room != UserRoom(userB) {
		t.Fatalf("unexpected envelope for userB: %+v", envB)
	}
}

func TestRoomSubscriptionAuthAndTripBroadcast(t *testing.T) {
	userID := uuid.New()
	allowedTrip := TripRoom(uuid.New())
	forbiddenTrip := TripRoom(uuid.New())

	validator := func(ctx context.Context, token string) (uuid.UUID, error) {
		return userID, nil
	}
	authorizer := func(ctx context.Context, uid uuid.UUID, room string) error {
		if uid == userID && room == allowedTrip {
			return nil
		}
		return errors.New("not a participant in this room")
	}

	hub := NewHub(testLogger())
	ctx, cancel := context.WithCancel(context.Background())
	runDone := make(chan struct{})
	go func() {
		hub.Run(ctx)
		close(runDone)
	}()
	defer func() {
		cancel()
		<-runDone
	}()

	srv := httptest.NewServer(hub.ServeWS(validator, authorizer, nil))
	defer srv.Close()

	conn, resp, err := websocket.DefaultDialer.Dial(wsURL(srv.URL, "token=ok"), nil)
	if err != nil {
		t.Fatalf("dial failed: %v", err)
	}
	if resp != nil {
		resp.Body.Close()
	}
	defer conn.Close()

	// Attempt forbidden subscription.
	if err := conn.WriteJSON(ClientMessage{Type: "subscribe", Room: forbiddenTrip}); err != nil {
		t.Fatalf("write forbidden subscribe failed: %v", err)
	}
	_ = conn.SetReadDeadline(time.Now().Add(2 * time.Second))
	var errEnv EventEnvelope
	if err := conn.ReadJSON(&errEnv); err != nil {
		t.Fatalf("expected error envelope on forbidden room: %v", err)
	}
	if errEnv.Type != "error" || errEnv.Room != forbiddenTrip {
		t.Fatalf("unexpected forbidden response: %+v", errEnv)
	}
	if hub.RoomSubscriberCount(forbiddenTrip) != 0 {
		t.Fatalf("expected 0 subscribers in forbidden trip room")
	}

	// Subscribe to allowed room.
	if err := conn.WriteJSON(ClientMessage{Type: "subscribe", Room: allowedTrip}); err != nil {
		t.Fatalf("write allowed subscribe failed: %v", err)
	}
	waitForCondition(t, time.Second, func() bool {
		return hub.RoomSubscriberCount(allowedTrip) == 1
	}, "subscribe to allowed trip room")

	hub.Publish(allowedTrip, "trip.gps_updated", map[string]any{
		"lat": -12.11,
		"lng": -77.03,
	})

	_ = conn.SetReadDeadline(time.Now().Add(2 * time.Second))
	var gpsEnv EventEnvelope
	if err := conn.ReadJSON(&gpsEnv); err != nil {
		t.Fatalf("ReadJSON gps update failed: %v", err)
	}
	if gpsEnv.Type != "trip.gps_updated" || gpsEnv.Room != allowedTrip {
		t.Fatalf("unexpected trip envelope: %+v", gpsEnv)
	}

	// Unsubscribe from allowed room.
	if err := conn.WriteJSON(ClientMessage{Type: "unsubscribe", Room: allowedTrip}); err != nil {
		t.Fatalf("write unsubscribe failed: %v", err)
	}
	waitForCondition(t, time.Second, func() bool {
		return hub.RoomSubscriberCount(allowedTrip) == 0
	}, "unsubscribe from allowed trip room")
}

func TestClientMessageDispatchGPSAndChat(t *testing.T) {
	userID := uuid.New()
	validator := func(ctx context.Context, token string) (uuid.UUID, error) {
		return userID, nil
	}

	var mu sync.Mutex
	var received []ClientMessage
	receivedUser := uuid.Nil

	onMsg := func(ctx context.Context, uid uuid.UUID, msg ClientMessage) {
		mu.Lock()
		defer mu.Unlock()
		receivedUser = uid
		received = append(received, msg)
	}

	hub := NewHub(testLogger())
	ctx, cancel := context.WithCancel(context.Background())
	runDone := make(chan struct{})
	go func() {
		hub.Run(ctx)
		close(runDone)
	}()
	defer func() {
		cancel()
		<-runDone
	}()

	srv := httptest.NewServer(hub.ServeWS(validator, nil, onMsg))
	defer srv.Close()

	conn, resp, err := websocket.DefaultDialer.Dial(wsURL(srv.URL, "token=ok"), nil)
	if err != nil {
		t.Fatalf("dial failed: %v", err)
	}
	if resp != nil {
		resp.Body.Close()
	}
	defer conn.Close()

	gpsFrame := ClientMessage{
		Type:          "gps.ping",
		ViajeID:       "viaje-123",
		ClientEventID: "evt-gps-1",
		Lat:           -12.11,
		Lng:           -77.03,
		SpeedKmh:      34.5,
		Heading:       180,
		Battery:       82,
		RecordedAt:    "2026-11-28T02:44:18Z",
	}
	chatFrame := ClientMessage{
		Type:          "chat.send",
		ReservaID:     "res-456",
		ClientEventID: "evt-chat-1",
		MsgType:       "texto",
		Content:       "Estoy en el paradero",
	}

	if err := conn.WriteJSON(gpsFrame); err != nil {
		t.Fatalf("write gpsFrame failed: %v", err)
	}
	if err := conn.WriteJSON(chatFrame); err != nil {
		t.Fatalf("write chatFrame failed: %v", err)
	}

	waitForCondition(t, time.Second, func() bool {
		mu.Lock()
		defer mu.Unlock()
		return len(received) == 2
	}, "receive 2 dispatched client messages")

	mu.Lock()
	defer mu.Unlock()
	if receivedUser != userID {
		t.Fatalf("expected userID %s, got %s", userID, receivedUser)
	}
	if received[0].Type != "gps.ping" || received[0].ViajeID != "viaje-123" || received[0].Battery != 82 || received[0].SpeedKmh != 34.5 {
		t.Fatalf("unexpected gps message: %+v", received[0])
	}
	if received[1].Type != "chat.send" || received[1].ReservaID != "res-456" || received[1].MsgType != "texto" || received[1].Content != "Estoy en el paradero" {
		t.Fatalf("unexpected chat message: %+v", received[1])
	}
}

func TestMockPushProviderDeliversNotificationOverWS(t *testing.T) {
	userID := uuid.New()
	validator := func(ctx context.Context, token string) (uuid.UUID, error) {
		return userID, nil
	}

	var logBuf bytes.Buffer
	logger := slog.New(slog.NewJSONHandler(&logBuf, nil))

	hub := NewHub(logger)
	ctx, cancel := context.WithCancel(context.Background())
	runDone := make(chan struct{})
	go func() {
		hub.Run(ctx)
		close(runDone)
	}()
	defer func() {
		cancel()
		<-runDone
	}()

	srv := httptest.NewServer(hub.ServeWS(validator, nil, nil))
	defer srv.Close()

	conn, resp, err := websocket.DefaultDialer.Dial(wsURL(srv.URL, "token=ok"), nil)
	if err != nil {
		t.Fatalf("dial failed: %v", err)
	}
	if resp != nil {
		resp.Body.Close()
	}
	defer conn.Close()

	waitForCondition(t, time.Second, func() bool {
		return hub.RoomSubscriberCount(UserRoom(userID)) == 1
	}, "user subscribed")

	push := NewMockPushProvider(hub, logger)
	if err := push.SendPush(context.Background(), userID, "Reserva confirmada", "Tu viaje inicia en 10 min", "booking", map[string]any{
		"reserva_id": "res-789",
	}); err != nil {
		t.Fatalf("SendPush failed: %v", err)
	}

	_ = conn.SetReadDeadline(time.Now().Add(2 * time.Second))
	var env EventEnvelope
	if err := conn.ReadJSON(&env); err != nil {
		t.Fatalf("ReadJSON failed: %v", err)
	}
	if env.Type != "notification.new" {
		t.Fatalf("expected event type notification.new, got %q", env.Type)
	}
	if env.Room != UserRoom(userID) {
		t.Fatalf("expected room %q, got %q", UserRoom(userID), env.Room)
	}

	payloadBytes, _ := json.Marshal(env.Payload)
	var payload PushNotificationPayload
	if err := json.Unmarshal(payloadBytes, &payload); err != nil {
		t.Fatalf("unmarshal payload failed: %v", err)
	}
	if payload.UserID != userID || payload.Title != "Reserva confirmada" || payload.Category != "booking" {
		t.Fatalf("unexpected push payload: %+v", payload)
	}
	if !strings.Contains(logBuf.String(), "mock push notification delivered") {
		t.Fatalf("expected slog output to contain mock push log, got %s", logBuf.String())
	}

	if err := push.SendPush(context.Background(), uuid.Nil, "t", "b", "c", nil); err == nil {
		t.Fatalf("expected error for uuid.Nil")
	}
	cancelledCtx, cancelReq := context.WithCancel(context.Background())
	cancelReq()
	if err := push.SendPush(cancelledCtx, userID, "t", "b", "c", nil); !errors.Is(err, context.Canceled) {
		t.Fatalf("expected context.Canceled, got %v", err)
	}
}

func TestSlowClientDoesNotBlockBroadcast(t *testing.T) {
	hub := NewHub(testLogger())
	hub.sendBufferSize = 4

	ctx, cancel := context.WithCancel(context.Background())
	runDone := make(chan struct{})
	go func() {
		hub.Run(ctx)
		close(runDone)
	}()
	defer func() {
		cancel()
		<-runDone
	}()

	fastUser := uuid.New()
	slowUser := uuid.New()
	validator := func(ctx context.Context, token string) (uuid.UUID, error) {
		if token == "fast" {
			return fastUser, nil
		}
		return slowUser, nil
	}

	srv := httptest.NewServer(hub.ServeWS(validator, nil, nil))
	defer srv.Close()

	fastConn, respFast, err := websocket.DefaultDialer.Dial(wsURL(srv.URL, "token=fast"), nil)
	if err != nil {
		t.Fatalf("dial fast client failed: %v", err)
	}
	if respFast != nil {
		respFast.Body.Close()
	}
	defer fastConn.Close()

	// Slow client connects via direct Hub registration without a reader/writer draining its channel.
	slowClient := newClient(hub, nil, slowUser, 2, nil)
	room := "trip:slow-test"
	hub.Subscribe(slowClient, room)

	if err := fastConn.WriteJSON(ClientMessage{Type: "subscribe", Room: room}); err != nil {
		t.Fatalf("fast subscribe failed: %v", err)
	}
	waitForCondition(t, time.Second, func() bool {
		return hub.RoomSubscriberCount(room) == 2
	}, "both fast and slow clients subscribed")

	// Fill slow client buffer and ensure Publish completes immediately and fast client still receives.
	const total = 10
	done := make(chan struct{})
	go func() {
		for i := 0; i < total; i++ {
			hub.Publish(room, "trip.gps_updated", map[string]int{"seq": i})
		}
		close(done)
	}()

	select {
	case <-done:
	case <-time.After(time.Second):
		t.Fatalf("Publish blocked on slow client")
	}

	// Fast client must receive messages without hanging.
	_ = fastConn.SetReadDeadline(time.Now().Add(2 * time.Second))
	var env EventEnvelope
	if err := fastConn.ReadJSON(&env); err != nil {
		t.Fatalf("fast client failed to read event: %v", err)
	}
	if env.Type != "trip.gps_updated" {
		t.Fatalf("unexpected event type: %q", env.Type)
	}

	hub.Unsubscribe(slowClient, room)
	slowClient.close()
}

func TestConcurrentPublishersSubscribersAndCleanShutdown(t *testing.T) {
	hub := NewHub(testLogger())
	hub.pingPeriod = 30 * time.Millisecond
	hub.pongWait = 200 * time.Millisecond

	ctx, cancel := context.WithCancel(context.Background())
	runDone := make(chan struct{})
	go func() {
		hub.Run(ctx)
		close(runDone)
	}()

	validator := func(ctx context.Context, token string) (uuid.UUID, error) {
		return uuid.Parse(token)
	}

	srv := httptest.NewServer(hub.ServeWS(validator, nil, nil))
	defer srv.Close()

	const clientCount = 8
	conns := make([]*websocket.Conn, 0, clientCount)
	userIDs := make([]uuid.UUID, 0, clientCount)
	sharedRoom := TripRoom(uuid.New())

	for i := 0; i < clientCount; i++ {
		uid := uuid.New()
		userIDs = append(userIDs, uid)
		c, resp, err := websocket.DefaultDialer.Dial(wsURL(srv.URL, "token="+uid.String()), nil)
		if err != nil {
			t.Fatalf("dial client %d failed: %v", i, err)
		}
		if resp != nil {
			resp.Body.Close()
		}
		conns = append(conns, c)
		if err := c.WriteJSON(ClientMessage{Type: "subscribe", Room: sharedRoom}); err != nil {
			t.Fatalf("subscribe client %d failed: %v", i, err)
		}
	}

	waitForCondition(t, 2*time.Second, func() bool {
		return hub.RoomSubscriberCount(sharedRoom) == clientCount
	}, "all clients subscribed to shared room")

	var readerWG sync.WaitGroup
	for _, c := range conns {
		readerWG.Add(1)
		go func(conn *websocket.Conn) {
			defer readerWG.Done()
			for {
				if _, _, err := conn.ReadMessage(); err != nil {
					return
				}
			}
		}(c)
	}

	var pubWG sync.WaitGroup
	for p := 0; p < 6; p++ {
		pubWG.Add(1)
		go func(worker int) {
			defer pubWG.Done()
			resRoom := ReservationRoom(uuid.New())
			for i := 0; i < 25; i++ {
				hub.Publish(sharedRoom, "trip.gps_updated", map[string]int{"worker": worker, "seq": i})
				hub.Publish(UserRoom(userIDs[i%len(userIDs)]), "notification.new", map[string]int{"seq": i})
				hub.Publish(resRoom, "chat.message_new", map[string]int{"seq": i})
				_ = hub.RoomSubscriberCount(sharedRoom)
			}
		}(p)
	}
	for s := 0; s < clientCount; s++ {
		pubWG.Add(1)
		go func(idx int) {
			defer pubWG.Done()
			extraRoom := TripRoom(uuid.New())
			c := newClient(hub, nil, userIDs[idx], 8, nil)
			for i := 0; i < 20; i++ {
				hub.Subscribe(c, extraRoom)
				_ = hub.RoomSubscriberCount(extraRoom)
				hub.Unsubscribe(c, extraRoom)
			}
			c.close()
		}(s)
	}
	pubWG.Wait()

	// Trigger clean shutdown and verify Hub.Run returns and connections close.
	cancel()
	select {
	case <-runDone:
	case <-time.After(3 * time.Second):
		t.Fatalf("Hub.Run did not shut down cleanly within timeout")
	}

	readerWG.Wait()
	for _, c := range conns {
		_ = c.Close()
	}

	if got := hub.RoomSubscriberCount(sharedRoom); got != 0 {
		t.Fatalf("expected 0 subscribers after shutdown, got %d", got)
	}
}
