ALTER TABLE inventory_reservations
    ADD COLUMN released_at TIMESTAMP WITH TIME ZONE;

CREATE TABLE inventory_reservation_items (
    order_id UUID NOT NULL REFERENCES inventory_reservations(order_id),
    product_id UUID NOT NULL REFERENCES inventory_items(product_id),
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    PRIMARY KEY (order_id, product_id)
);
