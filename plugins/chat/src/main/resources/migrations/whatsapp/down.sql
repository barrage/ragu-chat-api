DROP INDEX IF EXISTS idx_whatsapp_numbers_phone_number;
DROP INDEX IF EXISTS idx_whatsapp_numbers_user_id;
DROP TRIGGER IF EXISTS set_updated_at ON whats_app_numbers;
DROP TABLE whats_app_numbers;