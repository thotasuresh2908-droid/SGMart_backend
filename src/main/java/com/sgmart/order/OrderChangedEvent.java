package com.sgmart.order;

/**
 * Published after an order changes. Part 4 sends it over WebSocket after the transaction commits,
 * so admins see new orders and customers see status changes live.
 */
public record OrderChangedEvent(String orderNo, OrderStatus status, OrderEventType change) {
}
