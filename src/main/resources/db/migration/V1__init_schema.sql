-- ============================================
-- Sales Doctor: Boshlang'ich sxema
-- ============================================

-- 1. Hududlar (territoriyalar)
CREATE TABLE territories
(
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(255) NOT NULL,
    parent_id  BIGINT REFERENCES territories (id),
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

-- 2. Agentlar (savdo agentlari)
CREATE TABLE agents
(
    id           BIGSERIAL PRIMARY KEY,
    full_name    VARCHAR(255) NOT NULL,
    phone        VARCHAR(50),
    email        VARCHAR(255) UNIQUE,
    role         VARCHAR(50)  NOT NULL DEFAULT 'AGENT',
    territory_id BIGINT REFERENCES territories (id),
    is_active    BOOLEAN               DEFAULT TRUE,
    created_at   TIMESTAMPTZ           DEFAULT CURRENT_TIMESTAMP
);

-- 3. Mijozlar (savdo nuqtalari)
CREATE TABLE customers
(
    id           BIGSERIAL PRIMARY KEY,
    name         VARCHAR(255) NOT NULL,
    address      TEXT,
    phone        VARCHAR(50),
    latitude     NUMERIC(10, 7),
    longitude    NUMERIC(10, 7),
    territory_id BIGINT REFERENCES territories (id),
    debt_amount  NUMERIC(12, 2) DEFAULT 0,
    is_active    BOOLEAN        DEFAULT TRUE,
    created_at   TIMESTAMPTZ    DEFAULT CURRENT_TIMESTAMP
);

-- 4. Mahsulot kategoriyalari
CREATE TABLE categories
(
    id        BIGSERIAL PRIMARY KEY,
    name      VARCHAR(255) NOT NULL,
    parent_id BIGINT REFERENCES categories (id)
);

-- 5. Mahsulotlar
CREATE TABLE products
(
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(255)        NOT NULL,
    sku         VARCHAR(100) UNIQUE NOT NULL,
    price       NUMERIC(12, 2)      NOT NULL,
    category_id BIGINT REFERENCES categories (id),
    image_url   VARCHAR(500),
    is_active   BOOLEAN     DEFAULT TRUE,
    created_at  TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

-- 6. Ombor qoldiqlari
CREATE TABLE stock
(
    id                 BIGSERIAL PRIMARY KEY,
    product_id         BIGINT  NOT NULL REFERENCES products (id),
    agent_id           BIGINT REFERENCES agents (id),
    warehouse_location VARCHAR(100),
    quantity           INTEGER NOT NULL DEFAULT 0,
    reserved_quantity  INTEGER          DEFAULT 0,
    updated_at         TIMESTAMPTZ      DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (product_id, agent_id, warehouse_location)
);

-- 7. Buyurtmalar
CREATE TABLE orders
(
    id            BIGSERIAL PRIMARY KEY,
    order_number  VARCHAR(50) UNIQUE NOT NULL,
    customer_id   BIGINT             NOT NULL REFERENCES customers (id),
    agent_id      BIGINT             NOT NULL REFERENCES agents (id),
    status        VARCHAR(50)        NOT NULL DEFAULT 'NEW',
    total_amount  NUMERIC(12, 2)     NOT NULL DEFAULT 0,
    delivery_date DATE,
    created_at    TIMESTAMPTZ                 DEFAULT CURRENT_TIMESTAMP
);

-- 8. Buyurtma qatorlari
CREATE TABLE order_items
(
    id               BIGSERIAL PRIMARY KEY,
    order_id         BIGINT         NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    product_id       BIGINT         NOT NULL REFERENCES products (id),
    quantity         INTEGER        NOT NULL,
    unit_price       NUMERIC(12, 2) NOT NULL,
    discount_percent NUMERIC(5, 2) DEFAULT 0
);

-- 9. To'lovlar
CREATE TABLE payments
(
    id             BIGSERIAL PRIMARY KEY,
    customer_id    BIGINT         NOT NULL REFERENCES customers (id),
    order_id       BIGINT REFERENCES orders (id),
    amount         NUMERIC(12, 2) NOT NULL,
    payment_method VARCHAR(50),
    agent_id       BIGINT REFERENCES agents (id),
    created_at     TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

-- ============================================
-- Indekslar (foreign key'lar uchun majburiy)
-- ============================================
CREATE INDEX idx_agents_territory ON agents (territory_id);
CREATE INDEX idx_customers_territory ON customers (territory_id);
CREATE INDEX idx_products_category ON products (category_id);
CREATE INDEX idx_stock_product ON stock (product_id);
CREATE INDEX idx_stock_agent ON stock (agent_id);
CREATE INDEX idx_orders_customer ON orders (customer_id);
CREATE INDEX idx_orders_agent ON orders (agent_id);
CREATE INDEX idx_orders_status ON orders (status);
CREATE INDEX idx_order_items_order ON order_items (order_id);
CREATE INDEX idx_order_items_product ON order_items (product_id);
CREATE INDEX idx_payments_customer ON payments (customer_id);
CREATE INDEX idx_payments_order ON payments (order_id);