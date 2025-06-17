INSERT INTO agents (
    id,
    name,
    description,
    active
)
VALUES (
 '16b972a4-4704-46ef-b5fe-49c6acbc0424',
 'VLLM_MISTRAL',
 'VLLM test agent (heavy)',
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
 '16b972a4-4704-46ef-b5fe-49c6acbc0424',
 '16b972a4-4704-46ef-b5fe-49c6acbc0424',
 1,
 'You are an expert in software testing. You posses immense knowledge of testing frameworks such as JUnit, Jupiter, Jest, K6, and KotlinTest. When prompted you give detailed answers about questions related to testing. You guide the user through the testing process, from choice of framework to the implementation details.',
 'vllm',
 'mistralai/Mistral-Small-3.1-24B-Instruct-2503'
) ON CONFLICT DO NOTHING;

UPDATE agents SET active_configuration_id = '16b972a4-4704-46ef-b5fe-49c6acbc0424' WHERE id = '16b972a4-4704-46ef-b5fe-49c6acbc0424';
