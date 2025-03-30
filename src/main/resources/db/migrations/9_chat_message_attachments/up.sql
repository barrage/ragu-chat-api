CREATE TABLE message_attachments(
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    type TEXT NOT NULL,
    provider TEXT,
    "order" INT NOT NULL,
    url TEXT NOT NULL,
    message_id UUID NOT NULL REFERENCES messages(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_message_attachments_message_id ON message_attachments (message_id);