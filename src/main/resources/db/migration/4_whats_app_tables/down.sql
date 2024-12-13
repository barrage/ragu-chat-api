DROP INDEX IF EXISTS idx_whatsapp_numbers_user_id;
DROP INDEX IF EXISTS idx_whatsapp_numbers_phone_number;
DROP INDEX IF EXISTS idx_whatsapp_agents_active;

DROP TRIGGER IF EXISTS set_updated_at ON whats_app_messages;
DROP TRIGGER IF EXISTS set_updated_at ON whats_app_chats;
DROP TRIGGER IF EXISTS set_updated_at ON whats_app_agent_collections;
DROP TRIGGER IF EXISTS set_updated_at ON whats_app_agents;
DROP TRIGGER IF EXISTS set_updated_at ON whats_app_numbers;

DROP TABLE whats_app_messages;

DROP TABLE whats_app_chats;

DROP TABLE whats_app_agent_collections;

DROP TABLE whats_app_agents;

DROP TABLE whats_app_numbers;
