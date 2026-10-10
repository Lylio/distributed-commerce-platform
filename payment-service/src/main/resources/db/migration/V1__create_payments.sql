CREATE TABLE payments (
    order_id UUID PRIMARY KEY,
    status VARCHAR(20) NOT NULL CHECK (status IN ('SUCCEEDED', 'FAILED')),
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
