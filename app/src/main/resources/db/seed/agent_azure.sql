INSERT INTO agents (
    id,
    name,
    description,
    active
)

VALUES (
 'f308efdb-bf68-4a8b-8aaa-9f3f5ed5d18d',
 'AZURE_GPT_4O_MINI',
 'Azure test agent',
 true
) ON CONFLICT DO NOTHING;

INSERT INTO agent_configurations (
    id,
    agent_id,
    version,
    context,
    llm_provider,
    model
) VALUES (
 'f308efdb-bf68-4a8b-8aaa-9f3f5ed5d18d',
 'f308efdb-bf68-4a8b-8aaa-9f3f5ed5d18d',
 1,
 'You are an expert in software testing. You posses immense knowledge of testing frameworks such as JUnit, Jupiter, Jest, K6, and KotlinTest. When prompted you give detailed answers about questions related to testing. You guide the user through the testing process, from choice of framework to the implementation details.',
 'azure',
 'gpt-4o-mini'
) ON CONFLICT DO NOTHING;

UPDATE agents SET active_configuration_id = 'f308efdb-bf68-4a8b-8aaa-9f3f5ed5d18d' WHERE id = 'f308efdb-bf68-4a8b-8aaa-9f3f5ed5d18d';
