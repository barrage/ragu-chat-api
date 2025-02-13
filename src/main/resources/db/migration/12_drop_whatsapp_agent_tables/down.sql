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
