package com.sgmart.inventory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/** One row per stock change. Never updated or deleted: it is the stock history. */
@Entity
@Table(name = "stock_movements")
public class StockMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "change_qty", nullable = false)
    private Integer changeQty;

    @Column(name = "stock_after", nullable = false)
    private Integer stockAfter;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 30)
    private StockReason reason;

    @Column(name = "order_id")
    private Long orderId;

    @Column(length = 255)
    private String note;

    @Column(name = "created_by_admin_id")
    private Long createdByAdminId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected StockMovement() {
    }

    public StockMovement(Long productId, int changeQty, int stockAfter, StockReason reason,
                         Long orderId, String note, Long createdByAdminId) {
        this.productId = productId;
        this.changeQty = changeQty;
        this.stockAfter = stockAfter;
        this.reason = reason;
        this.orderId = orderId;
        this.note = note;
        this.createdByAdminId = createdByAdminId;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getProductId() { return productId; }
    public Integer getChangeQty() { return changeQty; }
    public Integer getStockAfter() { return stockAfter; }
    public StockReason getReason() { return reason; }
    public Long getOrderId() { return orderId; }
    public String getNote() { return note; }
    public Long getCreatedByAdminId() { return createdByAdminId; }
    public Instant getCreatedAt() { return createdAt; }
}
