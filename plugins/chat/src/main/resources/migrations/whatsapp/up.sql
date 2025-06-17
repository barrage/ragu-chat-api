CREATE TABLE whats_app_numbers (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id TEXT NOT NULL,
    username TEXT NOT NULL,
    phone_number TEXT NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

SELECT manage_updated_at('whats_app_numbers');

CREATE INDEX idx_whatsapp_numbers_user_id ON whats_app_numbers (user_id);
CREATE INDEX idx_whatsapp_numbers_phone_number ON whats_app_numbers (phone_number);