CREATE TABLE order_status_history (
    sequence BIGSERIAL PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    status VARCHAR(50) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_order_status_history_order ON order_status_history(order_id, sequence);
-- Existing orders have only their current known state; do not invent historical transitions.
INSERT INTO order_status_history(order_id,status,occurred_at) SELECT id,status,updated_at FROM orders;
CREATE FUNCTION record_order_status() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        INSERT INTO order_status_history(order_id,status,occurred_at) VALUES (NEW.id,NEW.status,NEW.created_at);
    ELSIF NEW.status IS DISTINCT FROM OLD.status THEN
        INSERT INTO order_status_history(order_id,status,occurred_at) VALUES (NEW.id,NEW.status,NEW.updated_at);
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER order_status_changed AFTER INSERT OR UPDATE OF status ON orders
    FOR EACH ROW EXECUTE FUNCTION record_order_status();
