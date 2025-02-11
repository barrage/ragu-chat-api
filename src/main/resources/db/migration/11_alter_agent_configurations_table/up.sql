ALTER TABLE agent_configurations
    ADD COLUMN presence_penalty FLOAT,
    ADD COLUMN max_completion_tokens INTEGER;

ALTER TABLE whats_app_agents
    ADD COLUMN presence_penalty FLOAT,
    ADD COLUMN max_completion_tokens INTEGER;