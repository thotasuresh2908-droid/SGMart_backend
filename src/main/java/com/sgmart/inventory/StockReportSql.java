package com.sgmart.inventory;

/**
 * SQL for the Today's stock report. Kept in one place so it can be tested directly on MySQL.
 * :dayStart and :dayEnd are the UTC instants of 00:00 India time on the chosen day and the next day.
 */
final class StockReportSql {

    private StockReportSql() {
    }

    /**
     * One row per product that isn't removed.
     *  start_of_day = stock now - every change since dayStart
     *  end_of_day   = stock now - every change since dayEnd   (equals stock now for today)
     *  added        = stock received, new products, and upward counts/corrections that day
     *  removed      = downward counts, damaged, corrections that day
     *  ordered      = net units taken by orders that day (placed/edited minus returned)
     */
    static final String PRODUCT_ROWS = """
            SELECT p.id, p.name, p.unit, p.icon, p.image_key, p.stock_qty, p.active,
                   COALESCE(SUM(m.change_qty), 0) AS change_since_start,
                   COALESCE(SUM(CASE WHEN m.created_at >= :dayEnd THEN m.change_qty END), 0) AS change_since_end,
                   COALESCE(SUM(CASE WHEN m.created_at < :dayEnd AND m.change_qty > 0
                                      AND m.reason IN ('RECEIVED','NEW_PRODUCT','SHELF_COUNT','CORRECTION')
                                     THEN m.change_qty END), 0) AS added,
                   COALESCE(SUM(CASE WHEN m.created_at < :dayEnd AND m.change_qty < 0
                                      AND m.reason IN ('SHELF_COUNT','DAMAGED','CORRECTION')
                                     THEN -m.change_qty END), 0) AS removed,
                   COALESCE(-SUM(CASE WHEN m.created_at < :dayEnd
                                      AND m.reason IN ('ORDER_PLACED','ORDER_EDITED','ITEM_UNAVAILABLE',
                                                       'ORDER_CANCELLED','ORDER_REJECTED')
                                     THEN m.change_qty END), 0) AS ordered
            FROM products p
            LEFT JOIN stock_movements m ON m.product_id = p.id AND m.created_at >= :dayStart
            WHERE p.deleted_at IS NULL
            GROUP BY p.id, p.name, p.unit, p.icon, p.image_key, p.stock_qty, p.active
            """;

    /** Units delivered per product: items still ACTIVE on orders delivered that day. */
    static final String DELIVERED_UNITS = """
            SELECT oi.product_id, SUM(oi.qty) AS units
            FROM order_items oi
            JOIN orders o ON o.id = oi.order_id
            WHERE o.status = 'DELIVERED' AND o.delivered_at >= :dayStart AND o.delivered_at < :dayEnd
              AND oi.status = 'ACTIVE'
            GROUP BY oi.product_id
            """;

    static final String DELIVERED_ORDERS = """
            SELECT COUNT(*) FROM orders
            WHERE status = 'DELIVERED' AND delivered_at >= :dayStart AND delivered_at < :dayEnd
            """;

    /** The "Stock added today" log: every admin stock change that day, newest first. */
    static final String RESTOCK_LOG = """
            SELECT m.created_at, p.id AS product_id, p.name, p.unit, p.icon, p.image_key,
                   m.change_qty, m.stock_after, m.reason, m.note, a.name AS admin_name
            FROM stock_movements m
            JOIN products p ON p.id = m.product_id
            LEFT JOIN admin_users a ON a.id = m.created_by_admin_id
            WHERE m.created_at >= :dayStart AND m.created_at < :dayEnd
              AND m.reason IN ('RECEIVED','NEW_PRODUCT','SHELF_COUNT','DAMAGED','CORRECTION')
            ORDER BY m.created_at DESC, m.id DESC
            """;

    /** "Delivered today": each delivered order with its partner, units and item names. */
    static final String DELIVERIES = """
            SELECT o.delivered_at, o.placed_at, o.order_no, o.customer_name, o.customer_mobile, o.total,
                   d.id AS partner_id, d.name AS partner_name, d.mobile AS partner_mobile,
                   (SELECT COALESCE(SUM(oi.qty), 0) FROM order_items oi
                     WHERE oi.order_id = o.id AND oi.status = 'ACTIVE') AS units,
                   (SELECT GROUP_CONCAT(CONCAT(oi.product_name, ' ×', oi.qty) ORDER BY oi.id SEPARATOR ', ')
                      FROM order_items oi WHERE oi.order_id = o.id AND oi.status = 'ACTIVE') AS item_names
            FROM orders o
            LEFT JOIN delivery_partners d ON d.id = o.delivery_partner_id
            WHERE o.status = 'DELIVERED' AND o.delivered_at >= :dayStart AND o.delivered_at < :dayEnd
            ORDER BY o.delivered_at DESC
            """;

    static final String ACTIVE_PARTNERS = """
            SELECT id, name, mobile, vehicle_type FROM delivery_partners WHERE active = 1 ORDER BY name
            """;
}
