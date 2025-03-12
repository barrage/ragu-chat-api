-- Mimics the `chats` table, except does not include the agent ID since
-- these are known in advance.
CREATE TABLE specialist_workflows (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id TEXT NOT NULL,
    username TEXT,
    type TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE specialist_message_groups (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workflow_id UUID NOT NULL REFERENCES specialist_workflows(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE specialist_messages (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    "order" INT NOT NULL,
    message_group_id UUID NOT NULL REFERENCES specialist_message_groups(id) ON DELETE CASCADE,
    sender_type TEXT NOT NULL,
    content TEXT,
    finish_reason TEXT,
    tool_calls TEXT,
    tool_call_id TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE specialist_message_group_evaluations (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    message_group_id UUID NOT NULL REFERENCES specialist_message_groups(id) ON DELETE CASCADE,
    evaluation BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_specialist_workflows_user_id ON specialist_workflows (user_id);
CREATE INDEX idx_specialist_workflows_type ON specialist_workflows (type);