DROP INDEX IF EXISTS idx_message_group_evaluations_evaluation;
DROP INDEX IF EXISTS idx_message_group_evaluations_message_group_id;

DROP INDEX IF EXISTS idx_messages_evaluation;
DROP INDEX IF EXISTS idx_messages_group_id;

DROP INDEX IF EXISTS idx_message_groups_agent_config_id;
DROP INDEX IF EXISTS idx_message_groups_chat_id;

DROP INDEX IF EXISTS idx_chats_type;
DROP INDEX IF EXISTS idx_chats_agent_id;
DROP INDEX IF EXISTS idx_chats_user_id;

DROP TRIGGER IF EXISTS set_updated_at ON message_group_evaluations;
DROP TRIGGER IF EXISTS set_updated_at ON messages;
DROP TRIGGER IF EXISTS set_updated_at ON message_groups;
DROP TRIGGER IF EXISTS set_updated_at ON chats;

DROP TABLE message_group_evaluations;
DROP TABLE messages;
DROP TABLE message_groups;
DROP TABLE chats;
