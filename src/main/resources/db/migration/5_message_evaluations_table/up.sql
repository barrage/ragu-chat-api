CREATE TABLE message_evaluations (
     id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
     message_id UUID NOT NULL REFERENCES messages(id) ON DELETE CASCADE UNIQUE,
     evaluation BOOLEAN NOT NULL,
     feedback TEXT,
     created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
     updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
 );

SELECT manage_updated_at('message_evaluations');

CREATE INDEX idx_message_evaluations_message_id ON message_evaluations (message_id);
CREATE INDEX idx_message_evaluations_evaluation ON message_evaluations (evaluation);

-- Migrate existing evaluations data to new table
INSERT INTO message_evaluations (message_id, evaluation)
SELECT id, evaluation FROM messages WHERE evaluation IS NOT NULL;

-- Remove evaluation column from messages
ALTER TABLE messages DROP COLUMN evaluation;

DROP INDEX IF EXISTS idx_messages_evaluation;
