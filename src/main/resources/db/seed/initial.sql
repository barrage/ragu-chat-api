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
 'cro',
 'weaviate',
 'azure',
 'text-embedding-ada-002'
) ON CONFLICT DO NOTHING;

INSERT INTO agent_instructions(agent_id)
VALUES ('00000000-0000-0000-0000-000000000000')
ON CONFLICT DO NOTHING;

INSERT INTO users (email, full_name, first_name, last_name, role, active)
VALUES
('josip.benkodakovic@barrage.net', 'Biblius Khan', 'Biblius', 'Khan', 'ADMIN', TRUE),
('antonio.vidakovic@barrage.net', 'Antoan', 'Antonio', 'V', 'ADMIN', TRUE),
('filip.brkic@barrage.net', 'Brx', 'Filip', 'B', 'ADMIN', TRUE),
('dino@barrage.net', 'Bayram, Blazing Quasar of the Chadoverse', 'Dino', 'B', 'ADMIN', TRUE)
ON CONFLICT DO NOTHING;

SELECT id, email, full_name FROM users;