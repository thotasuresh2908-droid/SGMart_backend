package com.sgmart.store;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;

/** The single row (id = 1) of store_settings. */
@Entity
@Table(name = "store_settings")
public class StoreSettings {

    public static final short SINGLE_ROW_ID = 1;

    @Id
    @Column(columnDefinition = "tinyint unsigned")
    private Short id;

    @Column(name = "store_name", nullable = false, length = 80)
    private String storeName;

    @Column(nullable = false, columnDefinition = "char(10)")
    private String phone;

    @Column(name = "opens_at", nullable = false)
    private LocalTime opensAt;

    @Column(name = "closes_at", nullable = false)
    private LocalTime closesAt;

    @Column(name = "eta_minutes", nullable = false, columnDefinition = "smallint unsigned")
    private Integer etaMinutes;

    @Column(name = "delivery_fee", nullable = false, precision = 10, scale = 2)
    private BigDecimal deliveryFee;

    @Column(name = "free_delivery_min", nullable = false, precision = 10, scale = 2)
    private BigDecimal freeDeliveryMin;

    /** Null means no cap on a new customer's first Cash-on-Delivery order. */
    @Column(name = "first_order_cod_limit", precision = 10, scale = 2)
    private BigDecimal firstOrderCodLimit;

    @Column(name = "updated_by_admin_id")
    private Long updatedByAdminId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected StoreSettings() {
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    /** For tests and manual setup; the real row comes from migration V2. */
    public static StoreSettings defaults(String phone) {
        StoreSettings s = new StoreSettings();
        s.id = SINGLE_ROW_ID;
        s.storeName = "SGMart";
        s.phone = phone;
        s.opensAt = LocalTime.of(7, 0);
        s.closesAt = LocalTime.of(23, 0);
        s.etaMinutes = 10;
        s.deliveryFee = new BigDecimal("25.00");
        s.freeDeliveryMin = new BigDecimal("199.00");
        s.firstOrderCodLimit = new BigDecimal("1000.00");
        return s;
    }

    public Short getId() { return id; }
    public String getStoreName() { return storeName; }
    public void setStoreName(String storeName) { this.storeName = storeName; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public LocalTime getOpensAt() { return opensAt; }
    public void setOpensAt(LocalTime opensAt) { this.opensAt = opensAt; }
    public LocalTime getClosesAt() { return closesAt; }
    public void setClosesAt(LocalTime closesAt) { this.closesAt = closesAt; }
    public Integer getEtaMinutes() { return etaMinutes; }
    public void setEtaMinutes(Integer etaMinutes) { this.etaMinutes = etaMinutes; }
    public BigDecimal getDeliveryFee() { return deliveryFee; }
    public void setDeliveryFee(BigDecimal deliveryFee) { this.deliveryFee = deliveryFee; }
    public BigDecimal getFreeDeliveryMin() { return freeDeliveryMin; }
    public void setFreeDeliveryMin(BigDecimal freeDeliveryMin) { this.freeDeliveryMin = freeDeliveryMin; }
    public BigDecimal getFirstOrderCodLimit() { return firstOrderCodLimit; }
    public void setFirstOrderCodLimit(BigDecimal firstOrderCodLimit) { this.firstOrderCodLimit = firstOrderCodLimit; }
    public Long getUpdatedByAdminId() { return updatedByAdminId; }
    public void setUpdatedByAdminId(Long updatedByAdminId) { this.updatedByAdminId = updatedByAdminId; }
    public Instant getUpdatedAt() { return updatedAt; }
}
