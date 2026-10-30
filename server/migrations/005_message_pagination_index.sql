-- +goose Up

-- Keyset pagination orders messages by (created_at DESC, id DESC) inside a
-- conversation. The original idx_messages_conv_created covered only
-- (conversation_id, created_at DESC), so PostgreSQL had to sort the candidate
-- rows to apply the id tiebreaker. Including id in the index makes the page
-- query a straight index scan.
CREATE INDEX idx_messages_conv_created_id
    ON messages (conversation_id, created_at DESC, id DESC);

-- +goose Down

DROP INDEX IF EXISTS idx_messages_conv_created_id;
