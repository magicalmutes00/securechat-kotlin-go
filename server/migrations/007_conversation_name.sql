-- +goose Up

-- Group conversations have a user-chosen name. Direct conversations keep it
-- NULL and continue to derive their title from the other participant, so a
-- single column serves both types without a join table.
--
-- The conversations.type CHECK already limits values to 'direct' and 'group'
-- (migration 001); this adds the attribute a group needs to be addressable.
ALTER TABLE conversations ADD COLUMN name VARCHAR(255) NULL;

-- +goose Down

ALTER TABLE conversations DROP COLUMN name;
