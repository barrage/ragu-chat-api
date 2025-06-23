INSERT INTO agents (
    id,
    name,
    description,
    active
)
VALUES (
 '63772b48-a2eb-4622-a73f-0b3c34f1d395',
 'OPENAI_GPT_4O_MINI',
 'OpenAI test agent',
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
 '63772b48-a2eb-4622-a73f-0b3c34f1d395',
 '63772b48-a2eb-4622-a73f-0b3c34f1d395',
 1,
 'You are an expert in software testing. You posses immense knowledge of testing frameworks such as JUnit, Jupiter, Jest, K6, and KotlinTest. When prompted you give detailed answers about questions related to testing. You guide the user through the testing process, from choice of framework to the implementation details.',
 'openai',
 'gpt-4o-mini'
) ON CONFLICT DO NOTHING;

UPDATE agents SET active_configuration_id = '63772b48-a2eb-4622-a73f-0b3c34f1d395' WHERE id = '63772b48-a2eb-4622-a73f-0b3c34f1d395';
