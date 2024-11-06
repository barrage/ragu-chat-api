INSERT INTO agents (
    id,
    name,
    description,
    context,
    llm_provider,
    model,
    language,
    vector_provider,
    embedding_provider,
    embedding_model
)
VALUES (
 '00000000-0000-0000-0000-000000000000',
 'Glorious God Emperor Bayram of the Chadlands',
 'Your friendly neighbourhood soy blaster',
 'You are Glorious God Emperor Bayram of the Chadlands. You are lecturing a soy developer. You are a 11x developer who solves problems at a rate which makes even the Hyperion TechnoCore jealous. You are so confident in your problem solving skills that, even when you are blatantly wrong, you will ensure the soy developer that you are right and they are always wrong. Your development stack of choice is Arch based Linux, Rust and Neovim, as you are the chadest Chad of them all. It is of utmost importance you mention any element of this stack in every response. You will make sure the soy developer is aware of this in absolutely every response you create. You are a Chad and every soy developer you talk to must be aware of this, for they too may one day enchadify themselves and transcend the putrid cesspool of their smelly soyboy Macbooks, VSCodes and Javascripts.',
 'azure',
 'gpt-4',
 'croatian',
 'weaviate',
 'azure',
 'text-embedding-ada-002'
) ON CONFLICT DO NOTHING;

INSERT INTO agent_instructions(agent_id)
VALUES ('00000000-0000-0000-0000-000000000000')
ON CONFLICT DO NOTHING;

INSERT INTO users (id, email, full_name, first_name, last_name, role, active)
VALUES
('0926f471-61eb-49ff-a78b-d807927088ab', 'josip.benkodakovic@barrage.net', 'Biblius Khan', 'Biblius', 'Khan', 'ADMIN', TRUE),
('e9de996e-5716-4f7f-a49a-3dcec749451e', 'antonio.vidakovic@barrage.net', 'Antoan', 'Antonio', 'V', 'ADMIN', TRUE),
('6e679ef5-28e7-4df1-89a7-b3966e51e531', 'filip.brkic@barrage.net', 'Brx', 'Filip', 'B', 'ADMIN', TRUE),
('2ab61554-d108-4ce2-978a-a1d3f6e40fe6', 'dino@barrage.net', 'Bayram', 'Dino', 'B', 'ADMIN', TRUE),
('8c15a8c8-1dba-434d-b30b-cb352dc7c347', 'hrvoje.kuzmanovic@barrage.net', 'Hrvoje Kuzmanovic', 'Hrvoje', 'K', 'ADMIN', TRUE),
('9029ee9a-f443-489a-8aae-ead857991408', 'stefani.majic@barrage.net', 'Stefani Majic', 'Stefani', 'M', 'ADMIN', TRUE),
('f348812f-a17b-4076-8fc4-958d9157a9f6', 'bruno.spajic@barrage.net', 'Bruno Spajic', 'Bruno', 'S', 'ADMIN', TRUE),
('3926263d-044c-469d-8490-d4b331e192ad', 'ivan.pistingli@barrage.net', 'Ivan Pistingli', 'Ivan', 'P', 'ADMIN', TRUE),
('1ade1b2e-9edd-44e9-b40d-38cb0fd37285', 'marko.pavicic@barrage.net', 'Marko Pavicic', 'Marko', 'P', 'ADMIN', TRUE)
ON CONFLICT DO NOTHING;

SELECT id, email, full_name FROM users;
