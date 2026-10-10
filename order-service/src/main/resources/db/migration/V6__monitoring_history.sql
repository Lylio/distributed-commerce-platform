-- Telemetry is written independently, never inside a business transaction.
CREATE TABLE monitoring_events (
    sequence BIGSERIAL PRIMARY KEY,
    evidence_key VARCHAR(250) NOT NULL UNIQUE,
    order_id UUID NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    source VARCHAR(50) NOT NULL,
    destination VARCHAR(50) NOT NULL,
    stage VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    observed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    event_id VARCHAR(100),
    topic VARCHAR(100),
    kafka_partition INTEGER,
    kafka_offset BIGINT
);
CREATE INDEX idx_monitoring_order ON monitoring_events(order_id, sequence);
