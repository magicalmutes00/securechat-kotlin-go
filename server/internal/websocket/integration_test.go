package websocket_test

import (
	"context"
	"database/sql"
	"encoding/json"
	"fmt"
	"net"
	"net/http"
	"os"
	"strings"
	"testing"
	"time"

	fasthhttp "github.com/fasthttp/websocket"
	"github.com/gofiber/fiber/v2"
	fiberwebsocket "github.com/gofiber/websocket/v2"
	"github.com/securechat/server/internal/auth"
	"github.com/securechat/server/internal/database"
	"github.com/securechat/server/internal/websocket"
	"github.com/securechat/server/pkg/jwt"
)

// These tests drive the real WebSocket handler over a real socket: two clients
// authenticate in-band, one sends a message, and the test asserts what actually
// comes back on the wire.
//
// This is the path every message in the product takes — message creation exists
// only over WebSocket, with no HTTP fallback — so it is the highest-value
// integration coverage in the project. It is skipped unless
// SECURECHAT_TEST_DATABASE_URL is set, exactly like the conversation tests.
func TestWebSocketMessagingIntegration(t *testing.T) {
	url := os.Getenv("SECURECHAT_TEST_DATABASE_URL")
	if url == "" {
		t.Skipf("SECURECHAT_TEST_DATABASE_URL not set; skipping integration test")
	}

	ctx := context.Background()
	db, err := database.Connect(ctx, url)
	if err != nil {
		t.Fatalf("connect: %v", err)
	}
	t.Cleanup(func() { db.Close() })

	if err := database.MigrateDir(db, "../../migrations"); err != nil {
		t.Fatalf("migrate: %v", err)
	}

	// Ephemeral signing keys: nothing needs to survive this process. The handler
	// takes the auth wrapper, which embeds the jwt manager and adds the claim
	// validation the middleware shares.
	jwtManager, err := jwt.NewTokenManager("", "", time.Minute, time.Hour, "securechat", "test")
	if err != nil {
		t.Fatalf("token manager: %v", err)
	}
	tokenManager := &auth.TokenManager{TokenManager: jwtManager}

	fx := newWSFixture(t, db, jwtManager)

	// --- start the server on an ephemeral port ----------------------------
	app := fiber.New()
	app.Get("/ws", fiberwebsocket.New(websocket.WebSocketHandler(db, tokenManager)))

	listener, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatalf("listen: %v", err)
	}
	go app.Listener(listener)
	t.Cleanup(func() {
		shutdownCtx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
		defer cancel()
		_ = app.ShutdownWithContext(shutdownCtx)
	})

	wsURL := fmt.Sprintf("ws://127.0.0.1:%d/ws", listener.Addr().(*net.TCPAddr).Port)

	// --- both participants authenticate -----------------------------------
	connA := dialAndAuthenticate(t, wsURL, fx.tokenA, fx.userA)
	t.Cleanup(func() { connA.Close() })
	connB := dialAndAuthenticate(t, wsURL, fx.tokenB, fx.userB)
	t.Cleanup(func() { connB.Close() })

	// --- A sends; B receives ---------------------------------------------
	const tempID = "temp-1"
	sendFrame(t, connA, map[string]any{
		"type": "MESSAGE_SEND",
		"id":   tempID,
		"payload": map[string]any{
			"conversation_id": fx.conversationID,
			"type":            "text",
			"text":            "hello from A",
			"temp_id":         tempID,
		},
	})

	ack := readFrameOfType(t, connA, "MESSAGE_ACK", 10*time.Second)
	if ack["id"] != tempID {
		t.Errorf("ack id = %v, want %q", ack["id"], tempID)
	}
	payload, _ := ack["payload"].(map[string]any)
	if payload["temp_id"] != tempID {
		t.Errorf("ack payload temp_id = %v, want %q", payload["temp_id"], tempID)
	}
	if _, ok := payload["message_id"].(float64); !ok {
		t.Errorf("ack payload message_id missing or not a number: %v", payload["message_id"])
	}

	received := readFrameOfType(t, connB, "MESSAGE_RECEIVED", 10*time.Second)
	rpayload, _ := received["payload"].(map[string]any)
	message, _ := rpayload["message"].(map[string]any)
	if message == nil {
		t.Fatalf("MESSAGE_RECEIVED payload has no message object: %v", rpayload)
	}
	if message["text"] != "hello from A" {
		t.Errorf("received text = %v, want %q", message["text"], "hello from A")
	}
	// Attribution travels with the message: without it a group chat has no author.
	if message["sender_name"] != "probe-a" {
		t.Errorf("received sender_name = %v, want %q", message["sender_name"], "probe-a")
	}
	if int64(message["conversation_id"].(float64)) != fx.conversationID {
		t.Errorf("received conversation_id = %v, want %d", message["conversation_id"], fx.conversationID)
	}

	// --- a non-participant cannot send ------------------------------------
	connC := dialAndAuthenticate(t, wsURL, fx.tokenC, fx.userC)
	t.Cleanup(func() { connC.Close() })

	sendFrame(t, connC, map[string]any{
		"type": "MESSAGE_SEND",
		"id":   "temp-2",
		"payload": map[string]any{
			"conversation_id": fx.conversationID,
			"type":            "text",
			"text":            "should be refused",
			"temp_id":         "temp-2",
		},
	})
	errFrame := readFrameOfType(t, connC, "ERROR", 10*time.Second)
	epayload, _ := errFrame["payload"].(map[string]any)
	if epayload["code"] != "NOT_PARTICIPANT" {
		t.Errorf("error code = %v, want NOT_PARTICIPANT", epayload["code"])
	}

	// --- media: the frame must carry the attachment, not just its id -------
	//
	// A recipient cannot fetch someone else's media row (GET /media/:id is scoped
	// to the uploader), so a MESSAGE_RECEIVED that carries only media_id leaves
	// the bubble impossible to draw.
	var mediaID int64
	if err := db.QueryRowContext(ctx, `
		INSERT INTO media (user_id, cloudinary_public_id, resource_type, secure_url,
		                   original_filename, mime_type, file_size, sha256)
		VALUES ($1, $2, 'image', $3, 'photo.jpg', 'image/jpeg', 4096, $4)
		RETURNING id`,
		fx.userA,
		fmt.Sprintf("securechat/users/%d/probe-media", fx.userA),
		"https://res.cloudinary.com/demo/image/upload/probe.jpg",
		strings.Repeat("a", 64),
	).Scan(&mediaID); err != nil {
		t.Fatalf("create media: %v", err)
	}

	const mediaTempID = "temp-media"
	sendFrame(t, connA, map[string]any{
		"type": "MESSAGE_SEND",
		"id":   mediaTempID,
		"payload": map[string]any{
			"conversation_id": fx.conversationID,
			"type":            "image",
			"media_id":        mediaID,
			"temp_id":         mediaTempID,
		},
	})

	mediaAck := readFrameOfType(t, connA, "MESSAGE_ACK", 10*time.Second)
	if mediaAck["id"] != mediaTempID {
		t.Errorf("media ack id = %v, want %q", mediaAck["id"], mediaTempID)
	}

	mediaFrame := readFrameOfType(t, connB, "MESSAGE_RECEIVED", 10*time.Second)
	mediaPayload, _ := mediaFrame["payload"].(map[string]any)
	mediaMessage, _ := mediaPayload["message"].(map[string]any)
	if mediaMessage == nil {
		t.Fatalf("media MESSAGE_RECEIVED has no message object: %v", mediaPayload)
	}
	if mediaMessage["type"] != "image" {
		t.Errorf("media message type = %v, want image", mediaMessage["type"])
	}

	attachment, ok := mediaMessage["media"].(map[string]any)
	if !ok || attachment == nil {
		t.Fatalf("media message has no nested media object: %v", mediaMessage)
	}
	if attachment["secure_url"] != "https://res.cloudinary.com/demo/image/upload/probe.jpg" {
		t.Errorf("attachment secure_url = %v", attachment["secure_url"])
	}
	if attachment["original_filename"] != "photo.jpg" {
		t.Errorf("attachment original_filename = %v", attachment["original_filename"])
	}
	if attachment["mime_type"] != "image/jpeg" {
		t.Errorf("attachment mime_type = %v", attachment["mime_type"])
	}
	if int64(attachment["id"].(float64)) != mediaID {
		t.Errorf("attachment id = %v, want %d", attachment["id"], mediaID)
	}

	// The refused message must not have been written: B should see nothing new.
	assertNoFrameOfType(t, connB, "MESSAGE_RECEIVED", 2*time.Second)
}

// ---------------------------------------------------------------------------
// socket helpers

func dialAndAuthenticate(t *testing.T, url, token string, wantUserID int64) *fasthhttp.Conn {
	t.Helper()

	dialer := &fasthhttp.Dialer{HandshakeTimeout: 10 * time.Second}
	conn, resp, err := dialer.Dial(url, nil)
	if err != nil {
		t.Fatalf("dial %s: %v", url, err)
	}
	if resp.StatusCode != http.StatusSwitchingProtocols {
		t.Fatalf("handshake status = %d, want 101", resp.StatusCode)
	}

	sendFrame(t, conn, map[string]any{
		"type":    "AUTHENTICATE",
		"payload": map[string]any{"access_token": token},
	})

	authAck := readFrameOfType(t, conn, "AUTH_ACK", 10*time.Second)
	payload, _ := authAck["payload"].(map[string]any)
	if int64(payload["user_id"].(float64)) != wantUserID {
		t.Fatalf("authenticated as user %v, want %d", payload["user_id"], wantUserID)
	}
	return conn
}

func sendFrame(t *testing.T, conn *fasthhttp.Conn, body map[string]any) {
	t.Helper()
	frame, err := json.Marshal(body)
	if err != nil {
		t.Fatalf("marshal frame: %v", err)
	}
	if err := conn.WriteMessage(fasthhttp.TextMessage, frame); err != nil {
		t.Fatalf("write frame: %v", err)
	}
}

// readFrameOfType reads frames until one of the wanted type arrives, skipping
// unrelated frames (presence broadcasts, other participants' events) rather than
// failing on them.
func readFrameOfType(t *testing.T, conn *fasthhttp.Conn, wantType string, timeout time.Duration) map[string]any {
	t.Helper()

	_ = conn.SetReadDeadline(time.Now().Add(timeout))
	for {
		_, raw, err := conn.ReadMessage()
		if err != nil {
			t.Fatalf("reading %s: %v", wantType, err)
		}
		var frame map[string]any
		if err := json.Unmarshal(raw, &frame); err != nil {
			continue
		}
		if frame["type"] == wantType {
			return frame
		}
	}
}

func assertNoFrameOfType(t *testing.T, conn *fasthhttp.Conn, wantType string, within time.Duration) {
	t.Helper()

	_ = conn.SetReadDeadline(time.Now().Add(within))
	for {
		_, raw, err := conn.ReadMessage()
		if err != nil {
			// Deadline hit without seeing the frame: the assertion holds.
			if strings.Contains(err.Error(), "timeout") || strings.Contains(err.Error(), "deadline") {
				return
			}
			t.Fatalf("reading while waiting for absence of %s: %v", wantType, err)
		}
		var frame map[string]any
		if err := json.Unmarshal(raw, &frame); err == nil && frame["type"] == wantType {
			t.Fatalf("received unexpected %s frame: %v", wantType, frame)
		}
	}
}

// ---------------------------------------------------------------------------
// fixture

type wsFixture struct {
	db                     *sql.DB
	userA, userB, userC    int64
	tokenA, tokenB, tokenC string
	conversationID         int64
}

// newWSFixture creates three users, devices, live sessions and a conversation
// shared by A and B. C is authenticated but in no conversation, so the test has a
// genuine outsider for the authorization assertion.
//
// Sessions are inserted directly: the WebSocket handshake re-checks the session
// row, so a token alone is not enough to authenticate.
func newWSFixture(t *testing.T, db *sql.DB, tm *jwt.TokenManager) *wsFixture {
	t.Helper()

	ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
	defer cancel()

	suffix := time.Now().UnixNano() % 1_000_000
	fx := &wsFixture{db: db}

	phone := func(n int) string { return fmt.Sprintf("+1997%06d%d", suffix, n) }

	insertUser := func(name, ph string) int64 {
		var id int64
		if err := db.QueryRowContext(ctx,
			`INSERT INTO users (phone_number, display_name) VALUES ($1, $2) RETURNING id`, ph, name).Scan(&id); err != nil {
			t.Fatalf("create %s: %v", name, err)
		}
		return id
	}
	fx.userA = insertUser("probe-a", phone(1))
	fx.userB = insertUser("probe-b", phone(2))
	fx.userC = insertUser("probe-c", phone(3))

	insertSession := func(userID int64) (deviceID, sessionID int64, token string) {
		if err := db.QueryRowContext(ctx, `
			INSERT INTO devices (user_id, device_name, device_identifier, platform)
			VALUES ($1, 'probe-device', $2, 'android') RETURNING id`,
			userID, fmt.Sprintf("ws-probe-%d-%d", userID, suffix)).Scan(&deviceID); err != nil {
			t.Fatalf("create device: %v", err)
		}
		if err := db.QueryRowContext(ctx, `
			INSERT INTO sessions (user_id, device_id, refresh_token_hash, expires_at)
			VALUES ($1, $2, $3, NOW() + INTERVAL '1 hour') RETURNING id`,
			userID, deviceID, fmt.Sprintf("probe-hash-%d-%d", userID, suffix)).Scan(&sessionID); err != nil {
			t.Fatalf("create session: %v", err)
		}
		token, _, tokenErr := tm.GenerateTokenPair(userID, deviceID, sessionID)
		if tokenErr != nil {
			t.Fatalf("generate token pair: %v", tokenErr)
		}
		return deviceID, sessionID, token
	}

	_, _, fx.tokenA = insertSession(fx.userA)
	_, _, fx.tokenB = insertSession(fx.userB)
	_, _, fx.tokenC = insertSession(fx.userC)

	if err := db.QueryRowContext(ctx, `INSERT INTO conversations (type) VALUES ('direct') RETURNING id`).
		Scan(&fx.conversationID); err != nil {
		t.Fatalf("create conversation: %v", err)
	}
	if _, err := db.ExecContext(ctx,
		`INSERT INTO conversation_participants (conversation_id, user_id) VALUES ($1, $2), ($1, $3)`,
		fx.conversationID, fx.userA, fx.userB); err != nil {
		t.Fatalf("add participants: %v", err)
	}

	t.Cleanup(func() {
		cleanupCtx, cleanupCancel := context.WithTimeout(context.Background(), 30*time.Second)
		defer cleanupCancel()
		_, _ = db.ExecContext(cleanupCtx, `DELETE FROM conversations WHERE id = $1`, fx.conversationID)
		_, _ = db.ExecContext(cleanupCtx, `DELETE FROM users WHERE id IN ($1, $2, $3)`, fx.userA, fx.userB, fx.userC)
	})

	return fx
}
