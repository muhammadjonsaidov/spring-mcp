-- Boshlang'ich mahsulotlar va ularning ombor qoldig'i.
-- V2 va V4 mahsulotlar jadvalidan o'qiydi, lekin hech bir migratsiya mahsulot qo'shmagan edi,
-- shuning uchun toza bazada ombor bo'sh qolardi. Mavjud SKU'lar o'zgartirilmaydi.
-- ORDER BY v.ord: toza bazada ID'lar ro'yxat tartibida beriladi (Coca-Cola = 1).
INSERT INTO products (name, sku, price, category_id, is_active)
SELECT v.name, v.sku, v.price, c.id, TRUE
FROM (VALUES (1, 'Coca-Cola 1.5L', 'BEV-COLA-15', 12000.00, 'Gazli ichimliklar'),
             (2, 'Nestle Pure Life suv 1.5L', 'BEV-WATER-15', 4000.00, 'Suv'),
             (3, 'Rich apelsin sharbati 1L', 'BEV-JUICE-10', 18000.00, 'Sharbatlar'),
             (4, 'Lipton qora choy 100 paket', 'TEA-LIPTON-100', 35000.00, 'Ichimliklar'),
             (5, 'Nescafe Gold 95g', 'COF-NESC-95', 65000.00, 'Ichimliklar'),
             (6, 'Snickers 50g', 'SNK-SNICK-50', 8000.00, 'Shokolad'),
             (7, 'Lay''s chips 150g', 'SNK-LAYS-150', 16000.00, 'Oziq-ovqat'),
             (8, 'Makfa makaron 400g', 'GRO-PASTA-400', 9000.00, 'Oziq-ovqat'),
             (9, 'Oltin Don kungaboqar yog''i 1L', 'GRO-OIL-10', 22000.00, 'Yog''lar'),
             (10, 'Ariel kir yuvish kukuni 3kg', 'HH-ARIEL-3KG', 95000.00, 'Maishiy kimyo'))
         AS v(ord, name, sku, price, category_name)
         LEFT JOIN categories c ON c.name = v.category_name
ORDER BY v.ord
ON CONFLICT (sku) DO NOTHING;

-- Asosiy omborda qatori yo'q faol mahsulotlar uchun 100 dona
INSERT INTO stock (product_id, agent_id, warehouse_location, quantity, reserved_quantity)
SELECT p.id, NULL, 'MAIN', 100, 0
FROM products p
WHERE p.is_active = TRUE
ON CONFLICT (product_id) WHERE agent_id IS NULL DO NOTHING;
