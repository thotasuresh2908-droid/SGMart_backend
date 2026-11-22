package com.sgmart.order;

import com.sgmart.auth.AdminUserService;
import com.sgmart.common.ApiException;
import com.sgmart.common.Money;
import com.sgmart.customer.CustomerService;
import com.sgmart.delivery.DeliveryPartner;
import com.sgmart.delivery.DeliveryPartnerRepository;
import com.sgmart.delivery.PartnerStatus;
import com.sgmart.order.OrderDtos.AdminOrderView;
import com.sgmart.order.OrderDtos.DeliverRequest;
import com.sgmart.payment.Payment;
import com.sgmart.payment.PaymentMode;
import com.sgmart.payment.PaymentRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/** Send out for delivery, then Mark as delivered with payment confirmation. */
@Service
public class OrderDeliveryService {

    private final CustomerOrderRepository orders;
    private final OrderEventRepository events;
    private final DeliveryPartnerRepository partners;
    private final PaymentRepository payments;
    private final CustomerService customers;
    private final AdminUserService admins;
    private final OrderMapper mapper;
    private final ApplicationEventPublisher publisher;
    private final Clock clock;

    public OrderDeliveryService(CustomerOrderRepository orders, OrderEventRepository events,
                                DeliveryPartnerRepository partners, PaymentRepository payments,
                                CustomerService customers, AdminUserService admins, OrderMapper mapper,
                                ApplicationEventPublisher publisher, Clock clock) {
        this.orders = orders;
        this.events = events;
        this.partners = partners;
        this.payments = payments;
        this.customers = customers;
        this.admins = admins;
        this.mapper = mapper;
        this.publisher = publisher;
        this.clock = clock;
    }

    /** "Send out for delivery": the partner must be Available; they become On delivery. */
    @Transactional
    public AdminOrderView dispatch(String orderNo, Long partnerId, Long adminId) {
        CustomerOrder o = lock(orderNo);
        OrderRules.requireAccepted(o);
        DeliveryPartner partner = partners.findByIdForUpdate(partnerId)
                .orElseThrow(() -> ApiException.badRequest("INVALID_PARTNER", "Choose a delivery partner from the list.", "partnerId"));
        if (!partner.canTakeOrder()) {
            String why = !partner.isActive() ? "is no longer active"
                    : partner.getStatus() == PartnerStatus.ON_DELIVERY ? "is on another delivery" : "is off duty";
            throw ApiException.conflict("PARTNER_NOT_AVAILABLE", partner.getName() + " " + why + ". Choose another partner.");
        }
        partner.setStatus(PartnerStatus.ON_DELIVERY);
        o.sendOutForDelivery(partner.getId(), Instant.now(clock));

        record(o, adminId, OrderEventType.ASSIGNED,
                "Assigned to " + partner.getName() + " (" + label(partner) + ", " + partner.getMobile() + ").");
        record(o, adminId, OrderEventType.OUT_FOR_DELIVERY, "Out for delivery with " + partner.getName() + ".");
        publisher.publishEvent(new OrderChangedEvent(o.getOrderNo(), o.getStatus(), OrderEventType.OUT_FOR_DELIVERY));
        return mapper.toAdminViews(List.of(o)).get(0);
    }

    /**
     * "Mark as delivered": records how the customer paid (amount = order total), who paid,
     * who collected it and which admin confirmed it. Frees the partner and counts the delivery
     * for the customer (so they are no longer a New customer).
     */
    @Transactional
    public AdminOrderView deliver(String orderNo, DeliverRequest request, Long adminId) {
        CustomerOrder o = lock(orderNo);
        OrderRules.requireOutForDelivery(o);
        DeliveryPartner collector = partners.findById(request.collectedByPartnerId())
                .orElseThrow(() -> ApiException.badRequest("INVALID_PARTNER", "Choose who collected the payment.", "collectedByPartnerId"));
        String upiRef = request.method() == PaymentMode.UPI ? OrderRules.blankToNull(request.upiRef()) : null;
        String paidBy = request.paidByName().trim();
        Instant now = Instant.now(clock);
        String confirmedBy = admins.get(adminId).name();

        payments.save(new Payment(o.getId(), request.method(), o.getTotal(), paidBy, collector.getId(), upiRef, adminId, now));
        o.markDelivered(now);
        if (o.getDeliveryPartnerId() != null) {
            partners.findByIdForUpdate(o.getDeliveryPartnerId())
                    .filter(p -> p.getStatus() == PartnerStatus.ON_DELIVERY)
                    .ifPresent(p -> p.setStatus(PartnerStatus.AVAILABLE));
        }
        customers.recordDelivery(o.getCustomerId());

        String how = request.method() == PaymentMode.UPI
                ? "by UPI" + (upiRef != null ? " (ref " + upiRef + ")" : "")
                : "in cash";
        record(o, adminId, OrderEventType.PAYMENT_CONFIRMED, "Paid " + Money.format(o.getTotal()) + " " + how
                + " by " + paidBy + " to " + collector.getName() + ". Confirmed by " + confirmedBy + ".");
        record(o, adminId, OrderEventType.DELIVERED, "Delivered.");
        publisher.publishEvent(new OrderChangedEvent(o.getOrderNo(), o.getStatus(), OrderEventType.DELIVERED));
        return mapper.toAdminViews(List.of(o)).get(0);
    }

    private CustomerOrder lock(String orderNo) {
        return orders.findByOrderNoForUpdate(orderNo).orElseThrow(() -> ApiException.notFound("Order not found."));
    }

    private void record(CustomerOrder o, Long adminId, OrderEventType type, String message) {
        events.save(new OrderEvent(o.getId(), ActorType.ADMIN, adminId, type, message));
    }

    private static String label(DeliveryPartner p) {
        String v = p.getVehicleType().name().toLowerCase();
        return Character.toUpperCase(v.charAt(0)) + v.substring(1);
    }
}
