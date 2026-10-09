-- Asosiy kategoriyalar
INSERT INTO categories (name, parent_id)
VALUES ('Ichimliklar', NULL),
       ('Oziq-ovqat', NULL),
       ('Maishiy kimyo', NULL),
       ('Shirinliklar', NULL);

-- Ichki kategoriyalar (Ichimliklar ostida)
INSERT INTO categories (name, parent_id)
VALUES ('Gazli ichimliklar', 1),
       ('Sharbatlar', 1),
       ('Suv', 1);

-- Ichki kategoriyalar (Oziq-ovqat ostida)
INSERT INTO categories (name, parent_id)
VALUES ('Non mahsulotlari', 2),
       ('Konserva', 2),
       ('Yog''lar', 2);

-- Ichki kategoriyalar (Shirinliklar ostida)
INSERT INTO categories (name, parent_id)
VALUES ('Shokolad', 4),
       ('Pechene', 4),
       ('Konfet', 4);