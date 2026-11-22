package com.sgmart.order;

/** Matches the CHECK constraint on orders.status. */
public enum OrderStatus {
    PLACED,
    ACCEPTED,
    OUT_FOR_DELIVERY,
    DELIVERED,
    REJECTED,
    CANCELLED
}
