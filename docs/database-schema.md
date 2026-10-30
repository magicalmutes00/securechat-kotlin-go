# Database Schema

PostgreSQL 16 (Neon in production), managed by [goose](https://github.com/pressly/goose).
Migrations live in `server/migrations/` and are applied automatically at server
startup (`cmd/server/main.go` runs `goose.Up`), so a deploy is also the migration
step.

All `updated_at` columns are maintained by the `set_updated_at()` trigger created
in migration 001 — PostgreSQL has no `ON UPDATE CURRENT_TIMESTAMP` equivalent.

> **Note on `conversations.updated_at`:** the trigger only fires on an UPDATE to
> that row, and nothing updates it when a message arrives. The conversation list
> therefore orders by newest message rather than by `updated_at`. See
> `conversations/repository.go`.

Source of truth: `server/migrations/*.sql`.

---

## Entity overview

```
users ──┬── devices ──┬── sessions
        │             ├── device_identity_keys      (E2EE, migration 006)
        │             ├── device_signed_prekeys      (E2EE, migration 006)
        │             └── device_one_time_prekeys    (E2EE, migration 006)
        ├── conversation_participants ── conversations ── messages ── media
        ├── user_settings
        └── otp_requests
```

---

## Migrations

| File | Purpose |
|---|---|
| `001_initial_schema.sql` | Core tables + `set_updated_at()` trigger |
| `002_add_google_auth.sql` | `users.email`, `users.google_sub`; `phone_number` becomes nullable |
| `003_fix_media_and_messages.sql` | `messages.status`; `media.user_id`, `media.thumbnail_url`; `media.message_id` nullable |
| `004_session_rotation_timestamp.sql` | `sessions.refresh_rotated_at` |
| `005_message_pagination_index.sql` | `idx_messages_conv_created_id` |
| `006_e2ee_prekeys.sql` | E2EE prekey directory (public keys only) |

### Why 004 exists

Refresh-token rotation and reuse-detection pull against each other. A client that
double-submits a refresh (retry, flaky network, two threads) presents a token
that was valid moments ago. Without a record of *when* the token was rotated, the
server cannot distinguish that from a genuinely stolen token being replayed — and
the wrong answer revokes every session the user owns. `refresh_rotated_at` bounds
the 30-second grace window in `auth.Service.RefreshToken`.

### Why 005 exists

Keyset pagination orders messages by `(created_at DESC, id DESC)`.
`idx_messages_conv_created` covered only `(conversation_id, created_at DESC)`, so
PostgreSQL had to sort candidate rows to apply the `id` tiebreaker. Including `id`
makes the page query a straight index scan.

---

## Tables

### `users`

| Column | Type | Notes |
|---|---|---|
| `id` | BIGSERIAL PK | |
| `phone_number` | VARCHAR(20) **NULL** | Nullable since 002 — Google users have none |
| `email` | VARCHAR(255) NULL | Unique (002) |
| `google_sub` | VARCHAR(255) NULL | Immutable Google subject; unique (002) |
| `username` | VARCHAR(50) NULL | Unique |
| `display_name` | VARCHAR(100) NOT NULL | |
| `profile_image_id` | BIGINT NULL | FK → `media.id` |
| `last_seen` | TIMESTAMPTZ NULL | |
| `is_online` | BOOLEAN | Denormalized presence; also updated by the WebSocket hub |
| `created_at`, `updated_at` | TIMESTAMPTZ | |

Indexes: unique phone, unique username, `idx_users_last_seen`.

### `devices`

| Column | Type | Notes |
|---|---|---|
| `id` | BIGSERIAL PK | |
| `user_id` | BIGINT | FK → users, CASCADE |
| `device_name` | VARCHAR(100) | |
| `device_identifier` | VARCHAR(255) | Unique per user |
| `platform` | VARCHAR(20) | Default `'android'` |
| `created_at`, `last_seen` | TIMESTAMPTZ | |

> Deleting a device cascades to its `sessions` rows — that cascade is what makes
> device revocation effective. If a cleanup job ever drops that FK, previously
> issued access tokens would keep validating.

### `sessions`

| Column | Type | Notes |
|---|---|---|
| `id` | BIGSERIAL PK | Referenced by the `session_id` JWT claim |
| `user_id`, `device_id` | BIGINT | FKs, CASCADE |
| `refresh_token_hash` | VARCHAR(255) | **SHA-256 digest**, not bcrypt (bcrypt's 72-byte cap is wrong for JWTs) |
| `expires_at` | TIMESTAMPTZ | |
| `revoked_at` | TIMESTAMPTZ NULL | |
| `refresh_rotated_at` | TIMESTAMPTZ NULL | 004 — bounds the refresh-reuse grace window |
| `created_at` | TIMESTAMPTZ | |

The `id` stays stable across rotations, which is why access tokens stay bound to
the same session.

### `conversations`

`id`, `type` (`direct`|`group`), `created_at`, `updated_at`.

### `conversation_participants`

Composite PK `(conversation_id, user_id)`, plus `joined_at` and `left_at`.
"Active member" everywhere in the codebase means `left_at IS NULL`.

### `messages`

| Column | Type | Notes |
|---|---|---|
| `id` | BIGSERIAL PK | |
| `conversation_id` | BIGINT | FK, CASCADE |
| `sender_id` | BIGINT | FK → users, CASCADE |
| `type` | TEXT | `text`|`image`|`video`|`audio`|`document` |
| `text` | TEXT NULL | **Plaintext today** — becomes opaque ciphertext in E2EE phase E3 |
| `media_id` | BIGINT NULL | FK → media, SET NULL |
| `reply_to_id` | BIGINT NULL | FK → messages, SET NULL |
| `status` | TEXT | `sent`|`delivered`|`read` (003) |
| `created_at`, `updated_at` | TIMESTAMPTZ | |
| `delivered_at`, `read_at`, `deleted_at` | TIMESTAMPTZ NULL | `deleted_at` is a soft delete |

Indexes: `(conversation_id, created_at DESC)`,
`(conversation_id, created_at DESC, id DESC)` (005), sender, reply, deleted, status.

> `messages.media_id → media(id) ON DELETE SET NULL` and
> `media.message_id → messages(id) ON DELETE CASCADE` form a cycle. Deleting a
> message row removes the media row, but leaving a conversation deletes nothing.

### `media`

`message_id` is nullable since 003: media is uploaded **before** the message that
references it exists, and is linked afterwards. `user_id` records the uploader and
drives the owner check on `GET/DELETE /media/:id`.

### `otp_requests`

`phone_number`, `otp_hash` (bcrypt), `expires_at`, `attempt_count`,
`verified_at`. Expired rows are purged hourly by a background goroutine.

> `attempt_count` is consumed **atomically** by
> `UPDATE … WHERE attempt_count < $max RETURNING …`, one statement per
> verification. A read-then-increment sequence here was raceable and let
> concurrency defeat the 5-attempt cap.

### `user_settings`

`user_id` PK, `theme` (`system`|`light`|`dark`), `notifications_enabled`,
`media_auto_download`, `language`.

---

## E2EE prekey tables (migration 006)

These hold **public** key material only. Private keys are generated on-device and
never leave it, which is what makes the server a blind relay rather than a
participant in the protocol. See `e2ee-design.md`.

### `device_identity_keys`

`device_id` PK (FK → devices, CASCADE), `user_id`, `identity_key BYTEA`,
`registration_id INTEGER`, `created_at`.

CHECK constraints enforce the wire sizes: `octet_length(identity_key) = 32` and
`registration_id BETWEEN 0 AND 2147483647`.

An existing identity key is never silently overwritten — a different key answers
409 `IDENTITY_KEY_CONFLICT`, because a replacement invalidates every session
built on it and is indistinguishable from a substituted key.

### `device_signed_prekeys`

PK `(device_id, key_id)`, `public_key BYTEA`, `identity_key_signature BYTEA`,
`created_at`. Sizes enforced: 32 and 64 bytes.

### `device_one_time_prekeys`

PK `(device_id, key_id)`, `public_key BYTEA`, `claimed_at TIMESTAMPTZ NULL`,
`created_at`.

`claimed_at` is set by the claiming sender. The partial index
`idx_dotp_unclaimed ON (device_id, key_id) WHERE claimed_at IS NULL` covers the
hot claim query.

> **Claim atomicity.** `POST /keys/claim` runs in one transaction: the unclaimed
> prekeys for the requested devices are locked with
> `SELECT … FOR UPDATE`, the lowest `key_id` per device is picked and marked
> `claimed_at = NOW()`, then the bundles are read. Two concurrent senders can
> never be handed the same prekey.---

## Index summary

| Index | Table | Serves |
|---|---|---|
| `idx_messages_conv_created_id` | messages | Keyset pagination (005) |
| `idx_messages_status` | messages | Read/delivery sweeps |
| `idx_cp_user` | conversation_participants | "my conversations" |
| `idx_users_last_seen` | users | Presence |
| `idx_otp_phone`, `idx_otp_expires` | otp_requests | Verification + hourly purge |
| `idx_sessions_user`, `idx_sessions_device`, `idx_sessions_expires`, `idx_sessions_revoked` | sessions | Session checks |
| `idx_dsp_device_created` | device_signed_prekeys | Latest signed prekey |
| `idx_dotp_unclaimed` | device_one_time_prekeys | Atomic claim |
