package com.sgmart.order;

import com.sgmart.catalog.Product;
import com.sgmart.catalog.ProductRepository;
import com.sgmart.common.ApiException;
import com.sgmart.common.Money;
import com.sgmart.common.PhoneNumbers;
import com.sgmart.customer.Customer;
import com.sgmart.customer.CustomerService;
import com.sgmart.inventory.StockReason;
import com.sgmart.inventory.StockService;
import com.sgmart.order.OrderDtos.ClaimResult;
import com.sgmart.order.OrderDtos.EditItemsRequest;
import com.sgmart.order.OrderDtos.OrderView;
import com.sgmart.order.OrderDtos.PlaceOrderRequest;
import com.sgmart.order.OrderDtos.PlacedOrder;
import com.sgmart.store.StoreSettings;
import com.sgmart.store.StoreSettingsService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** The customer side of orders: place, view, edit, cancel, guest "My orders", link guest orders. */
@Service
public class OrderService {

    private final CustomerOrderRepository orders;
    private final OrderEventRepository events;
    private final ProductRepository products;
    private final StockService stock;
    private final CustomerService customers;
    private final StoreSettingsService store;
    private final OrderMapper mapper;
    private final ApplicationEventPublisher publisher;
    private final Clock clock;

    public OrderService(CustomerOrderRepository orders, OrderEventRepository events, ProductRepository products,
                        StockService stock, CustomerService customers, StoreSettingsService store,
                        OrderMapper mapper, ApplicationEventPublisher publisher, Clock clock) {
        this.orders = orders;
        this.events = events;
        this.products = products;
        this.stock = stock;
        this.customers = customers;
        this.store = store;
        this.mapper = mapper;
        this.publisher = publisher;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ place

    /**
     * Checkout. All in one transaction: if any item is short or a rule fails, nothing is saved
     * and no stock is taken.
     */
    @Transactional
    public PlacedOrder place(PlaceOrderRequest request, Long signedInCustomerId) {
        StoreSettings settings = store.current();
        String mobile = PhoneNumbers.normalize(request.mobile(), "mobile");
        Map<Long, Integer> qty = OrderRules.mergeItems(request.items());
        Map<Long, Product> byId = products.findAllById(qty.keySet()).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        for (Long productId : qty.keySet()) {
            requireOrderable(byId.get(productId));
        }

        Customer customer = signedInCustomerId != null
                ? customers.attachMobile(signedInCustomerId, mobile)
                : customers.findOrCreateGuest(request.customerName().trim(), mobile);
        if (customer.isBlocked()) {
            throw ApiException.forbidden("CUSTOMER_BLOCKED",
                    "We can't take online orders from this number. Please call the store on " + settings.getPhone() + ".");
        }

        String token = TrackingTokens.newToken();
        CustomerOrder order = CustomerOrder.place(TrackingTokens.hash(token), customer.getId(),
                request.customerName().trim(), mobile, request.addressLine().trim(),
                OrderRules.blankToNull(request.landmark()), request.latitude(), request.longitude(),
                request.paymentMethod(), Instant.now(clock));
        qty.forEach((productId, q) -> {
            Product p = byId.get(productId);
            order.addItem(new OrderItem(order, p.getId(), p.getName(), p.getUnit(), p.getIcon(), p.getPrice(), q));
        });
        order.recalculateTotals(store::deliveryFeeFor);
        OrderRules.checkFirstOrderCap(customer, order.getTotal(), settings.getFirstOrderCodLimit(), settings.getPhone());

        CustomerOrder saved = orders.save(order);
        saved.assignOrderNo();
        stock.takeForOrder(qty, saved.getId(), StockReason.ORDER_PLACED);

        int units = qty.values().stream().mapToInt(Integer::intValue).sum();
        record(saved, ActorType.CUSTOMER, customer.getId(), OrderEventType.PLACED,
                "Order placed: " + units + " item" + (units == 1 ? "" : "s") + ", " + Money.format(saved.getTotal()) + ".");
        publisher.publishEvent(new OrderChangedEvent(saved.getOrderNo(), saved.getStatus(), OrderEventType.PLACED));
        return new PlacedOrder(saved.getOrderNo(), token, mapper.toView(saved));
    }

    // ------------------------------------------------------------------ view

    @Transactional(readOnly = true)
    public OrderView view(String orderNo, String trackingToken, Long signedInCustomerId) {
        CustomerOrder o = orders.findByOrderNo(orderNo).orElseThrow(OrderService::notFound);
        checkAccess(o, trackingToken, signedInCustomerId);
        return mapper.toView(o);
    }

    /** Guest "My orders": orders whose tracking tokens are saved in this browser. */
    @Transactional(readOnly = true)
    public List<OrderView> lookup(List<String> trackingTokens) {
        Set<String> hashes = trackingTokens.stream().filter(t -> t != null && !t.isBlank())
                .map(t -> TrackingTokens.hash(t.trim())).collect(Collectors.toSet());
        if (hashes.isEmpty()) {
            return List.of();
        }
        List<CustomerOrder> found = new ArrayList<>(orders.findByTrackingTokenHashIn(hashes));
        found.sort(Comparator.comparing(CustomerOrder::getPlacedAt).reversed());
        return mapper.toViews(found);
    }

    @Transactional(readOnly = true)
    public List<OrderView> myOrders(Long customerId) {
        return mapper.toViews(orders.findByCustomerIdOrderByPlacedAtDesc(customerId));
    }

    /**
     * After "Continue with Google": links guest orders saved in this browser to the account.
     * Orders from another mobile number than the account's are skipped (they stay trackable by token).
     */
    @Transactional
    public ClaimResult claim(Long customerId, List<String> trackingTokens) {
        Set<String> hashes = trackingTokens.stream().filter(t -> t != null && !t.isBlank())
                .map(t -> TrackingTokens.hash(t.trim())).collect(Collectors.toSet());
        if (hashes.isEmpty()) {
            return new ClaimResult(0, 0);
        }
        Set<Long> mergedGuests = new HashSet<>();
        int linked = 0;
        int skipped = 0;
        for (CustomerOrder o : orders.findByTrackingTokenHashIn(hashes)) {
            if (o.getCustomerId().equals(customerId) || mergedGuests.contains(o.getCustomerId())) {
                linked++;
                continue;
            }
            Customer me = customers.get(customerId);
            Customer owner = customers.get(o.getCustomerId());
            boolean sameNumber = me.getMobile() == null || me.getMobile().equals(owner.getMobile());
            if (owner.getGoogleSub() == null && owner.getMobile() != null && sameNumber) {
                customers.attachMobile(customerId, owner.getMobile());
                mergedGuests.add(owner.getId());
                linked++;
            } else {
                skipped++;
            }
        }
        return new ClaimResult(linked, skipped);
    }

    // ------------------------------------------------------------------ edit and cancel

    /**
     * "Add or remove items" while the store hasn't accepted. Items already in the order keep their
     * price; newly added items use today's price. Only the difference in stock is taken or returned.
     */
    @Transactional
    public OrderView editItems(String orderNo, String trackingToken, Long signedInCustomerId, EditItemsRequest request) {
        StoreSettings settings = store.current();
        CustomerOrder o = orders.findByOrderNoForUpdate(orderNo).orElseThrow(OrderService::notFound);
        checkAccess(o, trackingToken, signedInCustomerId);
        OrderRules.requireWaitingForCustomer(o, settings.getPhone());

        Map<Long, Integer> desired = OrderRules.mergeItems(request.items());
        if (desired.isEmpty()) {
            throw ApiException.badRequest("ORDER_EMPTY",
                    "An order needs at least one item. To remove everything, cancel the order instead.", "items");
        }

        Map<Long, Integer> toTake = new HashMap<>();
        Map<Long, Integer> toReturn = new HashMap<>();
        List<String> changes = new ArrayList<>();
        Set<Long> activeBefore = o.activeItems().stream().map(OrderItem::getProductId).collect(Collectors.toSet());

        for (OrderItem item : o.activeItems()) {
            int have = item.getQty();
            int want = desired.getOrDefault(item.getProductId(), 0);
            if (want == 0) {
                item.markRemovedByCustomer();
                toReturn.merge(item.getProductId(), have, Integer::sum);
                changes.add("removed " + item.getProductName());
            } else if (want != have) {
                if (want > have) {
                    toTake.merge(item.getProductId(), want - have, Integer::sum);
                } else {
                    toReturn.merge(item.getProductId(), have - want, Integer::sum);
                }
                item.setQty(want);
                changes.add(item.getProductName() + " " + have + " → " + want);
            }
        }
        for (Map.Entry<Long, Integer> e : desired.entrySet()) {
            if (activeBefore.contains(e.getKey())) {
                continue;
            }
            Product p = products.findLive(e.getKey()).orElse(null);
            requireOrderable(p);
            int want = e.getValue();
            o.itemForProduct(p.getId()).ifPresentOrElse(
                    old -> old.reactivate(p.getName(), p.getUnit(), p.getIcon(), p.getPrice(), want),
                    () -> o.addItem(new OrderItem(o, p.getId(), p.getName(), p.getUnit(), p.getIcon(), p.getPrice(), want)));
            toTake.merge(p.getId(), want, Integer::sum);
            changes.add("added " + want + " × " + p.getName());
        }
        if (changes.isEmpty()) {
            return mapper.toView(o);
        }

        stock.returnForOrder(toReturn, o.getId(), StockReason.ORDER_EDITED, null);
        stock.takeForOrder(toTake, o.getId(), StockReason.ORDER_EDITED);
        o.recalculateTotals(store::deliveryFeeFor);
        OrderRules.checkFirstOrderCap(customers.get(o.getCustomerId()), o.getTotal(),
                settings.getFirstOrderCodLimit(), settings.getPhone());

        record(o, ActorType.CUSTOMER, o.getCustomerId(), OrderEventType.EDITED,
                OrderRules.capitalize(String.join(", ", changes)) + ". New total " + Money.format(o.getTotal()) + ".");
        publisher.publishEvent(new OrderChangedEvent(o.getOrderNo(), o.getStatus(), OrderEventType.EDITED));
        return mapper.toView(o);
    }

    @Transactional
    public OrderView cancel(String orderNo, String trackingToken, Long signedInCustomerId, String reason) {
        StoreSettings settings = store.current();
        CustomerOrder o = orders.findByOrderNoForUpdate(orderNo).orElseThrow(OrderService::notFound);
        checkAccess(o, trackingToken, signedInCustomerId);
        OrderRules.requireWaitingForCustomer(o, settings.getPhone());

        stock.returnForOrder(quantities(o.activeItems()), o.getId(), StockReason.ORDER_CANCELLED, null);
        o.cancel(reason.trim(), Instant.now(clock));
        record(o, ActorType.CUSTOMER, o.getCustomerId(), OrderEventType.CANCELLED,
                "Cancelled the order: " + reason.trim() + ".");
        publisher.publishEvent(new OrderChangedEvent(o.getOrderNo(), o.getStatus(), OrderEventType.CANCELLED));
        return mapper.toView(o);
    }

    // ------------------------------------------------------------------ helpers

    /** The order's customer (signed in) or anyone holding its tracking token. Others get "not found". */
    static void checkAccess(CustomerOrder o, String trackingToken, Long signedInCustomerId) {
        boolean owner = signedInCustomerId != null && signedInCustomerId.equals(o.getCustomerId());
        if (!owner && !TrackingTokens.matches(trackingToken, o.getTrackingTokenHash())) {
            throw notFound();
        }
    }

    static void requireOrderable(Product p) {
        if (p == null) {
            throw ApiException.conflict("PRODUCT_UNAVAILABLE",
                    "A product in your cart is no longer available. Remove it and try again.");
        }
        if (!p.isActive() || p.isRemoved() || !p.getCategory().isActive()) {
            throw ApiException.conflict("PRODUCT_UNAVAILABLE",
                    p.getName() + " is no longer available. Remove it from your cart.");
        }
    }

    static Map<Long, Integer> quantities(List<OrderItem> items) {
        Map<Long, Integer> qty = new HashMap<>();
        items.forEach(i -> qty.merge(i.getProductId(), i.getQty(), Integer::sum));
        return qty;
    }

    private void record(CustomerOrder o, ActorType actor, Long actorId, OrderEventType type, String message) {
        events.save(new OrderEvent(o.getId(), actor, actorId, type, message));
    }

    private static ApiException notFound() {
        return ApiException.notFound("Order not found.");
    }
}
