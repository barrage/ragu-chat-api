CREATE TABLE jira_kira_workflows (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE jira_kira_messages (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    -- Only present for user messages.
    sender UUID REFERENCES users(id) ON DELETE SET NULL,
    sender_type TEXT NOT NULL,
    content TEXT,
    tool_calls TEXT,
    tool_call_id TEXT,
    workflow_id UUID NOT NULL REFERENCES jira_kira_workflows(id) ON DELETE CASCADE,
    response_to UUID REFERENCES jira_kira_messages(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Descriptions of custom Jira worklog attributes. If an attribute is present here, it will be included
-- in tool definitions for creating worklog entries. Only static list type attributes are supported.
-- The enumeration of the values they can take is obtained from the Jira API when initializing JiraKira.
CREATE TABLE jira_worklog_attributes (
    -- The attribute identifier, or the attribute value as its called in Jira.
    id TEXT NOT NULL UNIQUE,
    -- The attribute description that will be used in tool definitions for JiraKira.
    description TEXT NOT NULL,
    -- Whether or not to specify the attribute as required in tool definitions.
    required BOOLEAN NOT NULL
);

CREATE TABLE jira_api_keys (
    user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    api_key TEXT NOT NULL
);