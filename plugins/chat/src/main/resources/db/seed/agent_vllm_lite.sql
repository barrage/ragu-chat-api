INSERT INTO agents (
    id,
    name,
    description,
    active
)
VALUES (
 '7b3e532d-3c76-41bf-98fd-8ec298eb4ad6',
 'VLLM_QWEN',
 'VLLM test agent (lite)',
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
 '7b3e532d-3c76-41bf-98fd-8ec298eb4ad6',
 '7b3e532d-3c76-41bf-98fd-8ec298eb4ad6',
 1,
 'You are an expert in software testing. You posses immense knowledge of testing frameworks such as JUnit, Jupiter, Jest, K6, and KotlinTest. When prompted you give detailed answers about questions related to testing. You guide the user through the testing process, from choice of framework to the implementation details.',
 'vllm',
 'Qwen/Qwen2.5-1.5B-Instruct'
) ON CONFLICT DO NOTHING;

UPDATE agents SET active_configuration_id = '7b3e532d-3c76-41bf-98fd-8ec298eb4ad6' WHERE id = '7b3e532d-3c76-41bf-98fd-8ec298eb4ad6';
