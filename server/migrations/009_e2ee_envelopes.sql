-- +goose Up
-- E2EE phase E2: per-recipient-device message envelopes.
--
-- The server stores opaque ciphertext blobs it cannot read. Each message has one
-- envelope per destination device; the client encrypts the plaintext separately
-- for each device via the Signal Double Ratchet. The server is a blind relay.
--
-- messages.content_type marks whether a row carries plaintext (legacy) or an
-- envelope reference (e2ee). During E2 both paths coexist: E2EE clients write
-- envelopes (and optionally plaintext for fallback), legacy clients write plaintext.
-- E3 drops plaintext entirely.

ALTER TABLE messages ADD COLUMN content_type TEXT NOT NULL DEFAULT 'e2ee';
UPDATE messages SET content_type = 'legacy_plaintext' WHERE text IS NOT NULL;

CREATE TABLE message_envelopes (
    id                BIGSERIAL PRIMARY KEY,
    message_id        BIGINT NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
    recipient_device_id BIGINT NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    envelope_type     SMALLINT NOT NULL,
    ciphertext        BYTEA NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    delivered_at      TIMESTAMPTZ NULL,
    UNIQUE (message_id, recipient_device_id)
);
CREATE INDEX idx_envelopes_pending
    ON message_envelopes (recipient_device_id) WHERE delivered_at IS NULL;

-- +goose Down
DROP INDEX IF EXISTS idx_envelopes_pending;
DROP TABLE IF EXISTS message_envelopes;
ALTER TABLE messages DROP COLUMN content_type;
