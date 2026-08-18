package websocket

import (
	"context"
	"database/sql"
	"encoding/json"
	"sync"
	"time"

	"github.com/gofiber/websocket/v2"
	"github.com/securechat/server/internal/auth"
	"github.com/securechat/server/internal/messages"
	"github.com/securechat/server/pkg/logger"
	"go.uber.org/zap"
)

type Hub struct {
	clients    map[int64]map[*Client]bool // userID -> clients
	register   chan *Client
	unregister chan *Client
	broadcast  chan []byte
	mu         sync.RWMutex
	db         *sql.DB
	tokenMgr   *auth.TokenManager
}

type Client struct {
	hub      *Hub
	conn     *websocket.Conn
	send     chan []byte
	userID   int64
	deviceID int64
	sessionID int64
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
}

type MessageReadPayload struct {
	MessageID     int64 `json:"message_id"`
	ConversationID int64 `json:"conversation_id"`
}

type MessageDeletePayload struct {
	MessageID int64 `json:"message_id"`
}

type MessageAckPayload struct {
	TempID     string `json:"temp_id"`
	MessageID  int64  `json:"message_id"`
	Status     string `json:"status"`
	CreatedAt  int64  `json:"created_at"`
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
		broadcast:  make(chan []byte),
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
			logger.Log.Info("WebSocket client registered", zap.Int64("user_id", client.userID))

		case client := <-h.unregister:
			h.mu.Lock()
			if clients, ok := h.clients[client.userID]; ok {
				delete(clients, client)
				if len(clients) == 0 {
					delete(h.clients, client.userID)
				}
			}
			h.mu.Unlock()
			close(client.send)
			logger.Log.Info("WebSocket client unregistered", zap.Int64("user_id", client.userID))

		case message := <-h.broadcast:
			h.mu.RLock()
			for _, clients := range h.clients {
				for client := range clients {
					select {
					case client.send <- message:
					default:
						close(client.send)
						delete(clients, client)
					}
				}
			}
			h.mu.RUnlock()
		}
	}
}

func (c *Client) ReadPump() {
	defer func() {
		c.hub.unregister <- c
		c.conn.Close()
	}()

	c.conn.SetReadLimit(512)
	c.conn.SetReadDeadline(time.Now().Add(60 * time.Second))
	c.conn.SetPongHandler(func(string) error {
		c.conn.SetReadDeadline(time.Now().Add(60 * time.Second))
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
	ticker := time.NewTicker(30 * time.Second)
	defer func() {
		ticker.Stop()
		c.conn.Close()
	}()

	for {
		select {
		case message, ok := <-c.send:
			c.conn.SetWriteDeadline(time.Now().Add(10 * time.Second))
			if !ok {
				c.conn.WriteMessage(websocket.CloseMessage, []byte{})
				return
			}

			if err := c.conn.WriteMessage(websocket.TextMessage, message); err != nil {
				logger.Log.Error("WebSocket write error", zap.Error(err))
				return
			}
		case <-ticker.C:
			c.conn.SetWriteDeadline(time.Now().Add(10 * time.Second))
			if err := c.conn.WriteMessage(websocket.PingMessage, nil); err != nil {
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

	c.userID = claims.UserID
	c.deviceID = claims.DeviceID
	c.sessionID = claims.SessionID

	// Register client
	c.hub.register <- c

	// Send auth ack
	ack := WSMessage{
		Type: "AUTH_ACK",
		Payload: mustMarshal(AuthAckPayload{
			UserID:   claims.UserID,
			DeviceID: claims.DeviceID,
		}),
	}
	c.sendMessage(ack)

	// Update user online status
	c.hub.updateUserOnlineStatus(claims.UserID, true)
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

	// Broadcast to other participants
	c.hub.broadcastToConversation(msgSend.ConversationID, c.userID, WSMessage{
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

	eventType := "TYPING_START"
	if !isStart {
		eventType = "TYPING_STOP"
	}

	c.hub.broadcastToConversation(typing.ConversationID, c.userID, WSMessage{
		Type: eventType,
		Payload: mustMarshal(TypingPayload{
			ConversationID: typing.ConversationID,
		}),
	})
}

func (c *Client) handleMessageRead(payload json.RawMessage) {
	var read MessageReadPayload
	if err := json.Unmarshal(payload, &read); err != nil {
		return
	}

	// Update read status in DB
	msgRepo := messages.NewRepository(c.hub.db)
	msgRepo.UpdateReadStatus(context.Background(), read.MessageID)

	// Broadcast to conversation
	c.hub.broadcastToConversation(read.ConversationID, c.userID, WSMessage{
		Type: "MESSAGE_READ",
		Payload: mustMarshal(map[string]interface{}{
			"message_id":      read.MessageID,
			"conversation_id": read.ConversationID,
			"read_by":         c.userID,
			"read_at":         time.Now().Unix(),
		}),
	})
}

func (c *Client) handleMessageDelete(payload json.RawMessage) {
	var del MessageDeletePayload
	if err := json.Unmarshal(payload, &del); err != nil {
		return
	}

	// Soft delete in DB
	msgRepo := messages.NewRepository(c.hub.db)
	msgRepo.SoftDelete(context.Background(), del.MessageID, c.userID)

	// Broadcast to conversation
	c.hub.broadcastToConversation(0, c.userID, WSMessage{
		Type: "MESSAGE_DELETE",
		Payload: mustMarshal(map[string]interface{}{
			"message_id": del.MessageID,
			"deleted_by": c.userID,
		}),
	})
}

func (c *Client) sendMessage(msg WSMessage) {
	data, _ := json.Marshal(msg)
	select {
	case c.send <- data:
	default:
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

func (h *Hub) broadcastToConversation(conversationID, excludeUserID int64, msg WSMessage) {
	data, _ := json.Marshal(msg)

	h.mu.RLock()
	defer h.mu.RUnlock()

	if conversationID > 0 {
		// Get participants for this conversation
		rows, err := h.db.QueryContext(context.Background(), `
			SELECT user_id FROM conversation_participants WHERE conversation_id = ? AND left_at IS NULL
		`, conversationID)
		if err != nil {
			return
		}
		defer rows.Close()

		for rows.Next() {
			var userID int64
			if err := rows.Scan(&userID); err != nil {
				continue
			}
			if userID == excludeUserID {
				continue
			}
			if clients, ok := h.clients[userID]; ok {
				for client := range clients {
					select {
					case client.send <- data:
					default:
					}
				}
			}
		}
	} else {
		// Broadcast to all (for presence updates)
		for _, clients := range h.clients {
			for client := range clients {
				if client.userID != excludeUserID {
					select {
					case client.send <- data:
					default:
					}
				}
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
			"user_id":  userID,
			"last_seen": time.Now().Unix(),
		}),
	})
	h.broadcast <- data
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
		"id":             msg.ID,
		"conversation_id": msg.ConversationID,
		"sender_id":      msg.SenderID,
		"type":           msg.Type,
		"text":           msg.Text.String,
		"media_id":       msg.MediaID.Int64,
		"reply_to_id":    msg.ReplyToID.Int64,
		"status":         msg.Status,
		"created_at":     msg.CreatedAt.Unix(),
		"updated_at":     msg.UpdatedAt.Unix(),
	}
}

func WebSocketHandler(db *sql.DB, tokenMgr *auth.TokenManager) func(*websocket.Conn) {
	hub := NewHub(db, tokenMgr)
	go hub.Run()

	return func(c *websocket.Conn) {
		client := &Client{
			hub:  hub,
			conn: c,
			send: make(chan []byte, 256),
		}

		client.ReadPump()
	}
}