ALTER TABLE messages ADD COLUMN evaluation BOOLEAN;

CREATE INDEX idx_messages_evaluation ON messages (evaluation);

-- Migrate existing evaluations data to messages table
UPDATE messages SET evaluation = me.evaluation FROM message_evaluations me WHERE messages.id = me.message_id;

DROP INDEX IF EXISTS idx_message_evaluations_message_id;
DROP INDEX IF EXISTS idx_message_evaluations_evaluation;

DROP TRIGGER IF EXISTS set_updated_at ON message_evaluations;

DROP TABLE message_evaluations;

