-- Agentlarga boshlang'ich mahsulotlar berish (van yuklash)
-- Agent 1 (Ali) — Chilonzor
INSERT INTO stock (product_id, agent_id, warehouse_location, quantity, reserved_quantity)
SELECT p.id, 1, 'VAN-1', 50, 0
FROM products p
WHERE p.is_active = TRUE
ON CONFLICT (product_id, agent_id, warehouse_location) DO NOTHING;

-- Agent 2 (Bobur) — Yunusobod
INSERT INTO stock (product_id, agent_id, warehouse_location, quantity, reserved_quantity)
SELECT p.id, 2, 'VAN-2', 30, 0
FROM products p
WHERE p.is_active = TRUE
ON CONFLICT (product_id, agent_id, warehouse_location) DO NOTHING;

-- Agent 3 (Dilshod) — Samarqand
INSERT INTO stock (product_id, agent_id, warehouse_location, quantity, reserved_quantity)
SELECT p.id, 3, 'VAN-3', 40, 0
FROM products p
WHERE p.is_active = TRUE
ON CONFLICT (product_id, agent_id, warehouse_location) DO NOTHING;