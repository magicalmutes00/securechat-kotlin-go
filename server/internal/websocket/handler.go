package websocket

import (
	"context"
	"database/sql"
	"encoding/json"
	"sync"
	"time"

	"github.com/gofiber/websocket/v2"
	"github.com/securechat/server/internal/auth"
	"github.com/securechat/server/internal/media"
	"github.com/securechat/server/internal/messages"
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
)

type Hub struct {
	clients    map[int64]map[*Client]bool // userID -> clients (multi-device)
	register   chan *Client
	unregister chan *Client
	mu         sync.RWMutex
	db         *sql.DB
	tokenMgr   *auth.TokenManager
}

type Client struct {
	hub       *Hub
	conn      *websocket.Conn
	send      chan []byte
	closeOnce sync.Once
	mu        sync.Mutex // guards conn writes (write pump is the only writer)
	userID    int64
	deviceID  int64
	sessionID int64
	authed    bool
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

type MessageSendPayload struct {
	ConversationID int64  `json:"conversation_id"`
	Type           string `json:"type"`
	Text           string `json:"text,omitempty"`
	MediaID        *int64 `json:"media_id,omitempty"`
	ReplyToID      *int64 `json:"reply_to_id,omitempty"`
	TempID         string `json:"temp_id"`
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

func NewHub(db *sql.DB, tokenMgr *auth.TokenManager) *Hub {
	return &Hub{
		clients:    make(map[int64]map[*Client]bool),
		register:   make(chan *Client),
		unregister: make(chan *Client),
		db:         db,
		tokenMgr:   tokenMgr,
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
				h.updateUserOnlineStatus(client.userID, false)
			}
			logger.Log.Info("WebSocket client unregistered", zap.Int64("user_id", client.userID))
		}
	}
}

func (c *Client) closeSend() {
	c.closeOnce.Do(func() {
		close(c.send)
	})
}

func (c *Client) ReadPump() {
	defer func() {
		c.hub.unregister <- c
		c.conn.Close()
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
		c.conn.Close()
	}()

	for {
		select {
		case message, ok := <-c.send:
			c.mu.Lock()
			c.conn.SetWriteDeadline(time.Now().Add(writeWait))
			if !ok {
				// Hub closed our send channel: notify the client and exit.
				_ = c.conn.WriteMessage(websocket.CloseMessage, []byte{})
				c.mu.Unlock()
				return
			}
			if err := c.conn.WriteMessage(websocket.TextMessage, message); err != nil {
				c.mu.Unlock()
				logger.Log.Error("WebSocket write error", zap.Error(err))
				return
			}
			c.mu.Unlock()

		case <-ticker.C:
			c.mu.Lock()
			c.conn.SetWriteDeadline(time.Now().Add(writeWait))
			if err := c.conn.WriteMessage(websocket.PingMessage, nil); err != nil {
				c.mu.Unlock()
				return
			}
			c.mu.Unlock()
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
		c.conn.Close()
		return
	}

	// Verify session is still active
	var isActive bool
	err = c.hub.db.QueryRowContext(context.Background(), `
		SELECT EXISTS(SELECT 1 FROM sessions WHERE id = ? AND user_id = ? AND revoked_at IS NULL AND expires_at > NOW())
	`, claims.SessionID, claims.UserID).Scan(&isActive)
	if err != nil || !isActive {
		c.sendError("SESSION_REVOKED", "Session has been revoked")
		c.conn.Close()
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

		// Update user online status
		c.hub.updateUserOnlineStatus(claims.UserID, true)
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

	// Verify user is participant in conversation
	var isParticipant bool
	err := c.hub.db.QueryRowContext(context.Background(), `
		SELECT EXISTS(SELECT 1 FROM conversation_participants WHERE conversation_id = ? AND user_id = ? AND left_at IS NULL)
	`, msgSend.ConversationID, c.userID).Scan(&isParticipant)
	if err != nil || !isParticipant {
		c.sendError("NOT_PARTICIPANT", "Not a participant in this conversation")
		return
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
	}

	createdMsg, err := msgRepo.Create(context.Background(), msg)
	if err != nil {
		logger.Log.Error("Failed to create message", zap.Error(err))
		c.sendError("MESSAGE_FAILED", "Failed to send message")
		return
	}

	// Link uploaded media to the created message so the media row is reachable
	// from the message (media is uploaded before sending).
	if msgSend.MediaID != nil {
		mediaRepo := media.NewRepository(c.hub.db)
		if err := mediaRepo.LinkToMessage(context.Background(), *msgSend.MediaID, createdMsg.ID, c.userID); err != nil {
			logger.Log.Warn("Failed to link media to message",
				zap.Int64("media_id", *msgSend.MediaID),
				zap.Int64("message_id", createdMsg.ID),
				zap.Error(err))
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
			CreatedAt: createdMsg.CreatedAt.Unix(),
		}),
	}
	c.sendMessage(ack)

	// Broadcast to other participants (and the sender's other devices) so
	// multi-device clients stay in sync.
	c.hub.broadcastToConversation(msgSend.ConversationID, c, WSMessage{
		Type: "MESSAGE_RECEIVED",
		Payload: mustMarshal(MessageReceivedPayload{
			Message: toMessageResponse(createdMsg),
		}),
	})
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
	msgRepo := messages.NewRepository(c.hub.db)
	belongs, err := msgRepo.BelongsToConversation(context.Background(), read.MessageID, read.ConversationID)
	if err != nil || !belongs {
		c.sendError("INVALID_MESSAGE", "Message does not belong to this conversation")
		return
	}

	if err := msgRepo.UpdateReadStatus(context.Background(), read.MessageID); err != nil {
		logger.Log.Error("Failed to update read status", zap.Error(err))
	}

	// Broadcast to conversation (sender's other devices included)
	c.hub.broadcastToConversation(read.ConversationID, c, WSMessage{
		Type: "MESSAGE_READ",
		Payload: mustMarshal(map[string]interface{}{
			"message_id":      read.MessageID,
			"conversation_id": read.ConversationID,
			"read_by":         c.userID,
			"read_at":         time.Now().Unix(),
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

	msgRepo := messages.NewRepository(c.hub.db)
	belongs, err := msgRepo.BelongsToConversation(context.Background(), delivered.MessageID, delivered.ConversationID)
	if err != nil || !belongs {
		c.sendError("INVALID_MESSAGE", "Message does not belong to this conversation")
		return
	}

	if err := msgRepo.UpdateStatus(context.Background(), delivered.MessageID, "delivered"); err != nil {
		logger.Log.Error("Failed to update delivery status", zap.Error(err))
	}

	// Delivery receipts go back to the original sender (all their devices).
	c.hub.broadcastToConversation(delivered.ConversationID, c, WSMessage{
		Type: "MESSAGE_DELIVERED",
		Payload: mustMarshal(map[string]interface{}{
			"message_id":      delivered.MessageID,
			"conversation_id": delivered.ConversationID,
			"delivered_to":    c.userID,
			"delivered_at":    time.Now().Unix(),
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
	msg, err := msgRepo.GetByID(context.Background(), del.MessageID)
	if err != nil {
		c.sendError("MESSAGE_NOT_FOUND", "Message not found")
		return
	}

	if !c.isParticipant(msg.ConversationID) {
		c.sendError("NOT_PARTICIPANT", "Not a participant in this conversation")
		return
	}

	if err := msgRepo.SoftDelete(context.Background(), del.MessageID, c.userID); err != nil {
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

// isParticipant reports whether the client's user is an active participant of
// the conversation.
func (c *Client) isParticipant(conversationID int64) bool {
	var isParticipant bool
	err := c.hub.db.QueryRowContext(context.Background(), `
		SELECT EXISTS(SELECT 1 FROM conversation_participants WHERE conversation_id = ? AND user_id = ? AND left_at IS NULL)
	`, conversationID, c.userID).Scan(&isParticipant)
	return err == nil && isParticipant
}

func (c *Client) sendMessage(msg WSMessage) {
	data, _ := json.Marshal(msg)
	select {
	case c.send <- data:
	default:
		// Buffer full: drop the client rather than block the reader.
		c.hub.unregister <- c
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

	rows, err := h.db.QueryContext(context.Background(), `
		SELECT user_id FROM conversation_participants WHERE conversation_id = ? AND left_at IS NULL
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
			select {
			case client.send <- data:
			default:
				// Receiver too slow; skip rather than block everyone else.
			}
		}
	}
}

func (h *Hub) updateUserOnlineStatus(userID int64, isOnline bool) {
	h.db.ExecContext(context.Background(), `
		UPDATE users SET is_online = ?, last_seen = NOW() WHERE id = ?
	`, isOnline, userID)

	// Broadcast presence update
	data, _ := json.Marshal(WSMessage{
		Type: map[bool]string{true: "USER_ONLINE", false: "USER_OFFLINE"}[isOnline],
		Payload: mustMarshal(map[string]interface{}{
			"user_id":   userID,
			"is_online": isOnline,
			"last_seen": time.Now().Unix(),
		}),
	})

	h.mu.RLock()
	defer h.mu.RUnlock()
	for _, clients := range h.clients {
		for client := range clients {
			select {
			case client.send <- data:
			default:
			}
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
	return map[string]interface{}{
		"id":              msg.ID,
		"conversation_id": msg.ConversationID,
		"sender_id":       msg.SenderID,
		"type":            msg.Type,
		"text":            msg.Text.String,
		"media_id":        nullIfZero(msg.MediaID.Int64, msg.MediaID.Valid),
		"reply_to_id":     nullIfZero(msg.ReplyToID.Int64, msg.ReplyToID.Valid),
		"status":          msg.Status,
		"created_at":      msg.CreatedAt.Unix(),
		"updated_at":      msg.UpdatedAt.Unix(),
	}
}

func nullIfZero(v int64, valid bool) interface{} {
	if !valid {
		return nil
	}
	return v
}

func WebSocketHandler(db *sql.DB, tokenMgr *auth.TokenManager) func(*websocket.Conn) {
	hub := NewHub(db, tokenMgr)
	go hub.Run()

	return func(c *websocket.Conn) {
		client := &Client{
			hub:  hub,
			conn: c,
			send: make(chan []byte, sendBufSize),
		}

		// WritePump owns all socket writes (messages + pings); ReadPump owns
		// all reads. Without a running WritePump nothing ever reaches the
		// client and idle connections die at the read deadline.
		go client.WritePump()
		client.ReadPump()
	}
}
