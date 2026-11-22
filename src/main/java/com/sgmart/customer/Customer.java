package com.sgmart.customer;

import com.sgmart.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * One shopper. Guests are created on their first order (mobile only).
 * Google shoppers are created on first sign-in (google_sub, mobile added later).
 */
@Entity
@Table(name = "customers")
public class Customer extends BaseEntity {

    @Column(nullable = false, length = 100)
    private String name;

    @Column(columnDefinition = "char(10)")
    private String mobile;

    @Column(length = 254)
    private String email;

    @Column(name = "google_sub", length = 255)
    private String googleSub;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "verified_via", nullable = false, length = 20)
    private VerifiedVia verifiedVia = VerifiedVia.NONE;

    @Column(name = "verified_by_admin_id")
    private Long verifiedByAdminId;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "delivered_orders_count", nullable = false, columnDefinition = "int unsigned")
    private Integer deliveredOrdersCount = 0;

    @Column(nullable = false)
    private boolean blocked;

    @Column(name = "blocked_reason", length = 255)
    private String blockedReason;

    @Column(name = "blocked_at")
    private Instant blockedAt;

    protected Customer() {
    }

    public static Customer guest(String name, String mobile) {
        Customer c = new Customer();
        c.name = name;
        c.mobile = mobile;
        return c;
    }

    public static Customer fromGoogle(String googleSub, String email, String name) {
        Customer c = new Customer();
        c.googleSub = googleSub;
        c.email = email;
        c.name = name;
        return c;
    }

    /** A number with no delivered order and no store call is a "New customer". */
    public boolean isNewCustomer() {
        return verifiedVia != VerifiedVia.STORE_CALL && deliveredOrdersCount == 0;
    }

    public void markVerifiedByStore(Long adminId, Instant when) {
        this.verifiedVia = VerifiedVia.STORE_CALL;
        this.verifiedByAdminId = adminId;
        this.verifiedAt = when;
    }

    public void clearVerification() {
        this.verifiedVia = VerifiedVia.NONE;
        this.verifiedByAdminId = null;
        this.verifiedAt = null;
    }

    /** Takes over verification, delivered count and any block from a guest record being merged in. */
    void absorb(Customer guest) {
        this.deliveredOrdersCount = this.deliveredOrdersCount + guest.deliveredOrdersCount;
        if (guest.verifiedVia == VerifiedVia.STORE_CALL) {
            markVerifiedByStore(guest.verifiedByAdminId, guest.verifiedAt);
        } else {
            clearVerification();
        }
        if (guest.blocked) {
            this.blocked = true;
            this.blockedReason = guest.blockedReason;
            this.blockedAt = guest.blockedAt;
        }
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getMobile() { return mobile; }
    public void setMobile(String mobile) { this.mobile = mobile; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getGoogleSub() { return googleSub; }
    public VerifiedVia getVerifiedVia() { return verifiedVia; }
    public Long getVerifiedByAdminId() { return verifiedByAdminId; }
    public Instant getVerifiedAt() { return verifiedAt; }
    public Integer getDeliveredOrdersCount() { return deliveredOrdersCount; }
    public void setDeliveredOrdersCount(Integer deliveredOrdersCount) { this.deliveredOrdersCount = deliveredOrdersCount; }
    public boolean isBlocked() { return blocked; }
    public String getBlockedReason() { return blockedReason; }
    public Instant getBlockedAt() { return blockedAt; }

    public void block(String reason, Instant when) {
        this.blocked = true;
        this.blockedReason = reason;
        this.blockedAt = when;
    }

    public void unblock() {
        this.blocked = false;
        this.blockedReason = null;
        this.blockedAt = null;
    }
}
