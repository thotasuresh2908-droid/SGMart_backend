package com.sgmart.order;

import com.sgmart.common.BaseEntity;
import com.sgmart.common.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;

/** One line of an order. Name, pack size and price are copied in, so later product edits never change it. */
@Entity
@Table(name = "order_items")
public class OrderItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private CustomerOrder order;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "product_name", nullable = false, length = 120)
    private String productName;

    @Column(nullable = false, length = 40)
    private String unit;

    @Column(nullable = false, length = 16)
    private String icon;

    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;

    @Column(nullable = false)
    private Integer qty;

    @Column(name = "line_total", nullable = false, precision = 10, scale = 2)
    private BigDecimal lineTotal;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 30)
    private OrderItemStatus status = OrderItemStatus.ACTIVE;

    @Column(name = "unavailable_reason", length = 100)
    private String unavailableReason;

    protected OrderItem() {
    }

    public OrderItem(CustomerOrder order, Long productId, String productName, String unit, String icon,
                     BigDecimal unitPrice, int qty) {
        this.order = order;
        this.productId = productId;
        this.productName = productName;
        this.unit = unit;
        this.icon = icon;
        this.unitPrice = Money.of(unitPrice);
        setQty(qty);
    }

    public boolean isActive() {
        return status == OrderItemStatus.ACTIVE;
    }

    /** Line total always follows quantity (the database checks line_total = unit_price × qty). */
    public void setQty(int qty) {
        this.qty = qty;
        this.lineTotal = Money.of(unitPrice.multiply(BigDecimal.valueOf(qty)));
    }

    public void markRemovedByCustomer() {
        this.status = OrderItemStatus.REMOVED_BY_CUSTOMER;
    }

    /** A product removed earlier and added back in an edit: it gets today's details and price. */
    public void reactivate(String productName, String unit, String icon, BigDecimal currentPrice, int qty) {
        this.status = OrderItemStatus.ACTIVE;
        this.unavailableReason = null;
        this.productName = productName;
        this.unit = unit;
        this.icon = icon;
        this.unitPrice = Money.of(currentPrice);
        setQty(qty);
    }

    public void markNotAvailable(String reason) {
        this.status = OrderItemStatus.NOT_AVAILABLE;
        this.unavailableReason = reason;
    }

    public CustomerOrder getOrder() { return order; }
    public Long getProductId() { return productId; }
    public String getProductName() { return productName; }
    public String getUnit() { return unit; }
    public String getIcon() { return icon; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public Integer getQty() { return qty; }
    public BigDecimal getLineTotal() { return lineTotal; }
    public OrderItemStatus getStatus() { return status; }
    public String getUnavailableReason() { return unavailableReason; }
}
