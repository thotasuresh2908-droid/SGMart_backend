package com.sgmart.inventory;

/** The badge shown next to a product in Admin. */
public enum StockStatus {
    IN_SHOP,
    LOW_STOCK,
    OUT_OF_STOCK,
    HIDDEN;

    /** Same threshold as the prototype: 5 or fewer shows "Low stock" / "Only N left". */
    public static final int LOW_STOCK_THRESHOLD = 5;

    public static StockStatus of(boolean active, int stockQty) {
        if (!active) {
            return HIDDEN;
        }
        if (stockQty <= 0) {
            return OUT_OF_STOCK;
        }
        return stockQty <= LOW_STOCK_THRESHOLD ? LOW_STOCK : IN_SHOP;
    }

    /** Sort order for admin lists: problems first. */
    public int sortRank() {
        return switch (this) {
            case OUT_OF_STOCK -> 0;
            case LOW_STOCK -> 1;
            case IN_SHOP -> 2;
            case HIDDEN -> 3;
        };
    }
}
