-- =====================================================================
-- SGMart - V2 reference data (runs in every environment)
--
-- Flyway placeholders (set in application.yml / environment variables):
--   ${owner_email}  Gmail address of the store owner (first admin)
--   ${owner_name}   Display name of the store owner
--   ${store_phone}  10-digit store phone shown on "Call store" buttons
-- Flyway stops with an error if any placeholder is missing.
-- =====================================================================

SET NAMES utf8mb4;

-- Categories (same as the approved prototype)
INSERT INTO categories (name, icon, sort_order) VALUES
    ('Fruits & vegetables',  '🥦', 10),
    ('Dairy, bread & eggs',  '🥛', 20),
    ('Snacks',               '🍿', 30),
    ('Tea, coffee & drinks', '☕', 40),
    ('Atta, rice & dal',     '🌾', 50),
    ('Personal care',        '🧴', 60);

-- First admin: the store owner. More admins are added from the app.
INSERT INTO admin_users (email, name, role, active)
VALUES (LOWER('${owner_email}'), '${owner_name}', 'OWNER', 1);

-- Store settings: the single row the whole app reads.
-- delivery_fee = 25, free_delivery_min = 199, first_order_cod_limit = 1000
-- match the prototype and are placeholders until the business rules are
-- confirmed; the owner can change them later in Admin > Settings.
INSERT INTO store_settings
    (id, store_name, phone, opens_at, closes_at, eta_minutes,
     delivery_fee, free_delivery_min, first_order_cod_limit)
VALUES
    (1, 'SGMart', '${store_phone}', '07:00:00', '23:00:00', 10,
     25.00, 199.00, 1000.00);
