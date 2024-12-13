CREATE TABLE whats_app_numbers (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    phone_number TEXT NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE whats_app_agents (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name TEXT NOT NULL,
    description TEXT,
    context TEXT NOT NULL,
    llm_provider TEXT NOT NULL,
    model TEXT NOT NULL,
    temperature FLOAT NOT NULL DEFAULT 0.1,
    language TEXT,
    active BOOLEAN NOT NULL DEFAULT FALSE,
    prompt_instruction TEXT,
    language_instruction TEXT,
    summary_instruction TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE whats_app_agent_collections(
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    agent_id UUID REFERENCES whats_app_agents(id) ON DELETE CASCADE,
    -- Specifies what the agent should do with the data obtained from this collection.
    instruction TEXT NOT NULL,

    -- The collection name
    collection TEXT NOT NULL,

    embedding_model TEXT NOT NULL,
    vector_provider TEXT NOT NULL,
    embedding_provider TEXT NOT NULL,

    -- Amount of items to retrieve from collection
    amount INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT unique_whats_app_agent_collection_provider UNIQUE(agent_id, collection, vector_provider)
);

CREATE TABLE whats_app_chats (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE whats_app_messages (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    sender UUID NOT NULL,
    sender_type TEXT NOT NULL,
    content TEXT NOT NULL,
    chat_id UUID NOT NULL REFERENCES whats_app_chats(id) ON DELETE CASCADE,
    response_to UUID REFERENCES whats_app_messages(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

SELECT manage_updated_at('whats_app_numbers');
SELECT manage_updated_at('whats_app_agents');
SELECT manage_updated_at('whats_app_agent_collections');
SELECT manage_updated_at('whats_app_chats');
SELECT manage_updated_at('whats_app_messages');

CREATE INDEX idx_whatsapp_numbers_user_id ON whats_app_numbers (user_id);
CREATE INDEX idx_whatsapp_numbers_phone_number ON whats_app_numbers (phone_number);
CREATE INDEX idx_whatsapp_agents_active ON whats_app_agents (active);