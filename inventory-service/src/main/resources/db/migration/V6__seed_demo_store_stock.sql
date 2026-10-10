-- Initial demo stock; existing reservations and restock quantities are preserved.
INSERT INTO inventory_items (product_id, available_quantity, reserved_quantity) VALUES
('22222222-2222-2222-2222-222222222222', 100, 0),
('33333333-3333-3333-3333-333333333333', 0, 0),
('44444444-4444-4444-4444-444444444444', 48, 0),
('55555555-5555-5555-5555-555555555555', 24, 0),
('66666666-6666-6666-6666-666666666666', 32, 0),
('77777777-7777-7777-7777-777777777777', 64, 0)
ON CONFLICT (product_id) DO NOTHING;
