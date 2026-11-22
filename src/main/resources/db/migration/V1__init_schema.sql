-- =====================================================================
-- SGMart - V1 initial schema (MySQL 8.0+, InnoDB, utf8mb4)
--
-- Conventions
--   * BIGINT UNSIGNED auto-increment primary keys
--   * Money: DECIMAL(10,2) in rupees
--   * Time: DATETIME(3) stored in UTC (shown in IST by the app)
--   * Status/enum columns: VARCHAR + CHECK (maps cleanly to JPA @Enumerated(STRING))
--   * `version` columns are for JPA optimistic locking (@Version)
--   * Every table has created_at / updated_at (event/log tables: created_at only)
-- =====================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------
-- 1. admin_users : allowlist for admin Google sign-in
-- ---------------------------------------------------------------------
CREATE TABLE admin_users (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    email          VARCHAR(254)    NOT NULL,
    name           VARCHAR(120)    NOT NULL,
    role           VARCHAR(20)     NOT NULL DEFAULT 'MANAGER',
    active         TINYINT(1)      NOT NULL DEFAULT 1,
    last_login_at  DATETIME(3)     NULL,
    created_at     DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at     DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_admin_users_email (email),
    CONSTRAINT ck_admin_users_role CHECK (role IN ('OWNER', 'MANAGER'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------------
-- 2. customers : one row per mobile number (guests) or Google account
--    Guests are created on their first order (mobile, no google_sub).
--    Google users are created on first sign-in (google_sub, mobile later).
-- ---------------------------------------------------------------------
CREATE TABLE customers (
    id                      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    name                    VARCHAR(100)    NOT NULL,
    mobile                  CHAR(10)        NULL,
    email                   VARCHAR(254)    NULL,
    google_sub              VARCHAR(255)    NULL,
    verified_via            VARCHAR(20)     NOT NULL DEFAULT 'NONE',
    verified_by_admin_id    BIGINT UNSIGNED NULL,
    verified_at             DATETIME(3)     NULL,
    delivered_orders_count  INT UNSIGNED    NOT NULL DEFAULT 0,
    blocked                 TINYINT(1)      NOT NULL DEFAULT 0,
    blocked_reason          VARCHAR(255)    NULL,
    blocked_at              DATETIME(3)     NULL,
    created_at              DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at              DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_customers_mobile (mobile),
    UNIQUE KEY uk_customers_google_sub (google_sub),
    KEY idx_customers_email (email),
    CONSTRAINT fk_customers_verified_by FOREIGN KEY (verified_by_admin_id)
        REFERENCES admin_users (id) ON DELETE SET NULL,
    CONSTRAINT ck_customers_identity CHECK (mobile IS NOT NULL OR google_sub IS NOT NULL),
    CONSTRAINT ck_customers_mobile CHECK (mobile IS NULL OR REGEXP_LIKE(mobile, '^[6-9][0-9]{9}$')),
    CONSTRAINT ck_customers_verified_via CHECK (verified_via IN ('NONE', 'STORE_CALL', 'GOOGLE'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------------
-- 3. customer_addresses : saved addresses for signed-in customers
-- ---------------------------------------------------------------------
CREATE TABLE customer_addresses (
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    customer_id   BIGINT UNSIGNED NOT NULL,
    label         VARCHAR(40)     NULL,
    address_line  VARCHAR(500)    NOT NULL,
    landmark      VARCHAR(200)    NULL,
    latitude      DECIMAL(9,6)    NULL,
    longitude     DECIMAL(9,6)    NULL,
    is_default    TINYINT(1)      NOT NULL DEFAULT 0,
    created_at    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_customer_addresses_customer (customer_id),
    CONSTRAINT fk_customer_addresses_customer FOREIGN KEY (customer_id)
        REFERENCES customers (id) ON DELETE CASCADE,
    CONSTRAINT ck_customer_addresses_lat CHECK (latitude IS NULL OR latitude BETWEEN -90 AND 90),
    CONSTRAINT ck_customer_addresses_lng CHECK (longitude IS NULL OR longitude BETWEEN -180 AND 180)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------------
-- 4. categories
-- ---------------------------------------------------------------------
CREATE TABLE categories (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    name        VARCHAR(80)     NOT NULL,
    icon        VARCHAR(16)     NOT NULL,
    sort_order  INT             NOT NULL DEFAULT 0,
    active      TINYINT(1)      NOT NULL DEFAULT 1,
    created_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_categories_name (name),
    KEY idx_categories_active_sort (active, sort_order)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------------
-- 5. products
--    active = 0     -> "Hidden" in admin, not shown in shop
--    deleted_at set -> "Removed" by admin (kept for past orders)
--    stock_qty = 0  -> automatically not shown in shop
-- ---------------------------------------------------------------------
CREATE TABLE products (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    category_id  BIGINT UNSIGNED NOT NULL,
    name         VARCHAR(120)    NOT NULL,
    unit         VARCHAR(40)     NOT NULL,
    price        DECIMAL(10,2)   NOT NULL,
    mrp          DECIMAL(10,2)   NOT NULL,
    stock_qty    INT             NOT NULL DEFAULT 0,
    image_key    VARCHAR(255)    NULL,
    icon         VARCHAR(16)     NOT NULL DEFAULT '🛒',
    active       TINYINT(1)      NOT NULL DEFAULT 1,
    deleted_at   DATETIME(3)     NULL,
    version      INT             NOT NULL DEFAULT 0,
    created_at   DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at   DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_products_category_active (category_id, active),
    KEY idx_products_shop (active, deleted_at, stock_qty),
    KEY idx_products_name (name),
    CONSTRAINT fk_products_category FOREIGN KEY (category_id)
        REFERENCES categories (id),
    CONSTRAINT ck_products_price CHECK (price > 0),
    CONSTRAINT ck_products_mrp CHECK (mrp >= price),
    CONSTRAINT ck_products_stock CHECK (stock_qty >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------------
-- 6. delivery_partners
-- ---------------------------------------------------------------------
CREATE TABLE delivery_partners (
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    name          VARCHAR(100)    NOT NULL,
    mobile        CHAR(10)        NOT NULL,
    vehicle_type  VARCHAR(20)     NOT NULL DEFAULT 'BIKE',
    status        VARCHAR(20)     NOT NULL DEFAULT 'AVAILABLE',
    active        TINYINT(1)      NOT NULL DEFAULT 1,
    version       INT             NOT NULL DEFAULT 0,
    created_at    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_delivery_partners_mobile (mobile),
    KEY idx_delivery_partners_status (active, status),
    CONSTRAINT ck_delivery_partners_mobile CHECK (REGEXP_LIKE(mobile, '^[6-9][0-9]{9}$')),
    CONSTRAINT ck_delivery_partners_vehicle CHECK (vehicle_type IN ('BIKE', 'SCOOTER', 'BICYCLE')),
    CONSTRAINT ck_delivery_partners_status CHECK (status IN ('AVAILABLE', 'ON_DELIVERY', 'OFF_DUTY'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------------
-- 7. cart_items : server-side cart for signed-in customers only
--    (guests keep their cart in the browser)
-- ---------------------------------------------------------------------
CREATE TABLE cart_items (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    customer_id  BIGINT UNSIGNED NOT NULL,
    product_id   BIGINT UNSIGNED NOT NULL,
    qty          INT             NOT NULL,
    created_at   DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at   DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_cart_items_customer_product (customer_id, product_id),
    CONSTRAINT fk_cart_items_customer FOREIGN KEY (customer_id)
        REFERENCES customers (id) ON DELETE CASCADE,
    CONSTRAINT fk_cart_items_product FOREIGN KEY (product_id)
        REFERENCES products (id) ON DELETE CASCADE,
    CONSTRAINT ck_cart_items_qty CHECK (qty > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------------
-- 8. orders
--    order_no is set by the app right after insert, in the same
--    transaction: 'SG' + (1000 + id)  ->  SG1001, SG1002, ...
--    (MySQL cannot base a generated column on AUTO_INCREMENT.)
--    Name/mobile/address are copied in so later profile edits never
--    change past orders. Only a SHA-256 hash of the guest token is kept.
-- ---------------------------------------------------------------------
CREATE TABLE orders (
    id                   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    order_no             VARCHAR(16)     NULL,
    tracking_token_hash  CHAR(64)        NOT NULL,
    customer_id          BIGINT UNSIGNED NOT NULL,
    customer_name        VARCHAR(100)    NOT NULL,
    customer_mobile      CHAR(10)        NOT NULL,
    address_line         VARCHAR(500)    NOT NULL,
    landmark             VARCHAR(200)    NULL,
    latitude             DECIMAL(9,6)    NULL,
    longitude            DECIMAL(9,6)    NULL,
    status               VARCHAR(20)     NOT NULL DEFAULT 'PLACED',
    payment_method       VARCHAR(20)     NOT NULL,
    item_total           DECIMAL(10,2)   NOT NULL,
    delivery_fee         DECIMAL(10,2)   NOT NULL DEFAULT 0.00,
    total                DECIMAL(10,2)   NOT NULL,
    delivery_partner_id  BIGINT UNSIGNED NULL,
    reject_reason        VARCHAR(255)    NULL,
    cancel_reason        VARCHAR(255)    NULL,
    placed_at            DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    accepted_at          DATETIME(3)     NULL,
    out_for_delivery_at  DATETIME(3)     NULL,
    delivered_at         DATETIME(3)     NULL,
    rejected_at          DATETIME(3)     NULL,
    cancelled_at         DATETIME(3)     NULL,
    version              INT             NOT NULL DEFAULT 0,
    created_at           DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at           DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_orders_order_no (order_no),
    UNIQUE KEY uk_orders_tracking_token_hash (tracking_token_hash),
    KEY idx_orders_status_placed (status, placed_at),
    KEY idx_orders_mobile_placed (customer_mobile, placed_at),
    KEY idx_orders_customer_placed (customer_id, placed_at),
    KEY idx_orders_partner_status (delivery_partner_id, status),
    KEY idx_orders_delivered_at (delivered_at),
    CONSTRAINT fk_orders_customer FOREIGN KEY (customer_id)
        REFERENCES customers (id),
    CONSTRAINT fk_orders_partner FOREIGN KEY (delivery_partner_id)
        REFERENCES delivery_partners (id),
    CONSTRAINT ck_orders_status CHECK (status IN
        ('PLACED', 'ACCEPTED', 'OUT_FOR_DELIVERY', 'DELIVERED', 'REJECTED', 'CANCELLED')),
    CONSTRAINT ck_orders_payment_method CHECK (payment_method IN ('CASH_ON_DELIVERY', 'UPI_ON_DELIVERY')),
    CONSTRAINT ck_orders_amounts CHECK (item_total >= 0 AND delivery_fee >= 0 AND total = item_total + delivery_fee),
    CONSTRAINT ck_orders_mobile CHECK (REGEXP_LIKE(customer_mobile, '^[6-9][0-9]{9}$')),
    CONSTRAINT ck_orders_lat CHECK (latitude IS NULL OR latitude BETWEEN -90 AND 90),
    CONSTRAINT ck_orders_lng CHECK (longitude IS NULL OR longitude BETWEEN -180 AND 180)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------------
-- 9. order_items : price frozen at order time
--    ACTIVE               -> part of the order
--    REMOVED_BY_CUSTOMER  -> customer removed it while PLACED
--    NOT_AVAILABLE        -> store marked it not available on accept
-- ---------------------------------------------------------------------
CREATE TABLE order_items (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    order_id            BIGINT UNSIGNED NOT NULL,
    product_id          BIGINT UNSIGNED NOT NULL,
    product_name        VARCHAR(120)    NOT NULL,
    unit                VARCHAR(40)     NOT NULL,
    icon                VARCHAR(16)     NOT NULL,
    unit_price          DECIMAL(10,2)   NOT NULL,
    qty                 INT             NOT NULL,
    line_total          DECIMAL(10,2)   NOT NULL,
    status              VARCHAR(30)     NOT NULL DEFAULT 'ACTIVE',
    unavailable_reason  VARCHAR(100)    NULL,
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_items_order_product (order_id, product_id),
    KEY idx_order_items_product (product_id),
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id)
        REFERENCES orders (id) ON DELETE CASCADE,
    CONSTRAINT fk_order_items_product FOREIGN KEY (product_id)
        REFERENCES products (id),
    CONSTRAINT ck_order_items_qty CHECK (qty > 0),
    CONSTRAINT ck_order_items_price CHECK (unit_price > 0),
    CONSTRAINT ck_order_items_line_total CHECK (line_total = unit_price * qty),
    CONSTRAINT ck_order_items_status CHECK (status IN ('ACTIVE', 'REMOVED_BY_CUSTOMER', 'NOT_AVAILABLE')),
    CONSTRAINT ck_order_items_reason CHECK (status <> 'NOT_AVAILABLE' OR unavailable_reason IS NOT NULL)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------------
-- 10. order_events : every change to an order, with who and when.
--     Feeds the tracker times and the "Order updates" list.
-- ---------------------------------------------------------------------
CREATE TABLE order_events (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    order_id    BIGINT UNSIGNED NOT NULL,
    actor_type  VARCHAR(20)     NOT NULL,
    actor_id    BIGINT UNSIGNED NULL,
    event_type  VARCHAR(30)     NOT NULL,
    message     VARCHAR(500)    NULL,
    created_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_order_events_order_created (order_id, created_at),
    CONSTRAINT fk_order_events_order FOREIGN KEY (order_id)
        REFERENCES orders (id) ON DELETE CASCADE,
    CONSTRAINT ck_order_events_actor CHECK (actor_type IN ('CUSTOMER', 'ADMIN', 'SYSTEM')),
    CONSTRAINT ck_order_events_type CHECK (event_type IN
        ('PLACED', 'EDITED', 'CANCELLED', 'CUSTOMER_VERIFIED', 'ITEM_UNAVAILABLE', 'ACCEPTED',
         'REJECTED', 'ASSIGNED', 'OUT_FOR_DELIVERY', 'PAYMENT_CONFIRMED', 'DELIVERED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------------
-- 11. stock_movements : every stock change (admin and orders).
--     Today's stock report is built from this table:
--       start of day = stock_qty - SUM(today's change_qty)
-- ---------------------------------------------------------------------
CREATE TABLE stock_movements (
    id                   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    product_id           BIGINT UNSIGNED NOT NULL,
    change_qty           INT             NOT NULL,
    stock_after          INT             NOT NULL,
    reason               VARCHAR(30)     NOT NULL,
    order_id             BIGINT UNSIGNED NULL,
    note                 VARCHAR(255)    NULL,
    created_by_admin_id  BIGINT UNSIGNED NULL,
    created_at           DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_stock_movements_product_created (product_id, created_at),
    KEY idx_stock_movements_created (created_at),
    KEY idx_stock_movements_order (order_id),
    CONSTRAINT fk_stock_movements_product FOREIGN KEY (product_id)
        REFERENCES products (id),
    CONSTRAINT fk_stock_movements_order FOREIGN KEY (order_id)
        REFERENCES orders (id),
    CONSTRAINT fk_stock_movements_admin FOREIGN KEY (created_by_admin_id)
        REFERENCES admin_users (id),
    CONSTRAINT ck_stock_movements_change CHECK (change_qty <> 0),
    CONSTRAINT ck_stock_movements_after CHECK (stock_after >= 0),
    CONSTRAINT ck_stock_movements_reason CHECK (reason IN
        ('RECEIVED', 'SHELF_COUNT', 'DAMAGED', 'CORRECTION', 'NEW_PRODUCT',
         'ORDER_PLACED', 'ORDER_EDITED', 'ITEM_UNAVAILABLE', 'ORDER_CANCELLED', 'ORDER_REJECTED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------------
-- 12. payments : one row per delivered order
-- ---------------------------------------------------------------------
CREATE TABLE payments (
    id                       BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    order_id                 BIGINT UNSIGNED NOT NULL,
    method                   VARCHAR(10)     NOT NULL,
    amount                   DECIMAL(10,2)   NOT NULL,
    paid_by_name             VARCHAR(100)    NOT NULL,
    collected_by_partner_id  BIGINT UNSIGNED NOT NULL,
    upi_ref                  VARCHAR(64)     NULL,
    confirmed_by_admin_id    BIGINT UNSIGNED NOT NULL,
    confirmed_at             DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    created_at               DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_payments_order (order_id),
    KEY idx_payments_confirmed_at (confirmed_at),
    KEY idx_payments_partner_confirmed (collected_by_partner_id, confirmed_at),
    CONSTRAINT fk_payments_order FOREIGN KEY (order_id)
        REFERENCES orders (id),
    CONSTRAINT fk_payments_partner FOREIGN KEY (collected_by_partner_id)
        REFERENCES delivery_partners (id),
    CONSTRAINT fk_payments_admin FOREIGN KEY (confirmed_by_admin_id)
        REFERENCES admin_users (id),
    CONSTRAINT ck_payments_method CHECK (method IN ('CASH', 'UPI')),
    CONSTRAINT ck_payments_amount CHECK (amount > 0),
    CONSTRAINT ck_payments_upi_ref CHECK (method = 'UPI' OR upi_ref IS NULL)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------------
-- 13. store_settings : exactly one row (id = 1), edited by the owner.
--     delivery_fee / free_delivery_min / first_order_cod_limit are
--     business rules still to be confirmed; they are editable here.
--     first_order_cod_limit NULL = no cap.
-- ---------------------------------------------------------------------
CREATE TABLE store_settings (
    id                     TINYINT UNSIGNED NOT NULL,
    store_name             VARCHAR(80)      NOT NULL DEFAULT 'SGMart',
    phone                  CHAR(10)         NOT NULL,
    opens_at               TIME             NOT NULL DEFAULT '07:00:00',
    closes_at              TIME             NOT NULL DEFAULT '23:00:00',
    eta_minutes            SMALLINT UNSIGNED NOT NULL DEFAULT 10,
    delivery_fee           DECIMAL(10,2)    NOT NULL DEFAULT 25.00,
    free_delivery_min      DECIMAL(10,2)    NOT NULL DEFAULT 199.00,
    first_order_cod_limit  DECIMAL(10,2)    NULL DEFAULT 1000.00,
    updated_by_admin_id    BIGINT UNSIGNED  NULL,
    created_at             DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at             DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    CONSTRAINT fk_store_settings_admin FOREIGN KEY (updated_by_admin_id)
        REFERENCES admin_users (id) ON DELETE SET NULL,
    CONSTRAINT ck_store_settings_single_row CHECK (id = 1),
    CONSTRAINT ck_store_settings_phone CHECK (REGEXP_LIKE(phone, '^[6-9][0-9]{9}$')),
    CONSTRAINT ck_store_settings_fees CHECK (delivery_fee >= 0 AND free_delivery_min >= 0),
    CONSTRAINT ck_store_settings_cap CHECK (first_order_cod_limit IS NULL OR first_order_cod_limit > 0),
    CONSTRAINT ck_store_settings_eta CHECK (eta_minutes BETWEEN 1 AND 240)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
