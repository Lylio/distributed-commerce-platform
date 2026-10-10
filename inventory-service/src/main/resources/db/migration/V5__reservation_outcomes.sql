ALTER TABLE inventory_reservations ADD COLUMN outcome VARCHAR(20) NOT NULL DEFAULT 'RESERVED'
    CHECK (outcome IN ('RESERVED', 'REJECTED'));
ALTER TABLE inventory_reservations ADD COLUMN rejection_reason TEXT;
