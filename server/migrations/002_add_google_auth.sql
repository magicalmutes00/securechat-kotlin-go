-- +goose Up
-- Add Google OAuth columns to users. Google-created users have no phone number,
-- so phone_number becomes nullable. google_sub is the immutable Google subject
-- identifier used to match returning Google users.
--
-- TiDB note: ALTER TABLE statements are kept small and reference only columns
-- that already exist at execution time.

ALTER TABLE users
    ADD COLUMN email VARCHAR(255) NULL AFTER phone_number;

ALTER TABLE users
    ADD COLUMN google_sub VARCHAR(255) NULL AFTER email;

ALTER TABLE users
    MODIFY COLUMN phone_number VARCHAR(20) NULL;

ALTER TABLE users
    ADD UNIQUE KEY uk_users_google_sub (google_sub);

ALTER TABLE users
    ADD UNIQUE KEY uk_users_email (email);

-- +goose Down
ALTER TABLE users
    DROP KEY uk_users_email;

ALTER TABLE users
    DROP KEY uk_users_google_sub;

ALTER TABLE users
    DROP COLUMN google_sub;

ALTER TABLE users
    DROP COLUMN email;

ALTER TABLE users
    MODIFY COLUMN phone_number VARCHAR(20) NOT NULL;