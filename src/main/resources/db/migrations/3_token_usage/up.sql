-- Keeps track of tokens spent.
CREATE TABLE token_usage(
    id SERIAL PRIMARY KEY,

    -- Entity ID from which the tokens originated from.
    workflow_id UUID NOT NULL,

    -- Type of origin.
    workflow_type TEXT NOT NULL,

    -- ID of the user who caused the usage.
    user_id TEXT NOT NULL,

    -- Username at the time of usage.
    username TEXT NOT NULL,

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

CREATE INDEX ON token_usage (user_id);
CREATE INDEX ON token_usage (workflow_id);
CREATE INDEX ON token_usage (workflow_type);
CREATE INDEX ON token_usage (usage_type);
CREATE INDEX ON token_usage (model);
CREATE INDEX ON token_usage (provider);
