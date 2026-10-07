-- +goose Up

-- messages.repository.go reads/writes a status column that migration 001 never created.
ALTER TABLE messages
    ADD COLUMN status TEXT NOT NULL DEFAULT 'sent'
    CONSTRAINT chk_messages_status CHECK (status IN ('sent', 'delivered', 'read'));

-- Index for status lookups (e.g. unread/delivered sweeps).
CREATE INDEX idx_messages_status ON messages (status);

-- media.repository.go inserts user_id and selects thumbnail_url; the media table
-- only had message_id. Media is uploaded before a message exists, so message_id
-- becomes nullable and is linked after the message is created.
ALTER TABLE media ADD COLUMN user_id BIGINT NULL;
ALTER TABLE media ADD COLUMN thumbnail_url VARCHAR(500) NULL;

ALTER TABLE media ALTER COLUMN message_id DROP NOT NULL;

CREATE INDEX idx_media_user ON media (user_id);
ALTER TABLE media
    ADD CONSTRAINT fk_media_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

-- +goose Down

ALTER TABLE media DROP CONSTRAINT fk_media_user;
DROP INDEX IF EXISTS idx_media_user;
ALTER TABLE media DROP COLUMN user_id;
ALTER TABLE media DROP COLUMN thumbnail_url;
ALTER TABLE media ALTER COLUMN message_id SET NOT NULL;

DROP INDEX IF EXISTS idx_messages_status;
ALTER TABLE messages DROP COLUMN status;
