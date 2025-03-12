DROP INDEX IF EXISTS idx_agent_permissions_agent_id;
DROP INDEX IF EXISTS idx_agent_tools_agent_id;
DROP INDEX IF EXISTS idx_agent_configurations_agent_id;
DROP INDEX IF EXISTS idx_agent_collections_agent_id;
DROP INDEX IF EXISTS idx_agents_active;

DROP TRIGGER IF EXISTS set_updated_at ON agent_configurations;
DROP TRIGGER IF EXISTS set_updated_at ON agent_collections;
DROP TRIGGER IF EXISTS set_updated_at ON agents;

DROP TABLE agent_permissions;
DROP TABLE agent_tools;
DROP TABLE agent_configurations;
DROP TABLE agent_collections;
DROP TABLE agents;
