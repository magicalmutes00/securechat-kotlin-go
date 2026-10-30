-- +goose Up

-- Records when a session's refresh token was last rotated.
--
-- Refresh-token rotation and reuse detection are in tension: a client that
-- double-submits a refresh (retry, flaky network, two threads) presents a token
-- that was valid a moment ago. Without a timestamp the server cannot tell that
-- apart from a genuinely stolen token being replayed, and treating it as theft
-- revokes every session the user owns. The grace window in
-- auth.Service.RefreshToken consults this column to distinguish the two.
ALTER TABLE sessions ADD COLUMN refresh_rotated_at TIMESTAMPTZ NULL;

-- +goose Down

ALTER TABLE sessions DROP COLUMN refresh_rotated_at;
