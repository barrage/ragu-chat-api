ALTER TABLE agent_configurations ADD COLUMN error_message TEXT;
ALTER TABLE messages ADD COLUMN finish_reason TEXT;