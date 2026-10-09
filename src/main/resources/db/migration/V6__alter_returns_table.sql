-- returns jadvalini yaratish
CREATE TABLE returns
(
    id          BIGSERIAL PRIMARY KEY,
    customer_id BIGINT         NOT NULL REFERENCES customers (id),
    order_id    BIGINT REFERENCES orders (id),
    status      VARCHAR(50)    NOT NULL DEFAULT 'NEW',
    created_at  TIMESTAMPTZ             DEFAULT CURRENT_TIMESTAMP,
    product_id  BIGINT REFERENCES products (id),
    quantity    INTEGER        NOT NULL DEFAULT 0,
    amount      NUMERIC(12, 2) NOT NULL DEFAULT 0,
    agent_id    BIGINT REFERENCES agents (id),
    resolved_at TIMESTAMPTZ
);

CREATE INDEX idx_returns_customer ON returns (customer_id);
CREATE INDEX idx_returns_product ON returns (product_id);
CREATE INDEX idx_returns_status ON returns (status);