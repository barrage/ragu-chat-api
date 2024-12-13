DROP INDEX IF EXISTS idx_users_deleted_at;
DROP INDEX IF EXISTS idx_agents_active;
DROP INDEX IF EXISTS idx_agent_collections_agent_id;
DROP INDEX IF EXISTS idx_sessions_user_id_expires_at;
DROP INDEX IF EXISTS idx_chats_user_id;
DROP INDEX IF EXISTS idx_chats_agent_id;
DROP INDEX IF EXISTS idx_messages_chat_id;
DROP INDEX IF EXISTS idx_messages_sender;
DROP INDEX IF EXISTS idx_messages_evaluation;

DROP TRIGGER IF EXISTS set_updated_at ON failed_messages;
DROP TRIGGER IF EXISTS set_updated_at ON messages;
DROP TRIGGER IF EXISTS set_updated_at ON chats;
DROP TRIGGER IF EXISTS set_updated_at ON agent_configurations;
DROP TRIGGER IF EXISTS set_updated_at ON agent_collections;
DROP TRIGGER IF EXISTS set_updated_at ON agents;
DROP TRIGGER IF EXISTS set_updated_at ON sessions;
DROP TRIGGER IF EXISTS set_updated_at ON users;

DROP TABLE failed_messages;

DROP TABLE messages;

DROP TABLE chats;

DROP TABLE agent_configurations;

DROP TABLE agent_collections;

DROP TABLE agents;

DROP TABLE sessions;

DROP TABLE users;
