CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    email TEXT NOT NULL UNIQUE,
    full_name TEXT NOT NULL,
    first_name TEXT,
    last_name TEXT,
    role TEXT NOT NULL,
    active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMPTZ DEFAULT NULL
);

CREATE TABLE sessions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE agents (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name TEXT NOT NULL,
    description TEXT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    active_configuration_id UUID NULL,
    language TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE agent_configurations (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4() NOT NULL,
    agent_id UUID REFERENCES agents(id) ON DELETE CASCADE NOT NULL,
    version INTEGER NOT NULL,
    context TEXT NOT NULL,
    llm_provider TEXT NOT NULL,
    model TEXT NOT NULL,
    temperature FLOAT NOT NULL DEFAULT 0.1,
    prompt_instruction TEXT,
    title_instruction TEXT,
    language_instruction TEXT,
    summary_instruction TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE agent_collections (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    agent_id UUID NOT NULL REFERENCES agents(id) ON DELETE CASCADE,

    -- Specifies what the agent should do with the data obtained from this collection.
    instruction TEXT NOT NULL,

    -- The collection name.
    collection TEXT NOT NULL,

    -- Amount of items to retrieve from collection.
    amount INTEGER NOT NULL,

    -- Which model to use when embedding text for querying this collection.
    embedding_model TEXT NOT NULL,

    -- Which provider has the model.
    embedding_provider TEXT NOT NULL,

    -- Vector database the collection is stored in.
    vector_provider TEXT NOT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT unique_agent_collection UNIQUE(agent_id, collection, vector_provider)
);

CREATE TABLE chats (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    agent_id UUID NOT NULL REFERENCES agents(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id),
    title TEXT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE messages (
     id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
     sender UUID NOT NULL,
     sender_type TEXT NOT NULL,
     content TEXT NOT NULL,
     chat_id UUID NOT NULL REFERENCES chats(id) ON DELETE CASCADE,
     response_to UUID REFERENCES messages(id) ON DELETE SET NULL,
     evaluation BOOLEAN,
     created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
     updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
 );

CREATE TABLE failed_messages (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    chat_id UUID NOT NULL REFERENCES chats(id) ON DELETE CASCADE,
    fail_reason TEXT NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

SELECT manage_updated_at('users');
SELECT manage_updated_at('sessions');
SELECT manage_updated_at('agents');
SELECT manage_updated_at('agent_collections');
SELECT manage_updated_at('chats');
SELECT manage_updated_at('messages');
SELECT manage_updated_at('failed_messages');

CREATE INDEX idx_users_deleted_at ON users (deleted_at);
CREATE INDEX idx_agents_active ON agents (active);
CREATE INDEX idx_agent_collections_agent_id ON agent_collections (agent_id);
CREATE INDEX idx_sessions_user_id_expires_at ON sessions (user_id, expires_at);
CREATE INDEX idx_chats_user_id ON chats (user_id);
CREATE INDEX idx_chats_agent_id ON chats (agent_id);
CREATE INDEX idx_messages_chat_id ON messages (chat_id);
CREATE INDEX idx_messages_sender ON messages (sender);
CREATE INDEX idx_messages_evaluation ON messages (evaluation);