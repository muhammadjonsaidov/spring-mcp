-- Hududlar (ierarxik tuzilma)
INSERT INTO territories (name, parent_id)
VALUES ('O''zbekiston', NULL),
       ('Toshkent viloyati', 1),
       ('Samarqand viloyati', 1),
       ('Chilonzor tumani', 2),
       ('Yunusobod tumani', 2),
       ('Samarqand shahri', 3),
       ('Toshkent shahri', 1);

-- Chilonzor va Yunusobod Toshkent shahriga tegishli
UPDATE territories
SET parent_id = (SELECT id FROM territories WHERE name = 'Toshkent shahri')
WHERE name IN ('Chilonzor tumani', 'Yunusobod tumani');

-- Agentlar
INSERT INTO agents (full_name, phone, email, role, territory_id, is_active)
VALUES ('Ali Valiyev', '+998901111111', 'ali@salesdoctor.uz', 'AGENT', 4, TRUE),
       ('Bobur Karimov', '+998902222222', 'bobur@salesdoctor.uz', 'AGENT', 5, TRUE),
       ('Dilshod Rahimov', '+998903333333', 'dilshod@salesdoctor.uz', 'AGENT', 6, TRUE),
       ('Sarvar Toshev', '+998904444444', 'sarvar@salesdoctor.uz', 'SUPERVISOR', 2, TRUE),
       ('Nodira Yusupova', '+998905555555', 'nodira@salesdoctor.uz', 'EXPEDITOR', 1, TRUE);