package com.sgmart.order;

import com.sgmart.common.ApiException;
import com.sgmart.common.Money;
import com.sgmart.common.PageResult;
import com.sgmart.common.StoreTime;
import com.sgmart.customer.Customer;
import com.sgmart.customer.CustomerService;
import com.sgmart.inventory.StockReason;
import com.sgmart.inventory.StockService;
import com.sgmart.order.OrderDtos.AcceptRequest;
import com.sgmart.order.OrderDtos.AdminOrderView;
import com.sgmart.order.OrderDtos.OrderCounts;
import com.sgmart.order.OrderDtos.UnavailableItem;
import com.sgmart.store.StoreSettingsService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** The store side of orders: queue, counts, item-wise accept, reject. Delivery and payment are in Part 4. */
@Service
public class AdminOrderService {

    public static final int MAX_PAGE_SIZE = 50;

    static final String COUNTS_SQL = """
            SELECT COALESCE(SUM(status = 'PLACED'), 0)           AS new_orders,
                   COALESCE(SUM(status = 'ACCEPTED'), 0)         AS accepted,
                   COALESCE(SUM(status = 'OUT_FOR_DELIVERY'), 0) AS out_for_delivery,
                   COALESCE(SUM(status = 'DELIVERED' AND delivered_at >= :dayStart AND delivered_at < :dayEnd), 0) AS delivered_today,
                   COALESCE(SUM(CASE WHEN status = 'DELIVERED' AND delivered_at >= :dayStart AND delivered_at < :dayEnd
                                     THEN total END), 0)          AS sales_today,
                   COALESCE(SUM(status = 'REJECTED' AND rejected_at >= :dayStart AND rejected_at < :dayEnd), 0) AS rejected_today,
                   COALESCE(SUM(status = 'CANCELLED' AND cancelled_at >= :dayStart AND cancelled_at < :dayEnd), 0) AS cancelled_today
            FROM orders
            """;

    private final CustomerOrderRepository orders;
    private final OrderEventRepository events;
    private final StockService stock;
    private final CustomerService customers;
    private final StoreSettingsService store;
    private final OrderMapper mapper;
    private final NamedParameterJdbcTemplate jdbc;
    private final ApplicationEventPublisher publisher;
    private final Clock clock;

    public AdminOrderService(CustomerOrderRepository orders, OrderEventRepository events, StockService stock,
                             CustomerService customers, StoreSettingsService store, OrderMapper mapper,
                             NamedParameterJdbcTemplate jdbc, ApplicationEventPublisher publisher, Clock clock) {
        this.orders = orders;
        this.events = events;
        this.stock = stock;
        this.customers = customers;
        this.store = store;
        this.mapper = mapper;
        this.jdbc = jdbc;
        this.publisher = publisher;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ queue

    /** Order queue. status and date (India time) are optional filters. New orders come first. */
    @Transactional(readOnly = true)
    public PageResult<AdminOrderView> list(OrderStatus status, LocalDate date, int page, int size) {
        Instant from = date == null ? null : date.atStartOfDay(StoreTime.ZONE).toInstant();
        Instant to = date == null ? null : date.plusDays(1).atStartOfDay(StoreTime.ZONE).toInstant();
        Page<CustomerOrder> result = orders.findForAdmin(status, from, to,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE)));
        return new PageResult<>(mapper.toAdminViews(result.getContent()), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public AdminOrderView get(String orderNo) {
        return mapper.toAdminViews(List.of(load(orderNo))).get(0);
    }

    /** The tiles: New, Accepted, Out for delivery, Delivered today, Delivered sales today. */
    @Transactional(readOnly = true)
    public OrderCounts counts() {
        LocalDate today = LocalDate.now(clock.withZone(StoreTime.ZONE));
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("dayStart", Timestamp.from(today.atStartOfDay(StoreTime.ZONE).toInstant()))
                .addValue("dayEnd", Timestamp.from(today.plusDays(1).atStartOfDay(StoreTime.ZONE).toInstant()));
        return jdbc.queryForObject(COUNTS_SQL, params, (rs, i) -> new OrderCounts(
                rs.getLong("new_orders"), rs.getLong("accepted"), rs.getLong("out_for_delivery"),
                rs.getLong("delivered_today"), Money.of(rs.getBigDecimal("sales_today")),
                rs.getLong("rejected_today"), rs.getLong("cancelled_today")));
    }

    // ------------------------------------------------------------------ accept and reject

    /**
     * Accepts a new order. Items listed as unavailable are marked "Not available" with the reason,
     * their stock goes back, and the totals and delivery fee are recalculated.
     * A New customer must be called first: the admin confirms the call, which verifies the number.
     */
    @Transactional
    public AdminOrderView accept(String orderNo, AcceptRequest request, Long adminId) {
        CustomerOrder o = lock(orderNo);
        OrderRules.requireNewForStore(o);
        Instant now = Instant.now(clock);

        Customer customer = customers.get(o.getCustomerId());
        if (customer.isNewCustomer()) {
            if (!request.customerCallConfirmed()) {
                throw ApiException.conflict("CALL_NEW_CUSTOMER",
                        "This is a new customer. Call " + o.getCustomerMobile()
                                + " to confirm the order, then tick \"I called the customer\".");
            }
            customers.markVerified(customer.getId(), adminId);
            record(o, adminId, OrderEventType.CUSTOMER_VERIFIED, "Mobile number confirmed by phone call.");
        }

        Map<Long, OrderItem> active = o.activeItems().stream()
                .collect(Collectors.toMap(OrderItem::getId, Function.identity(), (a, b) -> a, LinkedHashMap::new));
        List<UnavailableItem> unavailable = request.unavailableItems() == null ? List.of() : request.unavailableItems();
        Map<Long, String> reasonByItem = new LinkedHashMap<>();
        for (UnavailableItem u : unavailable) {
            if (!active.containsKey(u.itemId())) {
                throw ApiException.badRequest("INVALID_ITEM", "One of the items isn't part of this order.", "unavailableItems");
            }
            reasonByItem.put(u.itemId(), u.reason().trim());
        }
        if (!active.isEmpty() && reasonByItem.size() == active.size()) {
            throw ApiException.badRequest("NOTHING_TO_ACCEPT",
                    "None of the items are available, so this order can't be accepted. Reject it instead.", null);
        }

        if (!reasonByItem.isEmpty()) {
            Map<Long, Integer> toReturn = new HashMap<>();
            List<String> notes = new ArrayList<>();
            reasonByItem.forEach((itemId, reason) -> {
                OrderItem item = active.get(itemId);
                item.markNotAvailable(reason);
                toReturn.merge(item.getProductId(), item.getQty(), Integer::sum);
                notes.add(item.getQty() + " × " + item.getProductName() + " (" + reason + ")");
            });
            stock.returnForOrder(toReturn, o.getId(), StockReason.ITEM_UNAVAILABLE, adminId);
            o.recalculateTotals(store::deliveryFeeFor);
            record(o, adminId, OrderEventType.ITEM_UNAVAILABLE,
                    "Not available: " + String.join(", ", notes) + ". New total " + Money.format(o.getTotal()) + ".");
        }

        o.accept(now);
        record(o, adminId, OrderEventType.ACCEPTED, "Order accepted.");
        publisher.publishEvent(new OrderChangedEvent(o.getOrderNo(), o.getStatus(), OrderEventType.ACCEPTED));
        return mapper.toAdminViews(List.of(o)).get(0);
    }

    /** Rejects a new order with a reason the customer sees. All stock goes back. */
    @Transactional
    public AdminOrderView reject(String orderNo, String reason, Long adminId) {
        CustomerOrder o = lock(orderNo);
        OrderRules.requireNewForStore(o);
        stock.returnForOrder(OrderService.quantities(o.activeItems()), o.getId(), StockReason.ORDER_REJECTED, adminId);
        o.reject(reason.trim(), Instant.now(clock));
        record(o, adminId, OrderEventType.REJECTED, "Order rejected: " + reason.trim() + ".");
        publisher.publishEvent(new OrderChangedEvent(o.getOrderNo(), o.getStatus(), OrderEventType.REJECTED));
        return mapper.toAdminViews(List.of(o)).get(0);
    }

    // ------------------------------------------------------------------ helpers

    private CustomerOrder load(String orderNo) {
        return orders.findByOrderNo(orderNo).orElseThrow(() -> ApiException.notFound("Order not found."));
    }

    private CustomerOrder lock(String orderNo) {
        return orders.findByOrderNoForUpdate(orderNo).orElseThrow(() -> ApiException.notFound("Order not found."));
    }

    private void record(CustomerOrder o, Long adminId, OrderEventType type, String message) {
        events.save(new OrderEvent(o.getId(), ActorType.ADMIN, adminId, type, message));
    }
}
