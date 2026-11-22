package com.sgmart.inventory;

/** Why stock changed. Must match the CHECK constraint on stock_movements.reason. */
public enum StockReason {
    // Admin changes
    RECEIVED("Stock received"),
    SHELF_COUNT("Shelf count"),
    DAMAGED("Damaged or expired"),
    CORRECTION("Correction"),
    NEW_PRODUCT("New product added"),
    // Order changes (Part 3)
    ORDER_PLACED("Order placed"),
    ORDER_EDITED("Order edited"),
    ITEM_UNAVAILABLE("Item not available"),
    ORDER_CANCELLED("Order cancelled"),
    ORDER_REJECTED("Order rejected");

    private final String label;

    StockReason(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** Reasons an admin may pick for "Set exact count". */
    public boolean isExactCountReason() {
        return this == SHELF_COUNT || this == DAMAGED || this == CORRECTION;
    }
}
