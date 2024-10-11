INSERT INTO users (id, email, full_name, first_name, last_name, role, active)
VALUES
('0926f471-61eb-49ff-a78b-d807927088ab', 'josip.benkodakovic@barrage.net', 'Biblius Khan', 'Biblius', 'Khan', 'ADMIN', TRUE),
('e9de996e-5716-4f7f-a49a-3dcec749451e', 'antonio.vidakovic@barrage.net', 'Antoan', 'Antonio', 'V', 'ADMIN', TRUE),
('6e679ef5-28e7-4df1-89a7-b3966e51e531', 'filip.brkic@barrage.net', 'Brx', 'Filip', 'B', 'ADMIN', TRUE),
('2ab61554-d108-4ce2-978a-a1d3f6e40fe6', 'dino@barrage.net', 'Bayram', 'Dino', 'B', 'ADMIN', TRUE),
('8c15a8c8-1dba-434d-b30b-cb352dc7c347', 'hrvoje.kuzmanovic@barrage.net', 'Hrvoje Kuzmanovic', 'Hrvoje', 'K', 'ADMIN', TRUE),
('9029ee9a-f443-489a-8aae-ead857991408', 'stefani.majic@barrage.net', 'Stefani Majic', 'Stefani', 'M', 'ADMIN', TRUE),
('f348812f-a17b-4076-8fc4-958d9157a9f6', 'bruno.spajic@barrage.net', 'Bruno Spajic', 'Bruno', 'S', 'ADMIN', TRUE),
ON CONFLICT DO NOTHING;

SELECT id, email, full_name FROM users;
