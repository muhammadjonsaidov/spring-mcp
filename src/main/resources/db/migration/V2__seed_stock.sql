-- Barcha mavjud mahsulotlar uchun ombor qoldig'i (100 dona)
INSERT INTO stock (product_id, agent_id, warehouse_location, quantity, reserved_quantity)
SELECT id, NULL, 'MAIN', 100, 0
FROM products
WHERE is_active = TRUE;