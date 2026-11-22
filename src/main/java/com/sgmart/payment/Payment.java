package com.sgmart.payment;

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

import java.math.BigDecimal;
import java.time.Instant;

/** One payment per delivered order: how, how much, who paid, who collected, who confirmed. */
@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 10)
    private PaymentMode method;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(name = "paid_by_name", nullable = false, length = 100)
    private String paidByName;

    @Column(name = "collected_by_partner_id", nullable = false)
    private Long collectedByPartnerId;

    @Column(name = "upi_ref", length = 64)
    private String upiRef;

    @Column(name = "confirmed_by_admin_id", nullable = false)
    private Long confirmedByAdminId;

    @Column(name = "confirmed_at", nullable = false)
    private Instant confirmedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Payment() {
    }

    public Payment(Long orderId, PaymentMode method, BigDecimal amount, String paidByName,
                   Long collectedByPartnerId, String upiRef, Long confirmedByAdminId, Instant confirmedAt) {
        this.orderId = orderId;
        this.method = method;
        this.amount = amount;
        this.paidByName = paidByName;
        this.collectedByPartnerId = collectedByPartnerId;
        this.upiRef = method == PaymentMode.UPI ? upiRef : null;   // the database allows a reference only for UPI
        this.confirmedByAdminId = confirmedByAdminId;
        this.confirmedAt = confirmedAt;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getOrderId() { return orderId; }
    public PaymentMode getMethod() { return method; }
    public BigDecimal getAmount() { return amount; }
    public String getPaidByName() { return paidByName; }
    public Long getCollectedByPartnerId() { return collectedByPartnerId; }
    public String getUpiRef() { return upiRef; }
    public Long getConfirmedByAdminId() { return confirmedByAdminId; }
    public Instant getConfirmedAt() { return confirmedAt; }
}
