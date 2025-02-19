CREATE TABLE token_usage(
    id SERIAL PRIMARY KEY,
    -- The user who initiated the usage.
    user_id UUID REFERENCES users(id) ON DELETE SET NULL,

    -- The agent which was used.
    agent_id UUID REFERENCES agents(id) ON DELETE SET NULL,

    -- The active configuration when the tokens were used.
    agent_configuration_id UUID REFERENCES agent_configurations(id) ON DELETE SET NULL,

    -- The originating entity which caused the usage, i.e. chat, whatsapp chat, etc.
    origin TEXT NOT NULL,

    -- The origin's ID.
    origin_id UUID,

    -- The amount of tokens used.
    amount INTEGER NOT NULL,

    -- The type of usage, i.e. embedding, completion, etc.
    usage_type TEXT NOT NULL,

    -- The embedding model or LLM that was used.
    model TEXT NOT NULL,

    -- The provider of the model.
    provider TEXT NOT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);