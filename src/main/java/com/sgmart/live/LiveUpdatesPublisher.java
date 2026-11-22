package com.sgmart.live;

import com.sgmart.inventory.StockChangedEvent;
import com.sgmart.order.OrderChangedEvent;
import com.sgmart.order.OrderEventType;
import com.sgmart.order.OrderStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;

/**
 * Sends changes to browsers only after the database transaction has committed,
 * so nobody ever sees a change that was rolled back. Messages carry just what changed;
 * the web app then re-fetches the order or list through the REST API.
 */
@Component
public class LiveUpdatesPublisher {

    private static final Logger log = LoggerFactory.getLogger(LiveUpdatesPublisher.class);

    /** What a browser receives. */
    public record OrderUpdateMessage(String orderNo, OrderStatus status, OrderEventType change, Instant at) {
    }

    private final SimpMessagingTemplate messaging;

    public LiveUpdatesPublisher(SimpMessagingTemplate messaging) {
        this.messaging = messaging;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderChanged(OrderChangedEvent event) {
        OrderUpdateMessage message = new OrderUpdateMessage(event.orderNo(), event.status(), event.change(), Instant.now());
        send(LiveTopics.ADMIN_ORDERS, message);
        send(LiveTopics.ORDER_PREFIX + event.orderNo(), message);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStockChanged(StockChangedEvent event) {
        send(LiveTopics.ADMIN_STOCK, event);
    }

    private void send(String topic, Object payload) {
        try {
            messaging.convertAndSend(topic, payload);
        } catch (RuntimeException e) {
            // A failed live update must never break the request; the screen still refreshes on its own.
            log.warn("Live update to {} failed: {}", topic, e.getMessage());
        }
    }
}
