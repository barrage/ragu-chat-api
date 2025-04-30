CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Function that sets the updated_at column to the current timestamp when a row in a table is updated.
CREATE OR REPLACE FUNCTION set_updated_at() RETURNS TRIGGER AS $$
BEGIN
    IF (
        NEW IS DISTINCT FROM OLD AND
        NEW.updated_at IS NOT DISTINCT FROM OLD.updated_at
    ) THEN
        NEW.updated_at := current_timestamp;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Usage: `manage_updated_at('table_name')`
CREATE OR REPLACE FUNCTION manage_updated_at(_tbl regclass) RETURNS VOID AS $$
BEGIN
    EXECUTE format('CREATE TRIGGER set_updated_at BEFORE UPDATE ON %s FOR EACH ROW EXECUTE PROCEDURE set_updated_at()', _tbl);
END;
$$ LANGUAGE plpgsql;

-- Function that forwards the deletion of a workflow to its message groups.
CREATE OR REPLACE FUNCTION delete_message_groups_for_workflow()
RETURNS TRIGGER AS $$
BEGIN
  DELETE FROM message_groups
  WHERE parent_id = OLD.id;
  RETURN OLD;
END;
$$ LANGUAGE plpgsql;

-- Usage: `manage_message_group_parent('table_name')`
CREATE OR REPLACE FUNCTION manage_message_group_parent(_tbl regclass) RETURNS VOID AS $$
BEGIN
    EXECUTE format('CREATE TRIGGER delete_message_groups_for_workflow AFTER DELETE ON %s FOR EACH ROW EXECUTE PROCEDURE delete_message_groups_for_workflow()', _tbl);
END;
$$ LANGUAGE plpgsql;
