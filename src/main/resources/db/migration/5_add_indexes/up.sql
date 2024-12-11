CREATE INDEX idx_users_deleted_at ON users (deleted_at);

CREATE INDEX idx_whatsapp_numbers_user_id ON whats_app_numbers (user_id);

CREATE INDEX idx_whatsapp_numbers_phone_number ON whats_app_numbers (phone_number);

CREATE INDEX idx_whatsapp_agents_active ON whats_app_agents (active);

CREATE INDEX idx_agents_active ON agents (active);

CREATE INDEX idx_agent_collections_agent_id ON agent_collections (agent_id);

CREATE INDEX idx_sessions_user_id_expires_at ON sessions (user_id, expires_at);

CREATE INDEX idx_chats_user_id ON chats (user_id);

CREATE INDEX idx_chats_agent_id ON chats (agent_id);

CREATE INDEX idx_messages_chat_id ON messages (chat_id);

CREATE INDEX idx_messages_sender ON messages (sender);

CREATE INDEX idx_messages_evaluation ON messages (evaluation);