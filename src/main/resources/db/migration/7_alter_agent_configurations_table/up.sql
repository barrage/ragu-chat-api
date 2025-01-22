ALTER TABLE agent_configurations
    ADD COLUMN presence_penalty FLOAT NOT NULL DEFAULT 0.0,
    ADD COLUMN max_completion_tokens INTEGER DEFAULT NULL;

ALTER TABLE whats_app_agents
    ADD COLUMN presence_penalty FLOAT NOT NULL DEFAULT 0.0,
    ADD COLUMN max_completion_tokens INTEGER DEFAULT NULL;