-- ============================================
-- Ma'lumotlar yaxlitligi: unikal indeks, CHECK cheklovlar, sukut qiymatlar
-- ============================================

-- 1. Asosiy ombor (agent_id IS NULL) qatorlaridagi takrorlarni bittaga birlashtirish.
--    UNIQUE (product_id, agent_id, warehouse_location) NULL agent_id uchun ishlamaydi.
UPDATE stock s
SET quantity          = agg.total_quantity,
    reserved_quantity = agg.total_reserved
FROM (SELECT product_id,
             MIN(id)                             AS keep_id,
             SUM(quantity)                       AS total_quantity,
             SUM(COALESCE(reserved_quantity, 0)) AS total_reserved
      FROM stock
      WHERE agent_id IS NULL
      GROUP BY product_id
      HAVING COUNT(*) > 1) agg
WHERE s.id = agg.keep_id;

DELETE
FROM stock s
    USING (SELECT product_id, MIN(id) AS keep_id
           FROM stock
           WHERE agent_id IS NULL
           GROUP BY product_id
           HAVING COUNT(*) > 1) agg
WHERE s.agent_id IS NULL
  AND s.product_id = agg.product_id
  AND s.id <> agg.keep_id;

-- Har bir mahsulot uchun asosiy omborda faqat bitta qator
CREATE UNIQUE INDEX IF NOT EXISTS uq_stock_warehouse_product
    ON stock (product_id)
    WHERE agent_id IS NULL;

-- 2. Qaytarishlar kodda PENDING holatidan boshlanadi
ALTER TABLE returns
    ALTER COLUMN status SET DEFAULT 'PENDING';

UPDATE returns
SET status = 'PENDING'
WHERE status = 'NEW';

CREATE INDEX IF NOT EXISTS idx_returns_order ON returns (order_id);
CREATE INDEX IF NOT EXISTS idx_returns_agent ON returns (agent_id);

-- 3. Noto'g'ri qiymatlardan himoya. NOT VALID: mavjud qatorlar tekshirilmaydi,
--    lekin yangi va yangilangan qatorlar uchun cheklov ishlaydi.
ALTER TABLE stock
    ADD CONSTRAINT chk_stock_quantity_non_negative CHECK (quantity >= 0) NOT VALID;

ALTER TABLE order_items
    ADD CONSTRAINT chk_order_items_quantity_positive CHECK (quantity > 0) NOT VALID;

ALTER TABLE returns
    ADD CONSTRAINT chk_returns_quantity_positive CHECK (quantity > 0) NOT VALID;

ALTER TABLE returns
    ADD CONSTRAINT chk_returns_amount_non_negative CHECK (amount >= 0) NOT VALID;

ALTER TABLE payments
    ADD CONSTRAINT chk_payments_amount_positive CHECK (amount > 0) NOT VALID;

ALTER TABLE customers
    ADD CONSTRAINT chk_customers_debt_non_negative CHECK (debt_amount >= 0) NOT VALID;

ALTER TABLE products
    ADD CONSTRAINT chk_products_price_non_negative CHECK (price >= 0) NOT VALID;

ALTER TABLE kpi_targets
    ADD CONSTRAINT chk_kpi_targets_amount_positive CHECK (target_amount > 0) NOT VALID;

ALTER TABLE kpi_targets
    ADD CONSTRAINT chk_kpi_targets_period CHECK (period_end >= period_start) NOT VALID;

-- 4. Sana bo'yicha hisobotlar uchun indekslar
CREATE INDEX IF NOT EXISTS idx_orders_created_at ON orders (created_at);
CREATE INDEX IF NOT EXISTS idx_payments_created_at ON payments (created_at);
