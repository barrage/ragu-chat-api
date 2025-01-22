ALTER TABLE whats_app_agents
    DROP COLUMN IF EXISTS presence_penalty,
    DROP COLUMN IF EXISTS max_completion_tokens;

ALTER TABLE agent_configurations
    DROP COLUMN IF EXISTS presence_penalty,
    DROP COLUMN IF EXISTS max_completion_tokens;