CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE OR REPLACE FUNCTION set_updated_at() RETURNS TRIGGER AS $$
BEGIN
    IF (
        NEW IS DISTINCT FROM OLD AND
        NEW.updated_at IS NOT DISTINCT FROM OLD.updated_at
    ) THEN
        NEW.updated_at := current_timestamp;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION manage_updated_at(_tbl regclass) RETURNS VOID AS $$
BEGIN
    EXECUTE format('CREATE TRIGGER set_updated_at BEFORE UPDATE ON %s FOR EACH ROW EXECUTE PROCEDURE set_updated_at()', _tbl);
END;
$$ LANGUAGE plpgsql;

CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    email TEXT NOT NULL UNIQUE,
    full_name TEXT NOT NULL,
    first_name TEXT,
    last_name TEXT,
    role TEXT NOT NULL,
    active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE sessions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(id),
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE agents (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),

    -- User friendly agent name
    name TEXT NOT NULL,

    -- User friendly agent description
    description TEXT,

    -- Agent context used when prompting
    context TEXT NOT NULL,

    -- LLM provider, e.g. openai, azure, etc.
    llm_provider TEXT NOT NULL,

    -- LLM the agent uses
    model TEXT NOT NULL,

    -- LLM LSD consumption amount
    temperature FLOAT NOT NULL DEFAULT 0.1,

    -- Vector database provider, e.g. weaviate, qdrant, etc.
    vector_provider TEXT NOT NULL,

    -- Conversation language
    language TEXT NOT NULL,

    -- Soft deletion flag
    active BOOLEAN NOT NULL DEFAULT TRUE,

    -- Which embedding provider to use, e.g. azure, fembed
    embedding_provider TEXT NOT NULL,

    -- Which embedding model to use, must be supported by provider
    embedding_model TEXT NOT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE agent_collections(
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    agent_id UUID REFERENCES agents(id) ON DELETE CASCADE,

    -- The collection name
    collection TEXT NOT NULL,

    -- Amount of items to retrieve from collection
    amount INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT unique_agent_collection UNIQUE(agent_id, collection)
);

CREATE TABLE chats (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    agent_id UUID NOT NULL REFERENCES agents(id),
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
