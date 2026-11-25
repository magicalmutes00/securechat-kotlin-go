-- +goose Up

-- Push notification device tokens, one row per (user, device, token).
-- Tokens are registered by the Android client via POST /push/register and
-- removed via DELETE /push/token on logout or when the user disables
-- notifications. The server sends an FCM push to a recipient's active tokens
-- only when none of that recipient's devices has a live WebSocket connection.
CREATE TABLE push_tokens (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL,
    device_id   BIGINT NOT NULL,
    fcm_token   TEXT NOT NULL,
    platform    TEXT NOT NULL DEFAULT 'android',
    is_active   BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_pt_user   FOREIGN KEY (user_id)   REFERENCES users(id)   ON DELETE CASCADE,
    CONSTRAINT fk_pt_device FOREIGN KEY (device_id) REFERENCES devices(id) ON DELETE CASCADE,
    CONSTRAINT uk_pt_token UNIQUE (fcm_token)
);
CREATE INDEX idx_pt_user_active ON push_tokens (user_id, is_active);
CREATE INDEX idx_pt_device ON push_tokens (device_id);
CREATE TRIGGER trg_push_tokens_updated_at BEFORE UPDATE ON push_tokens
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- +goose Down

DROP TRIGGER IF EXISTS trg_push_tokens_updated_at ON push_tokens;
DROP INDEX IF EXISTS idx_pt_device;
DROP INDEX IF EXISTS idx_pt_user_active;
DROP TABLE IF EXISTS push_tokens;
