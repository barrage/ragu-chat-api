-- Keeps track of the available tools for agents.
CREATE TABLE agent_tools(
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    agent_id UUID NOT NULL REFERENCES agents(id) ON DELETE CASCADE,
    tool_name TEXT NOT NULL
);

-- Keeps track of tools called during a conversation.
CREATE TABLE agent_tool_calls(
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    message_id UUID NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
    tool_name TEXT NOT NULL,
    tool_arguments TEXT NOT NULL,
    tool_result TEXT NOT NULL
);