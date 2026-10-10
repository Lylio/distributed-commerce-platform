CREATE TABLE order_payment_results (
    order_id UUID PRIMARY KEY REFERENCES orders(id),
    event_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('SUCCEEDED', 'FAILED')),
    received_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
ALTER TABLE orders ADD COLUMN cancellation_reason TEXT;
