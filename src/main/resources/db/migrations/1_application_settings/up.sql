-- Key value storage.
CREATE TABLE application_settings (
     name TEXT PRIMARY KEY,
     value TEXT NOT NULL,
     created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
     updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
 );

SELECT manage_updated_at('application_settings');

CREATE INDEX ON application_settings (name);
