-- +goose Up

-- messages.repository.go reads/writes a status column that migration 001 never created.
ALTER TABLE messages
    ADD COLUMN status ENUM('sent', 'delivered', 'read') NOT NULL DEFAULT 'sent' AFTER reply_to_id;

-- Index for status lookups (e.g. unread/delivered sweeps).
ALTER TABLE messages
    ADD INDEX idx_messages_status (status);

-- media.repository.go inserts user_id and selects thumbnail_url; the media table
-- only had message_id. Media is uploaded before a message exists, so message_id
-- becomes nullable and is linked after the message is created.
ALTER TABLE media
    ADD COLUMN user_id BIGINT UNSIGNED NULL AFTER id,
    ADD COLUMN thumbnail_url VARCHAR(500) NULL AFTER sha256;

ALTER TABLE media
    MODIFY message_id BIGINT UNSIGNED NULL;

ALTER TABLE media
    ADD INDEX idx_media_user (user_id),
    ADD CONSTRAINT fk_media_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

-- +goose Down

ALTER TABLE media
    DROP FOREIGN KEY fk_media_user,
    DROP INDEX idx_media_user,
    DROP COLUMN user_id,
    DROP COLUMN thumbnail_url;

ALTER TABLE messages
    DROP INDEX idx_messages_status,
    DROP COLUMN status;
