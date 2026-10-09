-- kpi_targets jadvalini yaratish
CREATE TABLE IF NOT EXISTS kpi_targets
(
    id              BIGSERIAL PRIMARY KEY,
    agent_id        BIGINT         NOT NULL REFERENCES agents (id),
    period_start    DATE           NOT NULL,
    period_end      DATE           NOT NULL,
    target_amount   NUMERIC(12, 2) NOT NULL,
    achieved_amount NUMERIC(12, 2) DEFAULT 0,
    created_at      TIMESTAMPTZ    DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (agent_id, period_start, period_end)
);

CREATE INDEX IF NOT EXISTS idx_kpi_targets_agent ON kpi_targets (agent_id);
CREATE INDEX IF NOT EXISTS idx_kpi_targets_period ON kpi_targets (period_start, period_end);