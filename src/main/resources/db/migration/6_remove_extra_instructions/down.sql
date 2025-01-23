ALTER TABLE agent_configurations ADD COLUMN language_instruction TEXT;
ALTER TABLE agent_configurations ADD COLUMN prompt_instruction TEXT;

ALTER TABLE whats_app_agents ADD COLUMN language_instruction TEXT;
ALTER TABLE whats_app_agents ADD COLUMN prompt_instruction TEXT;
