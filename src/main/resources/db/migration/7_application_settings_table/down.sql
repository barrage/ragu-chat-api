DROP INDEX IF EXISTS idx_application_settings_description;

DROP TRIGGER IF EXISTS set_updated_at ON application_settings;

DROP TABLE application_settings;