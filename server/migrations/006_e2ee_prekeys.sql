-- +goose Up

-- E2EE phase E1: the server becomes a Signal Protocol prekey directory.
--
-- It stores PUBLIC key material only. Private keys are generated on the device
-- and never leave it, which is what makes the server a blind relay rather than a
-- participant in the protocol. See docs/e2ee-design.md.

-- Long-term public identity key, one row per device. The private half stays in
-- the Android Keystore-backed store.
CREATE TABLE device_identity_keys (
    device_id       BIGINT PRIMARY KEY,
    user_id         BIGINT NOT NULL,
    identity_key    BYTEA NOT NULL,
    registration_id INTEGER NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_dik_device FOREIGN KEY (device_id) REFERENCES devices(id) ON DELETE CASCADE,
    CONSTRAINT fk_dik_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT chk_dik_identity_key_len CHECK (octet_length(identity_key) = 32),
    CONSTRAINT chk_dik_registration_id CHECK (registration_id BETWEEN 0 AND 2147483647)
);

-- Signed prekeys: medium-term, rotated by the client. identity_key_signature is
-- produced by the device's identity key so a sender can verify the prekey has
-- not been substituted by the server.
CREATE TABLE device_signed_prekeys (
    device_id              BIGINT NOT NULL,
    key_id                 INTEGER NOT NULL,
    public_key             BYTEA NOT NULL,
    identity_key_signature BYTEA NOT NULL,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (device_id, key_id),
    CONSTRAINT fk_dsp_device FOREIGN KEY (device_id) REFERENCES devices(id) ON DELETE CASCADE,
    CONSTRAINT chk_dsp_public_key_len CHECK (octet_length(public_key) = 32),
    CONSTRAINT chk_dsp_signature_len CHECK (octet_length(identity_key_signature) = 64)
);
CREATE INDEX idx_dsp_device_created ON device_signed_prekeys (device_id, created_at DESC);

-- One-time prekeys: consumed exactly once by a claiming sender. Remaining stock
-- is exposed via GET /keys/count so clients know when to top up.
CREATE TABLE device_one_time_prekeys (
    device_id  BIGINT NOT NULL,
    key_id     INTEGER NOT NULL,
    public_key BYTEA NOT NULL,
    claimed_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (device_id, key_id),
    CONSTRAINT fk_dotp_device FOREIGN KEY (device_id) REFERENCES devices(id) ON DELETE CASCADE,
    CONSTRAINT chk_dotp_public_key_len CHECK (octet_length(public_key) = 32)
);
-- Partial index: the claim path only ever looks at unclaimed rows, and this is
-- the hot query when a conversation is opened.
CREATE INDEX idx_dotp_unclaimed
    ON device_one_time_prekeys (device_id, key_id)
    WHERE claimed_at IS NULL;

-- +goose Down

DROP INDEX IF EXISTS idx_dotp_unclaimed;
DROP TABLE IF EXISTS device_one_time_prekeys;
DROP TABLE IF EXISTS device_signed_prekeys;
DROP TABLE IF EXISTS device_identity_keys;
