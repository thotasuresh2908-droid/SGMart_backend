package com.sgmart.order;

public enum OrderItemStatus {
    /** Part of the order. */
    ACTIVE,
    /** The customer removed it while the order was waiting for the store. */
    REMOVED_BY_CUSTOMER,
    /** The store marked it not available when accepting. */
    NOT_AVAILABLE
}
