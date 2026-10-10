CREATE TABLE products (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    subtitle VARCHAR(255) NOT NULL,
    category VARCHAR(100) NOT NULL,
    description TEXT NOT NULL,
    features TEXT NOT NULL,
    illustration VARCHAR(30) NOT NULL,
    color VARCHAR(30) NOT NULL,
    unit_price NUMERIC(19,2) NOT NULL CHECK (unit_price >= 0),
    currency CHAR(3) NOT NULL DEFAULT 'GBP',
    active BOOLEAN NOT NULL DEFAULT true
);
-- Deterministic demonstration catalogue. Checkout always resolves names/prices from this table.
INSERT INTO products(id,name,subtitle,category,description,features,illustration,color,unit_price,currency,active) VALUES
('22222222-2222-2222-2222-222222222222', 'Mechanical Keyboard', 'The tactile essential', 'Workspace', 'Compact proportions, satisfying tactile switches and a considered everyday layout. Built for the work you love.', 'Hot-swappable switches|USB-C connection|Compact 75% layout', 'keyboard', 'sage', 79.99, 'GBP', true),
('33333333-3333-3333-3333-333333333333', 'Studio Headphones', 'A little room to focus', 'Audio', 'Comfortable over-ear listening with a warm, balanced sound. Your own quiet corner, wherever you work.', '40 mm drivers|Soft replaceable cushions|Wired connection', 'headphones', 'sand', 129.00, 'GBP', true),
('44444444-4444-4444-4444-444444444444', 'Wireless Mouse', 'Quietly in control', 'Workspace', 'An effortless shape with quiet clicks and a precise optical sensor. Less friction, more flow.', 'Bluetooth connection|Quiet switches|Rechargeable battery', 'mouse', 'clay', 39.00, 'GBP', true),
('55555555-5555-5555-5555-555555555555', 'Desk Light', 'Put things in a new light', 'Lighting', 'A soft pool of adjustable light, a slender silhouette and a weighted base. Make space for your best ideas.', 'Three brightness levels|Warm LED light|Adjustable head', 'lamp', 'sage', 69.00, 'GBP', true),
('66666666-6666-6666-6666-666666666666', 'Monitor Stand', 'A fresh perspective', 'Workspace', 'Lift your screen and free your desk. A simple raised platform with room underneath for your daily essentials.', 'Solid timber platform|Non-slip feet|Cable-friendly design', 'stand', 'sand', 49.00, 'GBP', true),
('77777777-7777-7777-7777-777777777777', 'USB-C Hub', 'Everything, connected', 'Accessories', 'One compact connection for a less complicated desk. A durable aluminium hub that keeps your essentials close.', 'USB-C input|HDMI and USB-A ports|Compact aluminium shell', 'hub', 'clay', 45.00, 'GBP', true);
