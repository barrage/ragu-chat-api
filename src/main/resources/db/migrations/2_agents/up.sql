CREATE TABLE agents (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),

    -- Agent display name.
    name TEXT NOT NULL,

    -- Agent description for display purposes.
    description TEXT,

    -- If `true`, the agent is visible to non-admin users.
    active BOOLEAN NOT NULL,

    -- ID of the current configuration of the agent.
    active_configuration_id UUID NULL,

    -- Used for display purposes to hint the agent's language to the user.
    language TEXT,

    -- Avatar URL.
    avatar TEXT,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE agent_configurations (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4() NOT NULL,
    agent_id UUID REFERENCES agents(id) ON DELETE CASCADE NOT NULL,

    -- Current configuration version.
    version INTEGER NOT NULL,

    -- System message that gets sent every time the agent is used.
    context TEXT NOT NULL,

    -- Downstream LLM provider.
    llm_provider TEXT NOT NULL,

    -- The LLM. Has to be compatible with provider.
    model TEXT NOT NULL,

    -- Maximum number of tokens the LLM is allowed to generate. Overrides global setting.
    presence_penalty FLOAT,

    -- Maximum number of tokens the LLM is allowed to generate. Overrides global setting.
    max_completion_tokens INTEGER,

    -- Maximum number of tokens to keep in chat histories. Overrides global setting.
    max_history_tokens INTEGER,

    -- LLM LSD consumption amount.
    temperature FLOAT NOT NULL DEFAULT 0.1,

    -- The system message to set when generating a title for a chat.
    title_instruction TEXT,

    -- The default response for when the agent errors.
    error_message TEXT,

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

    CONSTRAINT agent_collections_unique_agent_collection UNIQUE(agent_id, collection, vector_provider)
);

-- Keeps track of the available tools for agents.
CREATE TABLE agent_tools(
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    agent_id UUID NOT NULL REFERENCES agents(id) ON DELETE CASCADE,

    -- The name of the tool. These are defined in the global tool registry, in the application.
    tool_name TEXT NOT NULL,

    CONSTRAINT agent_tools_unique_agent_tool UNIQUE(agent_id, tool_name)
);

-- Defines the visibility of agents in regards to user entitlements (groups).
CREATE TABLE agent_permissions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    agent_id UUID NOT NULL REFERENCES agents(id) ON DELETE CASCADE,

    -- The name of the entitlement (group) the agent is visible to.
    "group" TEXT NOT NULL,

    CONSTRAINT agent_groups_unique_agent_group UNIQUE(agent_id, "group")
);

SELECT manage_updated_at('agents');
SELECT manage_updated_at('agent_configurations');
SELECT manage_updated_at('agent_collections');

CREATE INDEX idx_agents_active ON agents (active);
CREATE INDEX idx_agent_collections_agent_id ON agent_collections (agent_id);
CREATE INDEX idx_agent_configurations_agent_id ON agent_configurations (agent_id);
CREATE INDEX idx_agent_tools_agent_id ON agent_tools (agent_id);
CREATE INDEX idx_agent_permissions_agent_id ON agent_permissions (agent_id);