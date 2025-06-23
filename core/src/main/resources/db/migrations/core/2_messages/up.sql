-- Represents complete user->[tool]->assistant interactions. Wraps the user message and the assistant response,
-- along with any tools called in between.
-- This table should be used by workflows to track user interactions.
CREATE TABLE message_groups(
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),

    -- The parent workflow. Origin of the interaction.
    parent_id UUID NOT NULL,

    -- Parent workflow type. Used so we know which table to query for the parent.
    parent_type TEXT NOT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- An individual message in a message group.
CREATE TABLE messages (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),

     message_group_id UUID NOT NULL REFERENCES message_groups(id) ON DELETE CASCADE,

     -- The order of the message in the message group.
     -- The first message (order == 0) will always be the user message.
     -- The last message (order == n - 1) will always be the assistant message.
     -- Everything in between can be either the assistant message where it calls
     -- tools or tool messages.
     "order" INT NOT NULL,

     -- One of: user, assistant, system, tool
     sender_type TEXT NOT NULL,

     -- Message content, can be null only on assistant messages when the LLM calls tools.
     content TEXT,

     -- Reason why the LLM stopped completion.
     finish_reason TEXT,

     -- Present only on `assistant` messages in cases where agents decided to call tools.
     tool_calls TEXT,

     -- Present only on `tool` messages, the contents of the message is the tool result.
     tool_call_id TEXT,

     created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Binary data sent along user messages.
CREATE TABLE message_attachments(
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),

    -- Type of attachment, e.g. image, audio, etc.
    type TEXT NOT NULL,

    -- Provider of the binary attachment data.
    provider TEXT,

    -- The order in which the attachment was sent.
    "order" INT NOT NULL,

    -- URL specific to the provider, or a public URL if no provider is required.
    url TEXT NOT NULL,

    message_id UUID NOT NULL REFERENCES messages(id) ON DELETE CASCADE
);

-- Message group evaluations. The existence of an entry in this table means
-- a message group (i.e. an interaction) has been evaluated.
CREATE TABLE message_group_evaluations(
     id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
     message_group_id UUID NOT NULL REFERENCES message_groups(id) ON DELETE CASCADE UNIQUE,

     -- true == good, false == bad
     evaluation BOOLEAN NOT NULL,

     -- Evaluation description
     feedback TEXT,

     created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
     updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

SELECT manage_updated_at('message_group_evaluations');

CREATE INDEX ON message_groups (parent_id);
CREATE INDEX ON messages (message_group_id);
CREATE INDEX ON message_attachments (message_id);
CREATE INDEX ON message_group_evaluations (message_group_id);
CREATE INDEX ON message_group_evaluations (evaluation);