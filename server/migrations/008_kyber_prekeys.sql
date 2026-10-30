-- +goose Up

-- Post-quantum (Kyber/ML-KEM-1024) one-time prekeys.
--
-- libsignal 0.76 performs PQXDH, not plain X3DH: a session is established with a
-- Kyber key alongside the X25519 ones, so a bundle without one cannot be used to
-- start a session at all. These are consumed once per claim, exactly like
-- device_one_time_prekeys.
--
-- Wire sizes are asserted in the client's SignalProtocolRoundTripTest and pinned
-- here: public 1569 bytes (1568 raw + 1 type byte), signature 64 (the X25519
-- identity key signs the Kyber public key). A library update that changes either
-- fails a client test rather than silently breaking registration.
CREATE TABLE device_kyber_prekeys (
    device_id              BIGINT NOT NULL,
    key_id                 INTEGER NOT NULL,
    public_key             BYTEA NOT NULL,
    identity_key_signature BYTEA NOT NULL,
    claimed_at             TIMESTAMPTZ NULL,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (device_id, key_id),
    CONSTRAINT fk_dkp_device FOREIGN KEY (device_id) REFERENCES devices(id) ON DELETE CASCADE,
    CONSTRAINT chk_dkp_public_key_len CHECK (octet_length(public_key) = 1569),
    CONSTRAINT chk_dkp_signature_len CHECK (octet_length(identity_key_signature) = 64)
);

-- The claim path only ever reads unclaimed rows.
CREATE INDEX idx_dkp_unclaimed
    ON device_kyber_prekeys (device_id, key_id)
    WHERE claimed_at IS NULL;

-- +goose Down

DROP INDEX IF EXISTS idx_dkp_unclaimed;
DROP TABLE IF EXISTS device_kyber_prekeys;
