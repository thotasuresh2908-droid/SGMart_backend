package com.sgmart.order;

import com.sgmart.common.BaseEntity;
import com.sgmart.common.Money;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * An order (table "orders"). Named CustomerOrder because ORDER is a reserved word in JPQL.
 * Name, mobile and address are copied in at checkout, so profile changes never alter past orders.
 */
@Entity
@Table(name = "orders")
public class CustomerOrder extends BaseEntity {

    @Column(name = "order_no", length = 16)
    private String orderNo;

    @Column(name = "tracking_token_hash", nullable = false, columnDefinition = "char(64)")
    private String trackingTokenHash;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "customer_name", nullable = false, length = 100)
    private String customerName;

    @Column(name = "customer_mobile", nullable = false, columnDefinition = "char(10)")
    private String customerMobile;

    @Column(name = "address_line", nullable = false, length = 500)
    private String addressLine;

    @Column(length = 200)
    private String landmark;

    @Column(precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(precision = 9, scale = 6)
    private BigDecimal longitude;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private OrderStatus status = OrderStatus.PLACED;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "payment_method", nullable = false, length = 20)
    private PaymentMethod paymentMethod;

    @Column(name = "item_total", nullable = false, precision = 10, scale = 2)
    private BigDecimal itemTotal = Money.of(BigDecimal.ZERO);

    @Column(name = "delivery_fee", nullable = false, precision = 10, scale = 2)
    private BigDecimal deliveryFee = Money.of(BigDecimal.ZERO);

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal total = Money.of(BigDecimal.ZERO);

    @Column(name = "delivery_partner_id")
    private Long deliveryPartnerId;

    @Column(name = "reject_reason", length = 255)
    private String rejectReason;

    @Column(name = "cancel_reason", length = 255)
    private String cancelReason;

    @Column(name = "placed_at", nullable = false, updatable = false)
    private Instant placedAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "out_for_delivery_at")
    private Instant outForDeliveryAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "rejected_at")
    private Instant rejectedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Version
    @Column(nullable = false)
    private Integer version;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL)
    @OrderBy("id")
    private List<OrderItem> items = new ArrayList<>();

    protected CustomerOrder() {
    }

    public static CustomerOrder place(String trackingTokenHash, Long customerId, String customerName,
                                      String customerMobile, String addressLine, String landmark,
                                      BigDecimal latitude, BigDecimal longitude,
                                      PaymentMethod paymentMethod, Instant placedAt) {
        CustomerOrder o = new CustomerOrder();
        o.trackingTokenHash = trackingTokenHash;
        o.customerId = customerId;
        o.customerName = customerName;
        o.customerMobile = customerMobile;
        o.addressLine = addressLine;
        o.landmark = landmark;
        o.latitude = latitude;
        o.longitude = longitude;
        o.paymentMethod = paymentMethod;
        o.placedAt = placedAt;
        return o;
    }

    /** SG1001, SG1002, ... Set right after the first insert, in the same transaction. */
    public void assignOrderNo() {
        this.orderNo = "SG" + (1000 + getId());
    }

    public void addItem(OrderItem item) {
        items.add(item);
    }

    public List<OrderItem> activeItems() {
        return items.stream().filter(OrderItem::isActive).toList();
    }

    public Optional<OrderItem> itemForProduct(Long productId) {
        return items.stream().filter(i -> i.getProductId().equals(productId)).findFirst();
    }

    /** Item total from ACTIVE lines only; delivery fee from the store rule. Total = items + fee. */
    public void recalculateTotals(UnaryOperator<BigDecimal> deliveryFeeFor) {
        this.itemTotal = Money.of(activeItems().stream().map(OrderItem::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        this.deliveryFee = Money.of(deliveryFeeFor.apply(itemTotal));
        this.total = Money.of(itemTotal.add(deliveryFee));
    }

    public void accept(Instant when) {
        this.status = OrderStatus.ACCEPTED;
        this.acceptedAt = when;
    }

    public void reject(String reason, Instant when) {
        this.status = OrderStatus.REJECTED;
        this.rejectReason = reason;
        this.rejectedAt = when;
    }

    public void cancel(String reason, Instant when) {
        this.status = OrderStatus.CANCELLED;
        this.cancelReason = reason;
        this.cancelledAt = when;
    }

    public void sendOutForDelivery(Long partnerId, Instant when) {
        this.status = OrderStatus.OUT_FOR_DELIVERY;
        this.deliveryPartnerId = partnerId;
        this.outForDeliveryAt = when;
    }

    public void markDelivered(Instant when) {
        this.status = OrderStatus.DELIVERED;
        this.deliveredAt = when;
    }

    public void moveToCustomer(Long customerId) {
        this.customerId = customerId;
    }

    public String getOrderNo() { return orderNo; }
    public String getTrackingTokenHash() { return trackingTokenHash; }
    public Long getCustomerId() { return customerId; }
    public String getCustomerName() { return customerName; }
    public String getCustomerMobile() { return customerMobile; }
    public String getAddressLine() { return addressLine; }
    public String getLandmark() { return landmark; }
    public BigDecimal getLatitude() { return latitude; }
    public BigDecimal getLongitude() { return longitude; }
    public OrderStatus getStatus() { return status; }
    public PaymentMethod getPaymentMethod() { return paymentMethod; }
    public BigDecimal getItemTotal() { return itemTotal; }
    public BigDecimal getDeliveryFee() { return deliveryFee; }
    public BigDecimal getTotal() { return total; }
    public Long getDeliveryPartnerId() { return deliveryPartnerId; }
    public String getRejectReason() { return rejectReason; }
    public String getCancelReason() { return cancelReason; }
    public Instant getPlacedAt() { return placedAt; }
    public Instant getAcceptedAt() { return acceptedAt; }
    public Instant getOutForDeliveryAt() { return outForDeliveryAt; }
    public Instant getDeliveredAt() { return deliveredAt; }
    public Instant getRejectedAt() { return rejectedAt; }
    public Instant getCancelledAt() { return cancelledAt; }
    public Integer getVersion() { return version; }
    public List<OrderItem> getItems() { return items; }
}
