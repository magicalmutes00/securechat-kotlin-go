# End-to-End Encryption Design (Signal Protocol)

Status: **proposed — awaiting one architecture decision** (see
[Decision required](#decision-required)).

SecureChat currently encrypts nothing end-to-end. `messages.text` holds
plaintext in PostgreSQL, media sits unencrypted in Cloudinary, and the server
can read every conversation. The README is honest about this ("End-to-end
message encryption is not implemented yet"). This document specifies how to
close that gap with the Signal Protocol — the same construction WhatsApp uses.

---

## 1. What "Signal Protocol" actually means

Four distinct mechanisms, often conflated:

| Mechanism | Purpose | Used for |
|---|---|---|
| **X3DH** (Extended Triple Diffie–Hellman) | Asynchronous session setup — Alice can start a session with Bob while Bob is offline | First message in a 1:1 chat |
| **Double Ratchet** | Per-message keys; forward secrecy + post-compromise security | Every subsequent 1:1 message |
| **Sender Keys** | One symmetric ratchet fanned out to a group, instead of N pairwise ratchets | Group chats |
| **Sealed Sender / safety numbers** | Hide the sender from the server; let users verify no MITM | Metadata + verification UI |

WhatsApp uses X3DH + Double Ratchet for 1:1 and Sender Keys for groups. That is
the target.

---

## 2. Decision required

**Where does the cryptography run?**

### Option A — Client-only crypto; the server becomes a blind relay (recommended)

All Signal Protocol operations happen on-device in Kotlin via the **official
`org.signal:libsignal-client`** artifact (published by Signal to Maven; it is
the real implementation, not a reimplementation). The Go server never sees a
private key, never derives a session key, and never decrypts anything.

The server's role shrinks to two jobs:

1. **Prekey directory** — store and hand out *public* identity keys and prekey
   bundles so a sender can build a session with an offline recipient.
2. **Blind envelope relay** — store and forward opaque ciphertext blobs.

Why this is the right answer, not just the easy one:

- It is how real E2EE messengers are built. A server that *can* decrypt is a
  server that can be compelled to, and one bug there is a total compromise.
- There is **no official libsignal for Go.** The Go options today are community
  ports of varying freshness (`go.mau.fi/libsignal`, `signal-golang`) or CGo
  bindings to the Rust library. Option A means the server needs **none** of
  them, so we keep the pure-Go build and the current Render deploy.
- Key material never crosses the network, so there is no server-side key
  storage to get wrong.

### Option B — server participates in the protocol

Only makes sense if we ever want server-side search, previews, or AI over
message content. It requires CGo + the Rust libsignal in the server image,
breaks the current build/deploy story, and means private keys or session state
live on the server. **Not recommended.**

**Everything below assumes Option A.**

---

## 3. Tradeoffs to accept (WhatsApp has the same ones)

These are consequences of the design, not bugs to fix later:

- **Server-side search over message text is impossible.** Search must move
  on-device (over the local Room cache). The current `ILIKE '%q%'` user search
  is unaffected — it searches profiles, not messages.
- **`internal/ai` (Ollama smart replies) can no longer read messages.** It
  becomes an on-device feature or is dropped. The Ollama config is currently
  parsed but unused, so nothing regresses today.
- **Notification previews must be produced on-device.** The server cannot send
  "Alice: see you at 8". This is exactly why WhatsApp renders previews locally
  after decrypting, and shows a generic notification otherwise.
- **Media must be encrypted client-side before upload.** Cloudinary then stores
  ciphertext only (see §6).
- **Multi-device fan-out cost.** A message to a user with 3 devices is 3
  envelopes. Group × devices grows multiplicatively — client-side sender-key
  distribution, same as WhatsApp.
- **Metadata is not protected.** Who talks to whom, when, and how much remains
  visible to the server. Sealed Sender is a separate, later project.

---

## 4. Server-side changes

### 4.1 New tables

```sql
-- A device's long-term public identity key. The private half never leaves the
-- device. One row per (user, device).
CREATE TABLE device_identity_keys (
    device_id       BIGINT PRIMARY KEY REFERENCES devices(id) ON DELETE CASCADE,
    user_id         BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    identity_key    BYTEA NOT NULL,   -- public Curve25519 identity key
    registration_id INTEGER NOT NULL, -- Signal protocol registration id
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Signed prekeys: medium-term, rotated periodically (Signal uses ~weekly).
CREATE TABLE device_signed_prekeys (
    device_id     BIGINT PRIMARY KEY REFERENCES devices(id) ON DELETE CASCADE,
    key_id        INTEGER NOT NULL,
    public_key    BYTEA NOT NULL,
    signature     BYTEA NOT NULL,     -- signed by the identity key
    created_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- One-time prekeys: consumed exactly once by a claiming sender. Remaining
-- stock is visible to clients so they can top up.
CREATE TABLE device_one_time_prekeys (
    device_id  BIGINT NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    key_id     INTEGER NOT NULL,
    public_key BYTEA NOT NULL,
    claimed_at TIMESTAMPTZ NULL,
    PRIMARY KEY (device_id, key_id)
);
CREATE INDEX idx_otpk_unclaimed ON device_one_time_prekeys (device_id) WHERE claimed_at IS NULL;

-- Per-recipient-device ciphertext. A message is no longer one row with text;
-- it is one row per destination device, each independently encrypted.
CREATE TABLE message_envelopes (
    id                BIGSERIAL PRIMARY KEY,
    message_id        BIGINT NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
    recipient_device_id BIGINT NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    -- Signal "type" byte (PreKeySignalMessage vs SignalMessage) and the
    -- serialized ciphertext. Opaque to the server.
    envelope_type     SMALLINT NOT NULL,
    ciphertext        BYTEA NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    delivered_at      TIMESTAMPTZ NULL,
    UNIQUE (message_id, recipient_device_id)
);
CREATE INDEX idx_envelopes_pending ON message_envelopes (recipient_device_id) WHERE delivered_at IS NULL;
```

### 4.2 Migration of `messages`

`messages.text` must stop carrying plaintext.

```sql
ALTER TABLE messages ADD COLUMN content_type TEXT NOT NULL DEFAULT 'e2ee';
-- Existing plaintext rows are pre-E2EE history. Keep them readable in-app by
-- marking them explicitly rather than pretending they are encrypted.
UPDATE messages SET content_type = 'legacy_plaintext' WHERE text IS NOT NULL;
```

`media.cloudinary_public_id` / `secure_url` become ciphertext locations, and the
per-file key is delivered inside the message envelope, never to the server.

### 4.3 New endpoints

| Method | Path | Purpose |
|---|---|---|
| `PUT` | `/api/v1/keys/identity` | Publish this device's identity key + registration id |
| `PUT` | `/api/v1/keys/signed-prekey` | Publish/rotate the signed prekey |
| `POST` | `/api/v1/keys/one-time-prekeys` | Upload a batch of one-time prekeys |
| `GET` | `/api/v1/keys/count` | How many unclaimed one-time prekeys remain (top-up trigger) |
| `POST` | `/api/v1/keys/claim` | Claim a bundle for a set of recipient devices (atomically consumes one one-time prekey per device) |
| `GET` | `/api/v1/users/:id/devices` | List a recipient's devices, so the sender knows the fan-out set |

`POST /keys/claim` is the only place needing care: it must consume one-time
prekeys atomically so two senders can never be handed the same prekey.

```sql
-- One statement; the sub-select locks the row it hands out.
UPDATE device_one_time_prekeys
SET claimed_at = NOW()
WHERE (device_id, key_id) IN (
    SELECT device_id, key_id FROM device_one_time_prekeys
    WHERE device_id = ANY($1) AND claimed_at IS NULL
    ORDER BY device_id, key_id
    FOR UPDATE SKIP LOCKED
)
RETURNING device_id, key_id, public_key;
```

### 4.4 WebSocket changes

`MESSAGE_SEND` currently carries `text`. It becomes:

```json
{
  "type": "MESSAGE_SEND",
  "id": "<temp_id>",
  "payload": {
    "conversation_id": 42,
    "envelopes": [
      { "recipient_device_id": 7, "envelope_type": 3, "ciphertext": "<base64>" },
      { "recipient_device_id": 9, "envelope_type": 1, "ciphertext": "<base64>" }
    ],
    "temp_id": "<temp_id>"
  }
}
```

The server validates that every `recipient_device_id` belongs to a participant
of the conversation (it already does the participant check), stores the
envelopes, and fans them out. It cannot and does not inspect `ciphertext`.
`MESSAGE_RECEIVED` likewise carries the envelope for the receiving device only.

### 4.5 What to delete from the server

- Reading `messages.text` anywhere in the API surface (`toMessageResponse`,
  conversation previews, WebSocket `toMessageResponse`).
- The `text` field from `MessageSendPayload` handling.
- Any future server-side AI/search over message bodies.

---

## 5. Client-side changes (Android)

### 5.1 Library

```kotlin
implementation("org.signal:libsignal-client:0.x.y")   // version to pin at implementation time
```

This provides `IdentityKeyPair`, `PreKeyBundle`, `SessionBuilder`,
`SessionCipher`, `GroupCipher`/`SenderKeyDistributionMessage`, and
`Fingerprint` (safety numbers). Do **not** hand-roll X3DH or the ratchet.

### 5.2 New local storage (Room)

Session state must persist across app restarts and is per-peer-device:

- `signal_sessions` — serialized ratchet state, keyed by
  (peerUserId, peerDeviceId).
- `signal_identity` — this device's identity key pair + registration id,
  stored **in the Android Keystore-backed encrypted store**, not plain Room.
  Room is acceptable for the SQLCipher-less case only if the DB file is
  protected; safest is Keystore-wrapped keys in `EncryptedSharedPreferences`.
- `signal_sender_keys` — per-group sender-key state.
- `prekey_store` — the private halves of our own one-time and signed prekeys.

Room version bump to 4 with a real migration; the existing migration style
(`MIGRATION_2_3`) is the pattern to follow.

### 5.3 Flow

**Registration** (once per device, after login):
1. Generate identity key pair, registration id, signed prekey, and a batch of
   ~100 one-time prekeys.
2. Publish the **public** halves via `/keys/*`.
3. Keep private halves locally.

**Sending a 1:1 message**:
1. Look up the recipient's device list.
2. For each device without a session: `POST /keys/claim` → `PreKeyBundle` →
   `SessionBuilder.process()` → session exists.
3. `SessionCipher.encrypt(plaintext)` per device.
4. Send all envelopes in one `MESSAGE_SEND`.

**Receiving**:
1. `MESSAGE_RECEIVED` arrives with an envelope addressed to this device.
2. `PreKeySignalMessage` → `SessionBuilder.process()` (completes X3DH).
   `SignalMessage` → `SessionCipher.decrypt()`.
3. Store plaintext in the local Room cache **only**; never send it back.

**Groups**: `SenderKeyDistributionMessage` is fanned out pairwise (encrypted
with each pairwise session), then messages are encrypted once with the sender
key. Membership changes force redistribution.

### 5.4 Media

Per file: random 32-byte key + 16-byte IV; AES-256-GCM encrypt before upload;
upload ciphertext to Cloudinary; put `{key, iv, sha256, mimeType, size}` inside
the (already encrypted) message payload. Recipients decrypt after download.
`CloudinaryUploadHelper` currently buffers whole files in memory — that must
become streaming **before** encryption is layered on, or memory use doubles.

### 5.5 Verification UI (safety numbers)

Show a 60-digit fingerprint per contact (`Fingerprint`), with a "scan QR" path.
Warn on identity-key change — that is the only defence against a
server-substituted key.

---

## 6. Phasing

| Phase | Deliverable | Depends on |
|---|---|---|
| **E1** | Prekey tables + endpoints + atomic claim; client key generation and publication. No message changes yet — old plaintext path keeps working. | — |
| **E2** | 1:1 X3DH + Double Ratchet over WebSocket; envelopes stored and fanned out; plaintext `text` still written for fallback during rollout. | E1 |
| **E3** | Cut over: stop writing `messages.text`; on-device search and notifications; delete server-side plaintext reads. | E2 |
| **E4** | Group Sender Keys. | E2 |
| **E5** | Media encryption + streaming upload/download rewrite. | E2, media refactor |
| **E6** | Safety-number verification UI; identity-change warnings. | E2 |
| **E7** | Multi-device: envelope fan-out to all of a user's devices, device linking. | E2 |

E1 and E2 are independently shippable and do not break the current app. E3 is
the irreversible step and should not land until E2 has real-device testing.

---

## 7. Testing

- **Frozen test vectors**: libsignal ships test vectors. Assert our storage and
  session-serialization round-trips against them; do not invent our own crypto
  tests.
- **Cross-device harness**: an instrumented test that establishes a session
  between two emulated clients and exchanges messages, then simulates a
  ratchet step (out-of-order delivery, dropped message, replayed message).
- **Server tests**: `POST /keys/claim` under concurrency must never hand the
  same one-time prekey to two callers — a parallel test hitting the endpoint
  N times and asserting N distinct prekeys.
- **Negative tests**: a tampered ciphertext must fail; a replayed envelope must
  not double-deliver; an identity-key change must surface a warning.

---

## 8. Open questions for the maintainer

1. **Confirm Option A** (client-only crypto, server as blind relay) — this is
   the single decision that gates all E2EE work.
2. Accept losing server-side search, notification previews, and server-side AI?
3. Is multi-device in scope for the first E2EE release, or is single-device
   acceptable initially? (Multi-device roughly triples E2's complexity.)
4. Media encryption in the first E2EE release, or text-only first with media
   marked as not-yet-E2EE in the UI?
