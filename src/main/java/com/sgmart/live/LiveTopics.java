package com.sgmart.live;

/** WebSocket (STOMP) topics. Connect to /ws, then SUBSCRIBE. */
public final class LiveTopics {

    /** Admins only: new, edited and cancelled orders and every status change. */
    public static final String ADMIN_ORDERS = "/topic/admin/orders";
    /** Admins only: stock changes (for out-of-stock badges). */
    public static final String ADMIN_STOCK = "/topic/admin/stock";
    /** One order: its customer (session or X-Tracking-Token header on SUBSCRIBE) and admins. */
    public static final String ORDER_PREFIX = "/topic/orders/";
    public static final String ADMIN_PREFIX = "/topic/admin/";

    private LiveTopics() {
    }
}
