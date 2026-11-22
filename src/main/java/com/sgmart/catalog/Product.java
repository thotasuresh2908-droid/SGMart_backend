package com.sgmart.catalog;

import com.sgmart.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A product in the store.
 * Shown in the shop only when active, not removed, and stock is above 0.
 */
@Entity
@Table(name = "products")
public class Product extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 40)
    private String unit;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal mrp;

    @Column(name = "stock_qty", nullable = false)
    private Integer stockQty = 0;

    @Column(name = "image_key", length = 255)
    private String imageKey;

    @Column(nullable = false, length = 16)
    private String icon = "🛒";

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Version
    @Column(nullable = false)
    private Integer version;

    protected Product() {
    }

    public static Product create(Category category, String name, String unit, BigDecimal price,
                                 BigDecimal mrp, int stockQty, String icon) {
        Product p = new Product();
        p.category = category;
        p.name = name;
        p.unit = unit;
        p.price = price;
        p.mrp = mrp;
        p.stockQty = stockQty;
        if (icon != null && !icon.isBlank()) {
            p.icon = icon;
        }
        return p;
    }

    public boolean isRemoved() {
        return deletedAt != null;
    }

    /** Can a shopper see and order it right now? */
    public boolean isSellable() {
        return active && deletedAt == null && stockQty > 0;
    }

    public void remove(Instant when) {
        this.deletedAt = when;
        this.active = false;
    }

    public Category getCategory() { return category; }
    public void setCategory(Category category) { this.category = category; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public BigDecimal getMrp() { return mrp; }
    public void setMrp(BigDecimal mrp) { this.mrp = mrp; }
    public Integer getStockQty() { return stockQty; }
    public void setStockQty(Integer stockQty) { this.stockQty = stockQty; }
    public String getImageKey() { return imageKey; }
    public void setImageKey(String imageKey) { this.imageKey = imageKey; }
    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public Instant getDeletedAt() { return deletedAt; }
    public Integer getVersion() { return version; }
}
