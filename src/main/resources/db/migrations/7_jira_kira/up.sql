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
    user_id TEXT NOT NULL,
    api_key TEXT NOT NULL
);