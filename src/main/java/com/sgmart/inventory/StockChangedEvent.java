package com.sgmart.inventory;

/** Published when a product's stock changes. Sent to admins over WebSocket after commit. */
public record StockChangedEvent(Long productId, int stockQty, StockStatus status) {
}
