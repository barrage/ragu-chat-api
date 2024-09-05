CREATE TABLE chats (
    id UUID PRIMARY KEY UNIQUE DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL,
    agent_id INTEGER NOT NULL,
    title VARCHAR(255) NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (agent_id) REFERENCES agents(id)
);