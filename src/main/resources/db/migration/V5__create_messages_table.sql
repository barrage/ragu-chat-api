CREATE TABLE messages (
    id UUID PRIMARY KEY UNIQUE DEFAULT uuid_generate_v4(),
    sender VARCHAR(255) NOT NULL,
    sender_type VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    chat_id UUID NOT NULL,
    response_to UUID NULL,
    evaluation BOOLEAN NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (chat_id) REFERENCES chats(id) ON DELETE CASCADE,
    FOREIGN KEY (response_to) REFERENCES messages(id) ON DELETE SET NULL
);