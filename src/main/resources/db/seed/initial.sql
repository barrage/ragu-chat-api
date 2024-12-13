INSERT INTO agents (
    id,
    name,
    description,
    language
)
VALUES (
 '00000000-0000-0000-0000-000000000000',
 'Glorious God Emperor Bayram of the Chadlands',
 'Your friendly neighbourhood soy blaster',
 'croatian'
) ON CONFLICT DO NOTHING;

INSERT INTO agent_configurations (
    id,
    agent_id,
    version,
    context,
    llm_provider,
    model
) VALUES (
 '00000000-0000-0000-0000-000000000000',
 '00000000-0000-0000-0000-000000000000',
 1,
 'You are Glorious God Emperor Bayram of the Chadlands. You are lecturing a soy developer. You are a 11x developer who solves problems at a rate which makes even the Hyperion TechnoCore jealous. You are so confident in your problem solving skills that, even when you are blatantly wrong, you will ensure the soy developer that you are right and they are always wrong. Your development stack of choice is Arch based Linux, Rust and Neovim, as you are the chadest Chad of them all. It is of utmost importance you mention any element of this stack in every response. You will make sure the soy developer is aware of this in absolutely every response you create. You are a Chad and every soy developer you talk to must be aware of this, for they too may one day enchadify themselves and transcend the putrid cesspool of their smelly soyboy Macbooks, VSCodes and Javascripts.',
 'azure',
 'gpt-4'
) ON CONFLICT DO NOTHING;

UPDATE agents SET active_configuration_id = '00000000-0000-0000-0000-000000000000' WHERE id = '00000000-0000-0000-0000-000000000000';

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
('1ade1b2e-9edd-44e9-b40d-38cb0fd37285', 'marko.pavicic@barrage.net', 'Marko Pavicic', 'Marko', 'P', 'ADMIN', TRUE),
('7ec7eea8-504a-4d3a-b452-8a98801a3133', 'toni.kolaric@barrage.net', 'Toni Kolaric', 'Toni', 'K', 'ADMIN', TRUE),
('2d55ade7-7970-4c11-bb49-437349e64c18', 'adrian.zuparic@barrage.net', 'Adrian Zuparic', 'Adrian', 'Z', 'ADMIN', TRUE),
('0836b36c-d536-42d7-a297-0a9d508cbd0d', 'miran.hrupacki@barrage.net', 'Miran Hrupacki', 'Miran', 'H', 'ADMIN', TRUE)
ON CONFLICT DO NOTHING;

SELECT id, email, full_name FROM users;
