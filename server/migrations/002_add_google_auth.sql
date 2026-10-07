-- +goose Up
-- Add Google OAuth columns to users. Google-created users have no phone number,
-- so phone_number becomes nullable. google_sub is the immutable Google subject
-- identifier used to match returning Google users.

ALTER TABLE users ADD COLUMN email VARCHAR(255) NULL;

ALTER TABLE users ADD COLUMN google_sub VARCHAR(255) NULL;

ALTER TABLE users ALTER COLUMN phone_number DROP NOT NULL;

ALTER TABLE users ADD CONSTRAINT uk_users_google_sub UNIQUE (google_sub);

ALTER TABLE users ADD CONSTRAINT uk_users_email UNIQUE (email);

-- +goose Down
ALTER TABLE users DROP CONSTRAINT uk_users_email;

ALTER TABLE users DROP CONSTRAINT uk_users_google_sub;

ALTER TABLE users DROP COLUMN google_sub;

ALTER TABLE users DROP COLUMN email;

ALTER TABLE users ALTER COLUMN phone_number SET NOT NULL;
