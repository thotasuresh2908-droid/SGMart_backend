package com.sgmart.order;

import com.sgmart.auth.AdminUser;
import com.sgmart.auth.AdminUserRepository;
import com.sgmart.catalog.Product;
import com.sgmart.catalog.ProductDtos;
import com.sgmart.catalog.ProductRepository;
import com.sgmart.customer.Customer;
import com.sgmart.customer.CustomerDtos.CustomerAdminView;
import com.sgmart.customer.CustomerRepository;
import com.sgmart.delivery.DeliveryPartner;
import com.sgmart.delivery.DeliveryPartnerRepository;
import com.sgmart.order.OrderDtos.AdminOrderView;
import com.sgmart.order.OrderDtos.OrderItemView;
import com.sgmart.order.OrderDtos.OrderUpdate;
import com.sgmart.order.OrderDtos.OrderView;
import com.sgmart.order.OrderDtos.PartnerInfo;
import com.sgmart.order.OrderDtos.PaymentInfo;
import com.sgmart.payment.Payment;
import com.sgmart.payment.PaymentRepository;
import com.sgmart.store.StoreSettings;
import com.sgmart.store.StoreSettingsService;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Builds order views with a fixed number of queries (events, photos, partners, payments, admins,
 * customers), whatever the number of orders.
 */
@Component
public class OrderMapper {

    private final OrderEventRepository events;
    private final ProductRepository products;
    private final CustomerRepository customers;
    private final DeliveryPartnerRepository partners;
    private final PaymentRepository payments;
    private final AdminUserRepository admins;
    private final StoreSettingsService store;

    public OrderMapper(OrderEventRepository events, ProductRepository products, CustomerRepository customers,
                       DeliveryPartnerRepository partners, PaymentRepository payments, AdminUserRepository admins,
                       StoreSettingsService store) {
        this.events = events;
        this.products = products;
        this.customers = customers;
        this.partners = partners;
        this.payments = payments;
        this.admins = admins;
        this.store = store;
    }

    public OrderView toView(CustomerOrder order) {
        return toViews(List.of(order)).get(0);
    }

    public List<OrderView> toViews(List<CustomerOrder> orders) {
        if (orders.isEmpty()) {
            return List.of();
        }
        List<Long> ids = orders.stream().map(CustomerOrder::getId).toList();
        Map<Long, List<OrderEvent>> eventsByOrder = events.findByOrderIdInOrderByCreatedAtAscIdAsc(ids).stream()
                .collect(Collectors.groupingBy(OrderEvent::getOrderId));
        Map<Long, String> imageKeys = imageKeys(orders.stream()
                .flatMap(o -> o.getItems().stream().map(OrderItem::getProductId)).collect(Collectors.toSet()));
        Map<Long, Payment> paymentByOrder = payments.findByOrderIdIn(ids).stream()
                .collect(Collectors.toMap(Payment::getOrderId, Function.identity(), (a, b) -> a));

        Set<Long> partnerIds = new HashSet<>();
        orders.stream().map(CustomerOrder::getDeliveryPartnerId).filter(Objects::nonNull).forEach(partnerIds::add);
        paymentByOrder.values().forEach(p -> partnerIds.add(p.getCollectedByPartnerId()));
        Map<Long, DeliveryPartner> partnerById = partners.findAllById(partnerIds).stream()
                .collect(Collectors.toMap(DeliveryPartner::getId, Function.identity()));
        Map<Long, String> adminNames = admins.findAllById(paymentByOrder.values().stream()
                        .map(Payment::getConfirmedByAdminId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(AdminUser::getId, AdminUser::getName));
        StoreSettings settings = store.current();

        return orders.stream().map(o -> {
            List<OrderItemView> items = o.getItems().stream()
                    .filter(i -> i.getStatus() != OrderItemStatus.REMOVED_BY_CUSTOMER)
                    .map(i -> new OrderItemView(i.getId(), i.getProductId(), i.getProductName(), i.getUnit(),
                            i.getIcon(), ProductDtos.imageUrl(imageKeys.get(i.getProductId())), i.getUnitPrice(),
                            i.getQty(), i.getLineTotal(), i.getStatus(), i.getUnavailableReason()))
                    .toList();
            List<OrderUpdate> updates = eventsByOrder.getOrDefault(o.getId(), List.of()).stream()
                    .map(e -> new OrderUpdate(e.getCreatedAt(), e.getActorType().label(), e.getEventType(), e.getMessage()))
                    .toList();
            DeliveryPartner partner = o.getDeliveryPartnerId() == null ? null : partnerById.get(o.getDeliveryPartnerId());
            PartnerInfo partnerInfo = partner == null ? null
                    : new PartnerInfo(partner.getId(), partner.getName(), partner.getMobile(), partner.getVehicleType());
            Payment pay = paymentByOrder.get(o.getId());
            PaymentInfo paymentInfo = pay == null ? null : new PaymentInfo(pay.getMethod(), pay.getAmount(),
                    pay.getPaidByName(), nameOf(partnerById.get(pay.getCollectedByPartnerId())), pay.getUpiRef(),
                    adminNames.getOrDefault(pay.getConfirmedByAdminId(), "Store"), pay.getConfirmedAt());
            int itemCount = o.activeItems().stream().mapToInt(OrderItem::getQty).sum();
            boolean waiting = o.getStatus() == OrderStatus.PLACED;
            return new OrderView(o.getOrderNo(), o.getStatus(), o.getPlacedAt(),
                    o.getPlacedAt().plus(Duration.ofMinutes(settings.getEtaMinutes())),
                    o.getAcceptedAt(), o.getOutForDeliveryAt(), o.getDeliveredAt(), o.getRejectedAt(),
                    o.getCancelledAt(), o.getCustomerName(), o.getCustomerMobile(), o.getAddressLine(),
                    o.getLandmark(), o.getLatitude(), o.getLongitude(), o.getPaymentMethod(), o.getItemTotal(),
                    o.getDeliveryFee(), o.getTotal(), itemCount, items, updates, waiting, waiting,
                    o.getRejectReason(), o.getCancelReason(), settings.getPhone(), partnerInfo, paymentInfo);
        }).toList();
    }

    public List<AdminOrderView> toAdminViews(List<CustomerOrder> orders) {
        List<OrderView> views = toViews(orders);
        Map<Long, Customer> byId = customers.findAllById(
                        orders.stream().map(CustomerOrder::getCustomerId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(Customer::getId, Function.identity()));
        return IntStream.range(0, orders.size())
                .mapToObj(i -> {
                    Customer c = byId.get(orders.get(i).getCustomerId());
                    return new AdminOrderView(views.get(i), c == null ? null : CustomerAdminView.of(c));
                })
                .toList();
    }

    private Map<Long, String> imageKeys(Collection<Long> productIds) {
        return products.findAllById(productIds).stream()
                .filter(p -> p.getImageKey() != null)
                .collect(Collectors.toMap(Product::getId, Product::getImageKey, (a, b) -> a));
    }

    private static String nameOf(DeliveryPartner p) {
        return p == null ? "Delivery partner" : p.getName();
    }
}
