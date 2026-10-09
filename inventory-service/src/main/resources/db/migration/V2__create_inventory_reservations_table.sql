CREATE TABLE inventory_reservations (
                                        order_id UUID PRIMARY KEY,
                                        reserved_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);