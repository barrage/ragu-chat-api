CREATE TABLE token_usage(
    id SERIAL PRIMARY KEY,
    agent_id UUID REFERENCES agents(id) ON DELETE SET NULL,
    agent_configuration_id UUID REFERENCES agent_configurations(id) ON DELETE SET NULL,

    user_id TEXT,

    username TEXT,

    -- The originating entity which caused the usage, i.e. chat, whatsapp chat, etc.
    origin TEXT NOT NULL,

    -- The origin's ID.
    origin_id UUID,

    -- The amount of prompt tokens used.
    prompt_tokens_amount INTEGER,

    -- The amount of completion tokens used.
    completion_tokens_amount INTEGER,

    -- The total amount of tokens used.
    total_tokens_amount INTEGER,

    -- The type of usage, i.e. embedding, completion, etc.
    usage_type TEXT NOT NULL,

    -- The embedding model or LLM that was used.
    model TEXT NOT NULL,

    -- The provider of the model.
    provider TEXT NOT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);