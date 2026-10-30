# HTTP API Specification

Base URL: `/api/v1`. All authenticated endpoints require
`Authorization: Bearer <access token>` and re-validate the session against the
database on every request, so logout and device revocation take effect
immediately rather than at token expiry.

Success envelope:

```json
{ "success": true, "data": { } }
```

Error envelope:

```json
{ "success": false, "error": { "code": "INVALID_OTP", "message": "..." } }
```

Timestamps are Unix **milliseconds**. IDs are `int64`.

Source of truth: `server/cmd/server/main.go` and each module's `handler.go`.

---

## Rate limiting

| Scope | Limit |
|---|---|
| `/api/v1` (all, per IP) | 300/min |
| `/auth/send-otp`, `/auth/verify-otp` (per IP) | 10/min (shared) |
| OTP resend, per phone number | 1 per 60s |
| OTP verification, per code | 5 attempts (atomic) |

`/ws` is not behind the HTTP limiter — see `websocket-protocol.md`.

---

## Authentication

### `POST /auth/send-otp`

| Field | Type | Notes |
|---|---|---|
| `phone_number` | string | E.164 |

Returns `expires_in` and `resend_cooldown`, both in seconds. Delivery is via
Twilio; the mock provider is dev-only and rejected in production.

### `POST /auth/verify-otp`

`phone_number`, `otp`. On success creates a device + session and returns the
user, an access token and a refresh token.

Errors: `INVALID_OTP`, `OTP_EXPIRED`, `OTP_MAX_ATTEMPTS` (429).

### `POST /auth/refresh`

`refresh_token`. Rotates the session's refresh token.

> **Retry semantics.** Rotation is a compare-and-swap on the stored hash. A
> concurrent double-submit returns **409 `REFRESH_CONFLICT`** — the client
> should retry with the token it already holds. This is deliberately *not*
> treated as reuse: outside a 30s grace window, presenting a rotated-out token
> revokes every session for the user.

### `POST /auth/google`, `POST /auth/firebase`

Exchange a Google ID token / Firebase ID token for a SecureChat session.

Firebase tokens are accepted only when `firebase.sign_in_provider == "phone"`;
a `phone_number` claim from any other provider is rejected.

### `POST /auth/logout`, `POST /auth/logout-all`

Authenticated. Revokes the current session, or every session for the user.

---

## Users

| Method | Path | Notes |
|---|---|---|
| `GET` | `/users/me` | |
| `PATCH` | `/users/me` | `display_name`, `username`, `profile_image_id` |
| `GET` | `/users/search?q=&limit=` | Profiles only; never exposes phone numbers |

---

## Conversations

| Method | Path | Notes |
|---|---|---|
| `GET` | `/conversations?limit=&offset=` | Ordered by newest message |
| `POST` | `/conversations` | 1:1 via `participant_id`/`participant_phone`; group via `type: "group"` + `name` + `participant_ids` |
| `GET` | `/conversations/:id` | |
| `PATCH` | `/conversations/:id` | `{ name }` — retitles a **group**; direct conversations have no name and answer `NOT_PARTICIPANT` |
| `POST` | `/conversations/:id/members` | `{ participant_ids }` — adds members to a **group**; already-present members are skipped so a retry is a no-op |
| `DELETE` | `/conversations/:id/members/:user_id` | Removes one member; removing yourself answers 400 `CANNOT_REMOVE_SELF` (use leave) |
| `DELETE` | `/conversations/:id` | Leaves the conversation |

Membership changes require the caller to be an active participant of a `group`
conversation; mutating the members of a 1:1 chat is refused, which is what stops
a member of a private conversation pulling strangers into it.

`GET /conversations` returns participants, the last message and an unread count
for every conversation **in two round trips** (page query + one grouped
participants query), not per conversation.

---

## Messages

### `GET /conversations/:id/messages?limit=&cursor=`

Returns `messages`, `has_more`, and `next_cursor` **only when `has_more` is
true**.

> **Cursor contract.** `next_cursor` is an opaque **string** of the form
> `"<created_at_micros>:<id>"`. Echo it back verbatim in `cursor`. It is not a
> timestamp: parsing it as a number is what previously made every message
> sharing the boundary second disappear from the next page. Ordering is
> `(created_at DESC, id DESC)`, a total order.

Errors: `NOT_PARTICIPANT`, `INVALID_REQUEST` (malformed cursor).

> **Attachments.** Every message object carries `media_id` and, when that id
> resolves, a nested `media` object with the full record (`id`,
> `resource_type`, `secure_url`, `original_filename`, `mime_type`, `file_size`,
> `width`, `height`, `duration`, `sha256`, `thumbnail_url`, `created_at`). The
> attachment is embedded rather than left to a follow-up `GET /media/:id`
> because that endpoint is owner-scoped: the recipient of a photo could never
> fetch the record it had just been told about, so the bubble could not render.
> A message whose media row is gone (deleted asset, `ON DELETE SET NULL`)
> simply omits the field.

### `DELETE /messages/:id`

Soft-deletes; only the sender may delete.

---

## Media

| Method | Path | Notes |
|---|---|---|
| `POST` | `/media/sign-upload` | Returns signed direct-upload params |
| `POST` | `/media/complete` | Verifies with Cloudinary, stores the record, returns it |
| `GET` | `/media/:id` | Owner only |
| `DELETE` | `/media/:id` | Owner only |

`POST /media/complete` returns the **media record**, not a message. Sending the
message is a separate step the client performs over the WebSocket: `MESSAGE_SEND`
with `type` set to `image` / `video` / `audio` / `document` and `media_id` set to
the id from this response. The `conversation_id` field on the completion request
is accepted for forward compatibility; the WebSocket send is what links the asset
to a conversation.

The API secret never reaches the client: the backend signs
`public_id`, `timestamp`, `folder`, `overwrite`, `unique_filename`,
`use_filename` and (for images/videos) `eager`. On completion the server
re-derives `secure_url` from Cloudinary's `Explicit` response rather than
trusting the client, and requires the `public_id` to sit under the caller's
signed folder prefix.

---

## Devices

| Method | Path | Notes |
|---|---|---|
| `GET` | `/devices` | |
| `DELETE` | `/devices/:id` | Cascades to that device's sessions |

---

## E2EE keys (`/keys`)

Phase E1 of the E2EE rollout — see `e2ee-design.md`. All of these are
authenticated, and the device a key belongs to comes from the **access token**,
never from the request body, so a client can only publish keys for the device it
is logged in on. Only *public* key material is accepted.

### `PUT /keys/identity`

`{ "identity_key": "<32 bytes>", "registration_id": <int32> }`

Publishes the device's long-term public identity key. Re-uploading the identical
key is idempotent.

> **409 `IDENTITY_KEY_CONFLICT`** is returned when a *different* key is already
> registered. The server refuses to overwrite an identity key silently: a
> replacement invalidates every session built on it and is indistinguishable
> from a substituted key.

### `PUT /keys/signed-prekey`

`{ "key_id": <int32>, "public_key": "<32 bytes>", "identity_key_signature": "<64 bytes>" }`

Publish or rotate. Rotation is a new `key_id`; re-uploading the same `key_id`
updates it.

### `POST /keys/one-time-prekeys`

`{ "keys": { "<key_id>": "<32 bytes>", ... } }`

Uploads a batch. Cap: **100 keys** per request. Re-uploading an existing
`key_id` is a no-op, so retries are safe.

### `GET /keys/count`

`{ "unclaimed": <int64> }` — how many one-time prekeys this device has left.
Clients use this to decide when to top up.

### `POST /keys/claim`

`{ "device_ids": [<int64>, ...] }` — cap: **100 ids**, deduplicated.

Returns:

```json
{
  "bundles": [
    {
      "device_id": 7,
      "identity_key": "...",
      "registration_id": 12345,
      "signed_pre_key_id": 3,
      "signed_pre_key": "...",
      "signed_pre_key_signature": "...",
      "has_one_time_pre_key": true,
      "one_time_pre_key_id": 91,
      "one_time_pre_key": "..."
    }
  ],
  "missing": [ { "device_id": 9, "reason": "no_keys_published" } ]
}
```

Claiming consumes one unclaimed one-time prekey per device. All three steps run
in a single transaction: the unclaimed rows are locked with
`SELECT … FOR UPDATE`, the lowest `key_id` per device is picked and marked
claimed, and the bundles are read — so two concurrent senders can never be
handed the same prekey.

> **Authorization.** Only devices whose user shares at least one conversation
> with the caller may be claimed; anything else answers 403
> `DEVICE_NOT_AUTHORIZED` in `missing` (reason `not_authorized`). Without this
> the endpoint would let anyone drain a stranger's one-time prekeys.

`missing` reasons: `unknown_device`, `not_authorized`, `no_keys_published`.

---

## Health

`GET /health` (unauthenticated) — liveness probe used by Render.
