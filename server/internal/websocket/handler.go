package websocket

import (
	"context"
	"database/sql"
	"encoding/base64"
	"encoding/json"
	"strconv"
	"sync"
	"time"

	"github.com/gofiber/websocket/v2"
	"github.com/securechat/server/internal/auth"
	"github.com/securechat/server/internal/devices"
	"github.com/securechat/server/internal/media"
	"github.com/securechat/server/internal/messages"
	"github.com/securechat/server/internal/push"
	"github.com/securechat/server/pkg/logger"
	"go.uber.org/zap"
)

const (
	// writeWait is the deadline for a single socket write.
	writeWait = 10 * time.Second
	// pongWait is how long the server waits for a pong before dropping the
	// connection; pings are sent at pongWait/2.
	pongWait    = 60 * time.Second
	pingPeriod  = 30 * time.Second
	sendBufSize = 256
	// Max inbound frame size: text messages can be long, but this still caps
	// a single frame well below anything a chat message needs.
	maxMessageSize = 64 * 1024
	// dbTimeout bounds a single database call made on behalf of a socket.
	dbTimeout = 5 * time.Second

	// Envelope types mirror the Signal message wire types so the receiving
	// client can route to SessionBuilder (PreKeySignalMessage) vs SessionCipher
	// (SignalMessage). Opaque to the server.
	EnvelopeTypePreKeySignal = 1
	EnvelopeTypeSignal       = 2

	// Max ciphertext bytes per envelope. Keeps a single WebSocket frame (and a
	// single DB row) bounded even when a client pads or misbehaves.
	MaxEnvelopeBytes = 64 * 1024
)

type Hub struct {
	clients     map[int64]map[*Client]bool // userID -> clients (multi-device)
	register    chan *Client
	unregister  chan *Client
	mu          sync.RWMutex
	db          *sql.DB
	tokenMgr    *auth.TokenManager
	pushService *push.Service
}

type Client struct {
	hub        *Hub
	conn       *websocket.Conn
	send       chan []byte
	sendMu     sync.RWMutex // guards sendClosed and every write to send
	sendClosed bool
	connOnce   sync.Once // closes conn exactly once
	doneOnce   sync.Once // closes done + cancels ctx exactly once
	done       chan struct{}
	mu         sync.Mutex // guards conn writes (write pump is the only writer)
	userID     int64
	deviceID   int64
	sessionID  int64
	authed     bool
	// ctx is cancelled on teardown so a database call started for this socket is
	// abandoned rather than left running against a connection that is gone.
	ctx    context.Context
	cancel context.CancelFunc
}

type WSMessage struct {
	Type    string          `json:"type"`
	ID      string          `json:"id,omitempty"`
	Payload json.RawMessage `json:"payload,omitempty"`
}

type AuthPayload struct {
	AccessToken string `json:"access_token"`
}

type AuthAckPayload struct {
	UserID   int64 `json:"user_id"`
	DeviceID int64 `json:"device_id"`
}

type EnvelopePayload struct {
	RecipientDeviceID int64  `json:"recipient_device_id"`
	EnvelopeType      int    `json:"envelope_type"`
	Ciphertext        string `json:"ciphertext"` // base64, opaque to the server
}

type MessageSendPayload struct {
	ConversationID int64            `json:"conversation_id"`
	Type           string           `json:"type"`
	// Text is the legacy plaintext path. E2EE clients send envelopes instead and
	// leave text empty; the server stores neither side's plaintext for envelopes.
	Text          string             `json:"text,omitempty"`
	Envelopes     []EnvelopePayload  `json:"envelopes,omitempty"`
	MediaID       *int64             `json:"media_id,omitempty"`
	ReplyToID     *int64             `json:"reply_to_id,omitempty"`
	TempID        string             `json:"temp_id"`
}

type TypingPayload struct {
	ConversationID int64 `json:"conversation_id"`
	UserID         int64 `json:"user_id"`
}

type MessageReadPayload struct {
	MessageID      int64 `json:"message_id"`
	ConversationID int64 `json:"conversation_id"`
}

type MessageDeliveredPayload struct {
	MessageID      int64 `json:"message_id"`
	ConversationID int64 `json:"conversation_id"`
}

type MessageDeletePayload struct {
	MessageID int64 `json:"message_id"`
}

type MessageAckPayload struct {
	TempID    string `json:"temp_id"`
	MessageID int64  `json:"message_id"`
	Status    string `json:"status"`
	CreatedAt int64  `json:"created_at"`
}

type MessageReceivedPayload struct {
	Message interface{} `json:"message"`
}

type ErrorPayload struct {
	Code    string `json:"code"`
	Message string `json:"message"`
}

func NewHub(db *sql.DB, tokenMgr *auth.TokenManager, pushService *push.Service) *Hub {
	return &Hub{
		clients:     make(map[int64]map[*Client]bool),
		register:    make(chan *Client),
		unregister:  make(chan *Client),
		db:          db,
		tokenMgr:    tokenMgr,
		pushService: pushService,
	}
}

func (h *Hub) Run() {
	for {
		select {
		case client := <-h.register:
			h.mu.Lock()
			if h.clients[client.userID] == nil {
				h.clients[client.userID] = make(map[*Client]bool)
			}
			h.clients[client.userID][client] = true
			h.mu.Unlock()
			logger.Log.Info("WebSocket client registered",
				zap.Int64("user_id", client.userID),
				zap.Int64("device_id", client.deviceID))

		case client := <-h.unregister:
			h.mu.Lock()
			if clients, ok := h.clients[client.userID]; ok {
				delete(clients, client)
				if len(clients) == 0 {
					delete(h.clients, client.userID)
				}
			}
			h.mu.Unlock()
			client.closeSend()
			// If this was the user's last live connection, mark them offline.
			h.mu.RLock()
			_, stillOnline := h.clients[client.userID]
			h.mu.RUnlock()
			if !stillOnline && client.userID != 0 {
				// Deliberately off the hub loop: this used to run a database
				// write and a process-wide presence broadcast inline, stalling
				// every other client's registration while it completed.
				go h.updateUserOnlineStatus(client.userID, false)
			}
			logger.Log.Info("WebSocket client unregistered", zap.Int64("user_id", client.userID))
		}
	}
}

func (c *Client) closeSend() {
	c.sendMu.Lock()
	defer c.sendMu.Unlock()
	if c.sendClosed {
		return
	}
	c.sendClosed = true
	close(c.send)
}

// closeConn closes the underlying socket exactly once. fasthttp/websocket
// panics when Conn.Close runs twice, and any subsequent read/write on a closed
// connection dereferences a nil net.Conn, so every teardown path funnels here.
func (c *Client) closeConn() {
	c.connOnce.Do(func() {
		_ = c.conn.Close()
	})
}

// shutdown signals both pumps to stop and tears the socket down. It is safe to
// call concurrently and repeatedly from any goroutine.
func (c *Client) shutdown() {
	c.doneOnce.Do(func() {
		close(c.done)
		if c.cancel != nil {
			c.cancel()
		}
	})
	c.closeConn()
}

// dbCtx returns a context bounded by both dbTimeout and this connection's
// lifetime. Every database call in this file previously used
// context.Background(), so a slow query pinned the read pump forever and kept
// running long after the client disappeared.
func (c *Client) dbCtx() (context.Context, context.CancelFunc) {
	base := c.ctx
	if base == nil {
		base = context.Background()
	}
	return context.WithTimeout(base, dbTimeout)
}

func (c *Client) ReadPump() {
	defer func() {
		// A panic here (e.g. reading a connection another goroutine just tore
		// down) must tear down this client only, never the whole process.
		if r := recover(); r != nil {
			logger.Log.Error("WebSocket read pump panic", zap.Any("panic", r))
		}
		c.hub.unregister <- c
		c.shutdown()
	}()

	c.conn.SetReadLimit(maxMessageSize)
	c.conn.SetReadDeadline(time.Now().Add(pongWait))
	c.conn.SetPongHandler(func(string) error {
		c.conn.SetReadDeadline(time.Now().Add(pongWait))
		return nil
	})

	for {
		_, messageBytes, err := c.conn.ReadMessage()
		if err != nil {
			if websocket.IsUnexpectedCloseError(err, websocket.CloseGoingAway, websocket.CloseAbnormalClosure) {
				logger.Log.Error("WebSocket read error", zap.Error(err))
			}
			break
		}

		c.handleMessage(messageBytes)
	}
}

func (c *Client) WritePump() {
	ticker := time.NewTicker(pingPeriod)
	defer func() {
		ticker.Stop()
		// A recovered panic (e.g. writing to a socket the read pump just closed)
		// must not take the process down — the connection is already dead.
		if r := recover(); r != nil {
			logger.Log.Error("WebSocket write pump panic", zap.Any("panic", r))
		}
		// If the write pump is the first to exit, this unblocks the read pump.
		c.shutdown()
	}()

	for {
		select {
		case <-c.done:
			return

		case message, ok := <-c.send:
			if !ok {
				// Hub closed the send channel; teardown is already handled.
				return
			}
			c.mu.Lock()
			_ = c.conn.SetWriteDeadline(time.Now().Add(writeWait))
			err := c.conn.WriteMessage(websocket.TextMessage, message)
			c.mu.Unlock()
			if err != nil {
				logger.Log.Error("WebSocket write error", zap.Error(err))
				return
			}

		case <-ticker.C:
			// Re-check done so a just-closed connection is never written to.
			select {
			case <-c.done:
				return
			default:
			}
			c.mu.Lock()
			_ = c.conn.SetWriteDeadline(time.Now().Add(writeWait))
			err := c.conn.WriteMessage(websocket.PingMessage, nil)
			c.mu.Unlock()
			if err != nil {
				return
			}
		}
	}
}

func (c *Client) handleMessage(messageBytes []byte) {
	var msg WSMessage
	if err := json.Unmarshal(messageBytes, &msg); err != nil {
		c.sendError("INVALID_MESSAGE", "Invalid message format")
		return
	}

	// Everything except (re-)authentication requires an authenticated client,
	// otherwise a pre-auth connection could act on behalf of userID 0.
	if !c.authed && msg.Type != "AUTHENTICATE" {
		c.sendError("NOT_AUTHENTICATED", "Send AUTHENTICATE first")
		return
	}

	switch msg.Type {
	case "AUTHENTICATE":
		c.handleAuthenticate(msg.Payload)
	case "MESSAGE_SEND":
		c.handleMessageSend(msg.ID, msg.Payload)
	case "TYPING_START":
		c.handleTyping(msg.Payload, true)
	case "TYPING_STOP":
		c.handleTyping(msg.Payload, false)
	case "MESSAGE_READ":
		c.handleMessageRead(msg.Payload)
	case "MESSAGE_DELIVERED":
		c.handleMessageDelivered(msg.Payload)
	case "MESSAGE_DELETE":
		c.handleMessageDelete(msg.Payload)
	default:
		c.sendError("UNKNOWN_MESSAGE_TYPE", "Unknown message type: "+msg.Type)
	}
}

func (c *Client) handleAuthenticate(payload json.RawMessage) {
	var auth AuthPayload
	if err := json.Unmarshal(payload, &auth); err != nil {
		c.sendError("INVALID_AUTH", "Invalid auth payload")
		return
	}

	claims, err := c.hub.tokenMgr.ValidateAccessToken(auth.AccessToken)
	if err != nil {
		c.sendError("AUTH_FAILED", "Invalid or expired token")
		c.shutdown()
		return
	}

	// Verify session is still active
	ctx, cancel := c.dbCtx()
	defer cancel()

	var isActive bool
	err = c.hub.db.QueryRowContext(ctx, `
		SELECT EXISTS(SELECT 1 FROM sessions WHERE id = $1 AND user_id = $2 AND revoked_at IS NULL AND expires_at > NOW())
	`, claims.SessionID, claims.UserID).Scan(&isActive)
	if err != nil || !isActive {
		c.sendError("SESSION_REVOKED", "Session has been revoked")
		c.shutdown()
		return
	}

	firstAuth := !c.authed
	c.userID = claims.UserID
	c.deviceID = claims.DeviceID
	c.sessionID = claims.SessionID
	c.authed = true

	if firstAuth {
		// Register client
		c.hub.register <- c

		// Update user online status (off the critical path: this does a DB write
		// plus a process-wide presence broadcast).
		go c.hub.updateUserOnlineStatus(claims.UserID, true)
	}

	// Send auth ack
	ack := WSMessage{
		Type: "AUTH_ACK",
		Payload: mustMarshal(AuthAckPayload{
			UserID:   claims.UserID,
			DeviceID: claims.DeviceID,
		}),
	}
	c.sendMessage(ack)
}

func (c *Client) handleMessageSend(tempID string, payload json.RawMessage) {
	var msgSend MessageSendPayload
	if err := json.Unmarshal(payload, &msgSend); err != nil {
		c.sendError("INVALID_MESSAGE", "Invalid message payload")
		return
	}

	if len(msgSend.Envelopes) == 0 && msgSend.Text == "" {
		c.sendError("INVALID_MESSAGE", "Message must carry text or envelopes")
		return
	}
	for _, env := range msgSend.Envelopes {
		if env.RecipientDeviceID == 0 {
			c.sendError("ENVELOPE_INVALID", "Envelope missing recipient device")
			return
		}
		if env.EnvelopeType != EnvelopeTypePreKeySignal && env.EnvelopeType != EnvelopeTypeSignal {
			c.sendError("ENVELOPE_INVALID", "Unknown envelope type")
			return
		}
		ciphertext, err := base64.StdEncoding.DecodeString(env.Ciphertext)
		if err != nil || len(ciphertext) == 0 || len(ciphertext) > MaxEnvelopeBytes {
			c.sendError("ENVELOPE_INVALID", "Envelope ciphertext invalid or too large")
			return
		}
		_ = ciphertext
	}

	// Bounded to dbTimeout and to this connection's lifetime, so a slow query
	// cannot pin the read pump and is abandoned when the socket dies.
	ctx, cancel := c.dbCtx()
	defer cancel()

	// Verify user is participant in conversation
	var isParticipant bool
	err := c.hub.db.QueryRowContext(ctx, `
		SELECT EXISTS(SELECT 1 FROM conversation_participants WHERE conversation_id = $1 AND user_id = $2 AND left_at IS NULL)
	`, msgSend.ConversationID, c.userID).Scan(&isParticipant)
	if err != nil || !isParticipant {
		c.sendError("NOT_PARTICICPANT", "Not a participant in this conversation")
		return
	}

	// Validate each envelope is addressed to a device of a conversation
	// participant — never to an outsider's device.
	deviceService := devices.NewService(devices.NewRepository(c.hub.db))
	participantDeviceIDs, err := deviceService.ParticipantDeviceIDs(ctx, msgSend.ConversationID)
	if err != nil {
		logger.Log.Error("Failed to load participant devices",
			zap.Int64("conversation_id", msgSend.ConversationID), zap.Error(err))
		c.sendError("MESSAGE_FAILED", "Failed to send message")
		return
	}
	allowed := make(map[int64]bool, len(participantDeviceIDs))
	for _, id := range participantDeviceIDs {
		allowed[id] = true
	}
	for _, env := range msgSend.Envelopes {
		if !allowed[env.RecipientDeviceID] {
			c.sendError("ENVELOPE_INVALID", "Envelope addressed to a non-participant device")
			return
		}
	}

	// Create message in database
	msgRepo := messages.NewRepository(c.hub.db)
	msg := &messages.Message{
		ConversationID: msgSend.ConversationID,
		SenderID:       c.userID,
		Type:           msgSend.Type,
		Text:           toNullString(msgSend.Text),
		MediaID:        toNullInt64(msgSend.MediaID),
		ReplyToID:      toNullInt64(msgSend.ReplyToID),
		Status:         "sent",
		ContentType:    contentType(msgSend.Envelopes),
	}

	createdMsg, err := msgRepo.Create(ctx, msg)
	if err != nil {
		logger.Log.Error("Failed to create message", zap.Error(err))
		c.sendError("MESSAGE_FAILED", "Failed to send message")
		return
	}

	// Store one envelope per recipient device and fan each out to that device's
	// live sockets. The server never opens ciphertext.
	if len(msgSend.Envelopes) > 0 {
		for _, env := range msgSend.Envelopes {
			ciphertext, _ := base64.StdEncoding.DecodeString(env.Ciphertext)
			if err := msgRepo.CreateEnvelope(ctx, createdMsg.ID, env.RecipientDeviceID, env.EnvelopeType, ciphertext); err != nil {
				logger.Log.Error("Failed to create envelope",
					zap.Int64("message_id", createdMsg.ID),
					zap.Int64("recipient_device_id", env.RecipientDeviceID),
					zap.Error(err))
				c.sendError("MESSAGE_FAILED", "Failed to send message")
				return
			}
			// Broadcast this envelope to the recipient device's socket only.
			c.hub.broadcastToDevice(env.RecipientDeviceID, c, WSMessage{
				Type: "MESSAGE_RECEIVED",
				Payload: mustMarshal(map[string]interface{}{
					"message": map[string]interface{}{
						"id":             createdMsg.ID,
						"conversation_id": msgSend.ConversationID,
						"sender_id":      c.userID,
						"type":           msgSend.Type,
						"media_id":       mediaIDValue(msgSend.MediaID),
						"reply_to_id":    mediaIDValue(msgSend.ReplyToID),
						"status":         "sent",
						"created_at":     createdMsg.CreatedAt.UnixMilli(),
						"updated_at":     createdMsg.UpdatedAt.UnixMilli(),
					},
					"envelope": map[string]interface{}{
						"envelope_type": env.EnvelopeType,
						"ciphertext":    env.Ciphertext,
					},
				}),
			})
		}
	}

	// Link uploaded media to the created message so the media row is reachable
	// from the message (media is uploaded before sending).
	if msgSend.MediaID != nil {
		mediaRepo := media.NewRepository(c.hub.db)
		if err := mediaRepo.LinkToMessage(ctx, *msgSend.MediaID, createdMsg.ID, c.userID); err != nil {
			logger.Log.Warn("Failed to link media to message",
				zap.Int64("media_id", *msgSend.MediaID),
				zap.Int64("message_id", createdMsg.ID),
				zap.Error(err))
		} else if refreshed, err := msgRepo.GetByID(ctx, createdMsg.ID); err == nil {
			// Re-read after linking: the row created above has media_id set but
			// no attached media, because media.message_id was still NULL when it
			// was fetched. Broadcasting it unrefreshed sent MESSAGE_RECEIVED to
			// every recipient without the attachment they were meant to draw.
			createdMsg = refreshed
		}
	}

	// Send ACK to sender
	ack := WSMessage{
		Type: "MESSAGE_ACK",
		ID:   tempID,
		Payload: mustMarshal(MessageAckPayload{
			TempID:    tempID,
			MessageID: createdMsg.ID,
			Status:    "sent",
			CreatedAt: createdMsg.CreatedAt.UnixMilli(),
		}),
	}
	c.sendMessage(ack)

	// Legacy plaintext path: broadcast the full message to other participants
	// (and the sender's other devices) so multi-device clients stay in sync.
	if len(msgSend.Envelopes) == 0 {
		c.hub.broadcastToConversation(msgSend.ConversationID, c, WSMessage{
			Type: "MESSAGE_RECEIVED",
			Payload: mustMarshal(MessageReceivedPayload{
				Message: toMessageResponse(createdMsg),
			}),
		})
	}

	// Push notifications: for every participant device that is not currently
	// connected via WebSocket, send an FCM push. Push payloads carry no
	// message plaintext, so the design stays E2EE-compatible.
	if c.hub.pushService != nil && c.hub.pushService.Enabled() {
		var offlineDevices []int64
		for _, deviceID := range participantDeviceIDs {
			if deviceID == c.deviceID {
				continue // sender's own device does not need a push
			}
			if !c.hub.DeviceOnline(deviceID) {
				offlineDevices = append(offlineDevices, deviceID)
			}
		}
		if len(offlineDevices) > 0 {
			// Fire-and-forget in a goroutine: push delivery must never block
			// message send. A fresh context bounded by dbTimeout is used.
			go func() {
				ctx, cancel := context.WithTimeout(context.Background(), dbTimeout)
				defer cancel()
				c.hub.pushService.NotifyDevices(
					ctx,
					offlineDevices,
					"New message",
					"New message",
					map[string]string{
						"conversation_id": strconv.FormatInt(msgSend.ConversationID, 10),
						"message_id":      strconv.FormatInt(createdMsg.ID, 10),
						"type":            msgSend.Type,
					},
				)
			}()
		}
	}
}

func (c *Client) handleTyping(payload json.RawMessage, isStart bool) {
	var typing TypingPayload
	if err := json.Unmarshal(payload, &typing); err != nil {
		return
	}

	if !c.isParticipant(typing.ConversationID) {
		return
	}

	eventType := "TYPING_START"
	if !isStart {
		eventType = "TYPING_STOP"
	}

	c.hub.broadcastToConversation(typing.ConversationID, c, WSMessage{
		Type: eventType,
		Payload: mustMarshal(TypingPayload{
			ConversationID: typing.ConversationID,
			UserID:         c.userID,
		}),
	})
}

func (c *Client) handleMessageRead(payload json.RawMessage) {
	var read MessageReadPayload
	if err := json.Unmarshal(payload, &read); err != nil {
		return
	}

	if !c.isParticipant(read.ConversationID) {
		c.sendError("NOT_PARTICIPANT", "Not a participant in this conversation")
		return
	}

	// Verify the message actually belongs to the conversation before marking
	// it read — otherwise any participant could toggle arbitrary messages.
	ctx, cancel := c.dbCtx()
	defer cancel()

	msgRepo := messages.NewRepository(c.hub.db)
	belongs, err := msgRepo.BelongsToConversation(ctx, read.MessageID, read.ConversationID)
	if err != nil || !belongs {
		c.sendError("INVALID_MESSAGE", "Message does not belong to this conversation")
		return
	}

	if err := msgRepo.UpdateReadStatus(ctx, read.MessageID); err != nil {
		logger.Log.Error("Failed to update read status", zap.Error(err))
	}

	// Broadcast to conversation (sender's other devices included)
	c.hub.broadcastToConversation(read.ConversationID, c, WSMessage{
		Type: "MESSAGE_READ",
		Payload: mustMarshal(map[string]interface{}{
			"message_id":      read.MessageID,
			"conversation_id": read.ConversationID,
			"read_by":         c.userID,
			"read_at":         time.Now().UnixMilli(),
		}),
	})
}

func (c *Client) handleMessageDelivered(payload json.RawMessage) {
	var delivered MessageDeliveredPayload
	if err := json.Unmarshal(payload, &delivered); err != nil {
		return
	}

	if !c.isParticipant(delivered.ConversationID) {
		c.sendError("NOT_PARTICIPANT", "Not a participant in this conversation")
		return
	}

	ctx, cancel := c.dbCtx()
	defer cancel()

	msgRepo := messages.NewRepository(c.hub.db)
	belongs, err := msgRepo.BelongsToConversation(ctx, delivered.MessageID, delivered.ConversationID)
	if err != nil || !belongs {
		c.sendError("INVALID_MESSAGE", "Message does not belong to this conversation")
		return
	}

	if err := msgRepo.UpdateStatus(ctx, delivered.MessageID, "delivered"); err != nil {
		logger.Log.Error("Failed to update delivery status", zap.Error(err))
	}

	// Mark this device's envelope delivered so senders can tell which
	// destinations have their copy (envelopes are per-device, not per-user).
	if err := msgRepo.MarkEnvelopeDelivered(ctx, delivered.MessageID, c.deviceID); err != nil {
		logger.Log.Error("Failed to mark envelope delivered",
			zap.Int64("message_id", delivered.MessageID),
			zap.Int64("device_id", c.deviceID),
			zap.Error(err))
	}

	// Delivery receipts go back to the original sender (all their devices).
	c.hub.broadcastToConversation(delivered.ConversationID, c, WSMessage{
		Type: "MESSAGE_DELIVERED",
		Payload: mustMarshal(map[string]interface{}{
			"message_id":      delivered.MessageID,
			"conversation_id": delivered.ConversationID,
			"delivered_to":    c.userID,
			"delivered_at":    time.Now().UnixMilli(),
		}),
	})
}

func (c *Client) handleMessageDelete(payload json.RawMessage) {
	var del MessageDeletePayload
	if err := json.Unmarshal(payload, &del); err != nil {
		return
	}

	msgRepo := messages.NewRepository(c.hub.db)

	// Resolve the message's conversation first: the delete event must be
	// scoped to that conversation only, never broadcast globally.
	ctx, cancel := c.dbCtx()
	defer cancel()

	msg, err := msgRepo.GetByID(ctx, del.MessageID)
	if err != nil {
		c.sendError("MESSAGE_NOT_FOUND", "Message not found")
		return
	}

	if !c.isParticipant(msg.ConversationID) {
		c.sendError("NOT_PARTICIPANT", "Not a participant in this conversation")
		return
	}

	if err := msgRepo.SoftDelete(ctx, del.MessageID, c.userID); err != nil {
		c.sendError("MESSAGE_DELETE_FAILED", "Failed to delete message")
		return
	}

	c.hub.broadcastToConversation(msg.ConversationID, c, WSMessage{
		Type: "MESSAGE_DELETE",
		Payload: mustMarshal(map[string]interface{}{
			"message_id":      del.MessageID,
			"conversation_id": msg.ConversationID,
			"deleted_by":      c.userID,
		}),
	})
}

// DeviceOnline reports whether any socket of the given device is currently
// connected to the hub. Used to decide whether a message recipient needs a
// push notification instead of (or in addition to) a WebSocket frame.
func (h *Hub) DeviceOnline(deviceID int64) bool {
	h.mu.RLock()
	defer h.mu.RUnlock()
	for _, clients := range h.clients {
		for client := range clients {
			if client.deviceID == deviceID {
				return true
			}
		}
	}
	return false
}

// isParticipant reports whether the client's user is an active participant of
// the conversation.
func (c *Client) isParticipant(conversationID int64) bool {
	ctx, cancel := c.dbCtx()
	defer cancel()

	var isParticipant bool
	err := c.hub.db.QueryRowContext(ctx, `
		SELECT EXISTS(SELECT 1 FROM conversation_participants WHERE conversation_id = $1 AND user_id = $2 AND left_at IS NULL)
	`, conversationID, c.userID).Scan(&isParticipant)
	return err == nil && isParticipant
}

func (c *Client) sendMessage(msg WSMessage) {
	data, _ := json.Marshal(msg)
	c.queue(data)
}

// queue hands data to the write pump without ever blocking and without ever
// writing to a closed channel.
//
// Closing c.send is guarded by sendMu so it cannot interleave with an
// in-flight send. Without that guard this was a live panic: a client whose
// buffer filled was unregistered (which closes c.send) while its own read pump
// was still handling inbound frames, and the next send wrote to the closed
// channel.
func (c *Client) queue(data []byte) {
	c.sendMu.RLock()
	defer c.sendMu.RUnlock()

	if c.sendClosed {
		return
	}

	select {
	case c.send <- data:
	default:
		// Buffer full: the peer is not draining its socket. Tear the
		// connection down instead of blocking the broadcaster. This must stay
		// non-blocking and must not touch the hub: queue also runs on the
		// broadcast path, which holds hub.mu.RLock.
		go c.shutdown()
	}
}

func (c *Client) sendError(code, message string) {
	c.sendMessage(WSMessage{
		Type: "ERROR",
		Payload: mustMarshal(ErrorPayload{
			Code:    code,
			Message: message,
		}),
	})
}

// broadcastToConversation sends msg to every connected device of every active
// participant except the sending client itself. The DB lookup happens before
// any lock is taken so database latency never blocks hub bookkeeping.
func (h *Hub) broadcastToConversation(conversationID int64, exclude *Client, msg WSMessage) {
	data, _ := json.Marshal(msg)

	ctx, cancel := context.WithTimeout(context.Background(), dbTimeout)
	defer cancel()

	rows, err := h.db.QueryContext(ctx, `
		SELECT user_id FROM conversation_participants WHERE conversation_id = $1 AND left_at IS NULL
	`, conversationID)
	if err != nil {
		logger.Log.Error("Failed to load conversation participants", zap.Int64("conversation_id", conversationID), zap.Error(err))
		return
	}
	defer rows.Close()

	var userIDs []int64
	for rows.Next() {
		var userID int64
		if err := rows.Scan(&userID); err != nil {
			continue
		}
		userIDs = append(userIDs, userID)
	}

	h.mu.RLock()
	defer h.mu.RUnlock()

	for _, userID := range userIDs {
		clients, ok := h.clients[userID]
		if !ok {
			continue
		}
		for client := range clients {
			if client == exclude {
				continue
			}
			// queue is non-blocking and closed-channel safe, so a slow or
			// already-dropped peer cannot stall the broadcast.
			client.queue(data)
		}
	}
}

// broadcastToDevice sends msg to every live socket of a single device. The
// device map is keyed by userID, so finding a specific device_id requires
// scanning each user's client set — acceptable because fan-out targets one
// device at a time.
func (h *Hub) broadcastToDevice(deviceID int64, exclude *Client, msg WSMessage) {
	data, _ := json.Marshal(msg)

	h.mu.RLock()
	defer h.mu.RUnlock()

	for _, clients := range h.clients {
		for client := range clients {
			if client.deviceID != deviceID {
				continue
			}
			if client == exclude {
				continue
			}
			client.queue(data)
		}
	}
}

func (h *Hub) updateUserOnlineStatus(userID int64, isOnline bool) {
	ctx, cancel := context.WithTimeout(context.Background(), dbTimeout)
	defer cancel()

	if _, err := h.db.ExecContext(ctx, `
		UPDATE users SET is_online = $1, last_seen = NOW() WHERE id = $2
	`, isOnline, userID); err != nil {
		logger.Log.Error("Failed to update user online status",
			zap.Int64("user_id", userID), zap.Bool("is_online", isOnline), zap.Error(err))
	}

	// Broadcast presence update
	data, _ := json.Marshal(WSMessage{
		Type: map[bool]string{true: "USER_ONLINE", false: "USER_OFFLINE"}[isOnline],
		Payload: mustMarshal(map[string]interface{}{
			"user_id":   userID,
			"is_online": isOnline,
			"last_seen": time.Now().UnixMilli(),
		}),
	})

	h.mu.RLock()
	defer h.mu.RUnlock()
	for _, clients := range h.clients {
		for client := range clients {
			client.queue(data)
		}
	}
}

func mustMarshal(v interface{}) json.RawMessage {
	data, _ := json.Marshal(v)
	return data
}

func toNullString(s string) sql.NullString {
	if s != "" {
		return sql.NullString{String: s, Valid: true}
	}
	return sql.NullString{}
}

func toNullInt64(i *int64) sql.NullInt64 {
	if i != nil {
		return sql.NullInt64{Int64: *i, Valid: true}
	}
	return sql.NullInt64{}
}

func toMessageResponse(msg *messages.Message) map[string]interface{} {
	// sender_name is absent rather than empty when unknown, so the client can
	// distinguish "no author recorded" from an author with a blank name.
	var senderName interface{}
	if msg.SenderName.Valid {
		senderName = msg.SenderName.String
	}

	return map[string]interface{}{
		"id":              msg.ID,
		"conversation_id": msg.ConversationID,
		"sender_id":       msg.SenderID,
		"sender_name":     senderName,
		"type":            msg.Type,
		"text":            msg.Text.String,
		"media_id":        nullIfZero(msg.MediaID.Int64, msg.MediaID.Valid),
		"media":           mediaAttachment(msg.Media),
		"reply_to_id":     nullIfZero(msg.ReplyToID.Int64, msg.ReplyToID.Valid),
		"status":          msg.Status,
		"created_at":      msg.CreatedAt.UnixMilli(),
		"updated_at":      msg.UpdatedAt.UnixMilli(),
	}
}

// mediaAttachment shapes an attachment for the socket frame, or nil when the
// message has none (so the client sees an absent field, not an empty object).
func mediaAttachment(m *messages.MediaAttachment) interface{} {
	if m == nil {
		return nil
	}
	return m
}

func nullIfZero(v int64, valid bool) interface{} {
	if !valid {
		return nil
	}
	return v
}

// mediaIDValue returns the pointer value as-is, or nil when absent — mirroring
// how toMessageResponse surfaces media/reply IDs without the zero-value trap.
func mediaIDValue(i *int64) interface{} {
	if i == nil {
		return nil
	}
	return *i
}

// contentType reports 'e2ee' when the message carries per-device envelopes,
// 'legacy_plaintext' for the plaintext path. Mirrors the migration's values.
func contentType(envelopes []EnvelopePayload) string {
	if len(envelopes) > 0 {
		return "e2ee"
	}
	return "legacy_plaintext"
}

func WebSocketHandler(db *sql.DB, tokenMgr *auth.TokenManager, pushService *push.Service) func(*websocket.Conn) {
	hub := NewHub(db, tokenMgr, pushService)
	go hub.Run()

	return func(c *websocket.Conn) {
		ctx, cancel := context.WithCancel(context.Background())
		client := &Client{
			hub:    hub,
			conn:   c,
			send:   make(chan []byte, sendBufSize),
			done:   make(chan struct{}),
			ctx:    ctx,
			cancel: cancel,
		}

		// WritePump owns all socket writes (messages + pings); ReadPump owns
		// all reads. Without a running WritePump nothing ever reaches the
		// client and idle connections die at the read deadline.
		go client.WritePump()
		client.ReadPump()
	}
}
