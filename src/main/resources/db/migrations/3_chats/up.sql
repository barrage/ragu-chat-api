-- Top level wrapper around user interactions with LLMs. Holds message groups.
CREATE TABLE chats (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    agent_id UUID NOT NULL REFERENCES agents(id) ON DELETE CASCADE,

    -- User ID specific to the authorization server
    user_id TEXT NOT NULL,

    -- Username at the time of chat creation
    username TEXT,

    -- Chat title
    title TEXT,

    -- Type of chat, i.e. RAG agent, whatsapp, JiraKira, etc.
    type TEXT NOT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Represents complete user->[tool]->assistant interactions. Wraps the user message and the assistant response,
-- along with any tools called in between.
CREATE TABLE message_groups(
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    chat_id UUID NOT NULL REFERENCES chats(id) ON DELETE CASCADE,

    -- The agent configuration at the time of the interaction
    agent_configuration_id UUID NOT NULL REFERENCES agent_configurations(id) ON DELETE CASCADE,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE messages (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),

     -- The order of the message in the message group.
     -- The first message (order == 0) will always be the user message.
     -- The last message (order == n - 1) will always be the assistant message.
     -- Everything in between can be either the assistant message where it calls
     -- tools or tool messages.
     "order" INT NOT NULL,

     message_group_id UUID NOT NULL REFERENCES message_groups(id) ON DELETE CASCADE,

     -- One of: user, assistant, system, tool
     sender_type TEXT NOT NULL,

     -- Message content, can be null if the assistant calls tools
     content TEXT,

     -- Reason why the LLM stopped completion
     finish_reason TEXT,

     -- Present only on `assistant` messages in cases where agents decided to call tools
     tool_calls TEXT,

     -- Present only on `tool` messages, the contents of the message is the tool result
     tool_call_id TEXT,

     created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
     updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
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

SELECT manage_updated_at('chats');
SELECT manage_updated_at('message_groups');
SELECT manage_updated_at('messages');
SELECT manage_updated_at('message_group_evaluations');

CREATE INDEX idx_chats_user_id ON chats (user_id);
CREATE INDEX idx_chats_agent_id ON chats (agent_id);
CREATE INDEX idx_chats_type ON chats (type);

CREATE INDEX idx_message_groups_chat_id ON message_groups (chat_id);
CREATE INDEX idx_message_groups_agent_config_id ON message_groups (agent_configuration_id);

CREATE INDEX idx_messages_group_id ON messages (message_group_id);

CREATE INDEX idx_message_group_evaluations_message_group_id ON message_group_evaluations (message_group_id);
CREATE INDEX idx_message_group_evaluations_evaluation ON message_group_evaluations (evaluation);