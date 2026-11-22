package com.sgmart.payment;

/** SQL for Admin > Payments. :fromTime / :toTime are UTC bounds of the chosen India-time period. */
final class PaymentReportSql {

    private PaymentReportSql() {
    }

    static final String TOTALS = """
            SELECT COUNT(*)                                                 AS payments,
                   COALESCE(SUM(amount), 0)                                 AS collected,
                   COALESCE(SUM(CASE WHEN method = 'CASH' THEN amount END), 0) AS cash,
                   COALESCE(SUM(CASE WHEN method = 'UPI'  THEN amount END), 0) AS upi
            FROM payments
            WHERE confirmed_at >= :fromTime AND confirmed_at < :toTime
            """;

    /** Every active partner, even with no deliveries in the period. cash = what they must hand over. */
    static final String BY_PARTNER = """
            SELECT d.id, d.name, d.mobile, d.vehicle_type, d.status,
                   COUNT(p.id)                                                  AS deliveries,
                   COALESCE(SUM(CASE WHEN p.method = 'CASH' THEN p.amount END), 0) AS cash,
                   COALESCE(SUM(CASE WHEN p.method = 'UPI'  THEN p.amount END), 0) AS upi
            FROM delivery_partners d
            LEFT JOIN payments p ON p.collected_by_partner_id = d.id
                                AND p.confirmed_at >= :fromTime AND p.confirmed_at < :toTime
            WHERE d.active = 1
            GROUP BY d.id, d.name, d.mobile, d.vehicle_type, d.status
            ORDER BY d.name
            """;

    /** "Still to collect": accepted or on the way, not yet paid. */
    static final String STILL_TO_COLLECT = """
            SELECT o.order_no, o.customer_name, o.customer_mobile, o.status, o.payment_method, o.total,
                   o.delivery_partner_id, d.name AS partner_name, d.mobile AS partner_mobile
            FROM orders o
            LEFT JOIN delivery_partners d ON d.id = o.delivery_partner_id
            WHERE o.status IN ('ACCEPTED', 'OUT_FOR_DELIVERY')
            ORDER BY o.placed_at DESC
            """;

    static final String RECORDS = """
            SELECT p.confirmed_at, o.order_no, p.paid_by_name, p.method, p.upi_ref, p.amount,
                   d.name AS collected_by, a.name AS confirmed_by
            FROM payments p
            JOIN orders o ON o.id = p.order_id
            JOIN delivery_partners d ON d.id = p.collected_by_partner_id
            JOIN admin_users a ON a.id = p.confirmed_by_admin_id
            WHERE p.confirmed_at >= :fromTime AND p.confirmed_at < :toTime
            ORDER BY p.confirmed_at DESC, p.id DESC
            LIMIT 500
            """;
}
