-- =====================================================================
-- SGMart - DEV ONLY sample data (same products and partners as the
-- approved prototype). Loaded only by the `dev` Spring profile:
--   spring.flyway.locations=classpath:db/migration,classpath:db/dev-data
-- Repeatable migration: safe to run again, it never creates duplicates
-- and never overwrites stock or prices you changed in the app.
-- =====================================================================

SET NAMES utf8mb4;

-- A second admin for testing roles (sign in with this Gmail in dev)
INSERT IGNORE INTO admin_users (email, name, role, active)
VALUES ('manager.sgmart@gmail.com', 'Store Manager', 'MANAGER', 1);

-- Delivery partners
INSERT IGNORE INTO delivery_partners (name, mobile, vehicle_type, status) VALUES
    ('Ravi Kumar',  '9848012345', 'BIKE',    'AVAILABLE'),
    ('Salma Begum', '9949023456', 'SCOOTER', 'AVAILABLE'),
    ('Arjun Reddy', '9000034567', 'BICYCLE', 'OFF_DUTY');

-- Products
CREATE TEMPORARY TABLE tmp_seed_products (
    category  VARCHAR(80),
    icon      VARCHAR(16),
    name      VARCHAR(120),
    unit      VARCHAR(40),
    price     DECIMAL(10,2),
    mrp       DECIMAL(10,2),
    stock_qty INT
) DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

INSERT INTO tmp_seed_products VALUES
    ('Fruits & vegetables',  '🍌', 'Banana (Robusta)',     '6 pcs',     48.00,  60.00, 40),
    ('Fruits & vegetables',  '🍅', 'Tomato',               '500 g',     24.00,  30.00, 50),
    ('Fruits & vegetables',  '🧅', 'Onion',                '1 kg',      38.00,  45.00, 60),
    ('Fruits & vegetables',  '🥔', 'Potato',               '1 kg',      34.00,  40.00, 45),
    ('Fruits & vegetables',  '🍎', 'Shimla Apple',         '4 pcs',    120.00, 150.00, 20),
    ('Fruits & vegetables',  '🥕', 'Carrot',               '500 g',     32.00,  36.00, 25),
    ('Dairy, bread & eggs',  '🥛', 'Toned Milk',           '500 ml',    28.00,  28.00, 80),
    ('Dairy, bread & eggs',  '🍞', 'Whole Wheat Bread',    '400 g',     45.00,  50.00, 30),
    ('Dairy, bread & eggs',  '🧈', 'Salted Butter',        '100 g',     58.00,  60.00, 24),
    ('Dairy, bread & eggs',  '🥚', 'Farm Fresh Eggs',      '6 pcs',     54.00,  60.00, 36),
    ('Dairy, bread & eggs',  '🧀', 'Fresh Paneer',         '200 g',     90.00, 100.00, 18),
    ('Snacks',               '🍿', 'Masala Popcorn',       '60 g',      35.00,  40.00, 40),
    ('Snacks',               '🍪', 'Butter Cookies',       '200 g',     40.00,  50.00, 35),
    ('Snacks',               '🥜', 'Roasted Peanuts',      '200 g',     60.00,  70.00, 22),
    ('Tea, coffee & drinks', '🥤', 'Cola',                 '750 ml',    40.00,  40.00, 48),
    ('Tea, coffee & drinks', '☕', 'Filter Coffee Powder', '200 g',    150.00, 175.00, 15),
    ('Tea, coffee & drinks', '🍵', 'Green Tea',            '25 bags',  140.00, 165.00,  3),
    ('Atta, rice & dal',     '🌾', 'Basmati Rice',         '1 kg',     110.00, 130.00, 30),
    ('Atta, rice & dal',     '🫘', 'Toor Dal',             '1 kg',     145.00, 160.00, 26),
    ('Atta, rice & dal',     '🛢️', 'Sunflower Oil',        '1 L',      155.00, 175.00, 20),
    ('Personal care',        '🧼', 'Bath Soap',            '4 × 100 g', 120.00, 140.00, 30),
    ('Personal care',        '🪥', 'Toothpaste',           '150 g',     95.00, 105.00, 28);

INSERT INTO products (category_id, icon, name, unit, price, mrp, stock_qty)
SELECT c.id, t.icon, t.name, t.unit, t.price, t.mrp, t.stock_qty
FROM tmp_seed_products t
JOIN categories c ON c.name = t.category
WHERE NOT EXISTS (
    SELECT 1 FROM products p WHERE p.name = t.name AND p.unit = t.unit
);

DROP TEMPORARY TABLE tmp_seed_products;
