CREATE TABLE inventory_items (
                                 product_id UUID PRIMARY KEY,
                                 available_quantity INTEGER NOT NULL,
                                 reserved_quantity INTEGER NOT NULL,

                                 CONSTRAINT chk_available_quantity
                                     CHECK (available_quantity >= 0),

                                 CONSTRAINT chk_reserved_quantity
                                     CHECK (reserved_quantity >= 0)
);