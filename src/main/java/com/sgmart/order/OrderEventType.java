package com.sgmart.order;

/** Matches the CHECK constraint on order_events.event_type. */
public enum OrderEventType {
    PLACED,
    EDITED,
    CANCELLED,
    CUSTOMER_VERIFIED,
    ITEM_UNAVAILABLE,
    ACCEPTED,
    REJECTED,
    ASSIGNED,
    OUT_FOR_DELIVERY,
    PAYMENT_CONFIRMED,
    DELIVERED
}
