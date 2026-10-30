# WebSocket Protocol

Endpoint: `GET /ws` (upgrade). One connection per device. The server keeps a
per-user multi-device registry, so a message to one user fans out to all of that
user's live sockets.

Source of truth: `server/internal/websocket/handler.go`.
Client: `android/app/src/main/java/com/securechat/data/remote/websocket/WebSocketClient.kt`.

---

## 1. Framing

Every frame is a JSON object:

```json
{ "type": "MESSAGE_SEND", "id": "<optional correlation id>", "payload": { } }
```

`id` is echoed back on `MESSAGE_ACK` so the sender can match a server-assigned
message id to its local optimistic row.

## 2. Authentication

The upgrade must be authenticated **in-band**:

1. Open the socket. The connection is *not* registered yet and cannot act.
2. Send `AUTHENTICATE` with the access token.
3. The server validates the JWT signature, then re-checks that the session row
   is still live (`revoked_at IS NULL AND expires_at > NOW()`).
4. On success the server replies `AUTH_ACK` and registers the socket. On
   failure it replies `ERROR` and closes.

Any message other than `AUTHENTICATE` received before authentication is
rejected with `ERROR { code: "NOT_AUTHENTICATED" }`. This closes the hole where
a pre-auth socket could act as `userID = 0`.

An `Authorization: Bearer <token>` header is also sent by the Android client,
but the in-band handshake is what the server trusts.

## 3. Client → Server events

| Type | Payload | Notes |
|---|---|---|
| `AUTHENTICATE` | `{ access_token }` | Required first message |
| `MESSAGE_SEND` | `{ conversation_id, type, text, media_id?, reply_to_id?, temp_id }` | Membership re-checked per message |
| `TYPING_START` / `TYPING_STOP` | `{ conversation_id, user_id }` | `user_id` ignored; the session decides |
| `MESSAGE_READ` | `{ message_id, conversation_id }` | Message must belong to the conversation |
| `MESSAGE_DELIVERED` | `{ message_id, conversation_id }` | Same check |
| `MESSAGE_DELETE` | `{ message_id }` | Conversation resolved from the message; sender-only |

## 4. Server → Client events

| Type | Payload | Notes |
|---|---|---|
| `AUTH_ACK` | `{ user_id, device_id }` | Registration confirmed |
| `MESSAGE_ACK` | `{ temp_id, message_id, status, created_at }` | Sent to the originating socket |
| `MESSAGE_RECEIVED` | `{ message }` | Fanned out to other participants' devices |
| `MESSAGE_DELIVERED` / `MESSAGE_READ` | `{ message_id, conversation_id, ... }` | Receipts |
| `MESSAGE_DELETE` | `{ message_id, conversation_id, deleted_by }` | Scoped to the conversation |
| `TYPING_START` / `TYPING_STOP` | `{ conversation_id, user_id }` | Transient, not persisted |
| `USER_ONLINE` / `USER_OFFLINE` | `{ user_id, is_online, last_seen }` | Process-wide presence broadcast |
| `CONVERSATION_UPDATED` | `{ conversation }` | |
| `MEDIA_MESSAGE_CREATED` | `{ message }` | Reserved — not emitted. Attachments arrive as `MESSAGE_RECEIVED` |
| `ERROR` | `{ code, message }` | See codes below |

Timestamps are Unix **milliseconds** (`created_at`, `updated_at`,
`delivered_at`, `read_at`), matching the REST API.

### Media messages

An attachment is uploaded first (`POST /media/sign-upload` → Cloudinary →
`POST /media/complete`), and then sent as an ordinary `MESSAGE_SEND` with
`type: image | video | audio | document` and `media_id`. There is one message
path, not two.

The resulting `MESSAGE_RECEIVED` and `MESSAGE_ACK` frames carry the message with
`media_id` **and** a nested `media` object holding the full record
(`secure_url`, `mime_type`, `original_filename`, `file_size`, `width`,
`height`, `duration`, `sha256`, `thumbnail_url`). Embedding it is required, not
convenience: `GET /media/:id` is scoped to the uploader, so a recipient cannot
resolve a bare `media_id` into anything drawable.

## 5. Error codes

`INVALID_MESSAGE`, `INVALID_AUTH`, `AUTH_FAILED`, `SESSION_REVOKED`,
`NOT_AUTHENTICATED`, `NOT_PARTICIPANT`, `MESSAGE_FAILED`,
`MESSAGE_NOT_FOUND`, `MESSAGE_DELETE_FAILED`, `UNKNOWN_MESSAGE_TYPE`.

`NOT_PARTICIPANT` is returned whenever the sender's user is not an active
(`left_at IS NULL`) member of the conversation — for sends, receipts and
deletes alike.

## 6. Authorization model

Every inbound event is authorised against the **session**, never against
payload fields:

- Conversation membership is re-checked on each `MESSAGE_SEND`, `MESSAGE_READ`,
  `MESSAGE_DELIVERED` and `MESSAGE_DELETE`.
- `MESSAGE_READ` / `MESSAGE_DELIVERED` additionally verify the target message
  belongs to the claimed conversation, so a participant of conversation A cannot
  toggle a message in conversation B.
- `MESSAGE_DELETE` resolves the message's conversation first, then checks
  membership, then enforces sender ownership.

## 7. Connection lifecycle

- The server pings every 30s (`pingPeriod`); a connection that does not pong
  within 60s (`pongWait`) is dropped.
- Maximum inbound frame size is 64 KiB.
- Per-connection send buffer is 256 frames. A client that stops draining its
  socket is torn down rather than allowed to block broadcasters.
- Teardown is idempotent (`sync.Once` guards) and every teardown path funnels
  through `Client.shutdown()`, which closes the socket and cancels the
  connection's context so in-flight database work is abandoned.
- Every database call made on behalf of a socket is bounded by a 5s timeout
  (`dbTimeout`) *and* by the connection lifetime.
- The Android client reconnects with exponential backoff (1s → 30s) plus full
  jitter, and pings every 20s so a half-open socket is detected.

## 8. Offline behaviour

Sends made while the socket is down are queued client-side in an unbounded
channel and flushed on reconnect. A frame that the socket refuses is retained
and retried by the next session rather than dropped.

`/ws` is deliberately **not** behind the `/api/v1` rate limiter (it is not an
HTTP request path), so per-connection limits above are what bound abuse.
