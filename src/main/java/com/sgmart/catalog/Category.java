package com.sgmart.catalog;

import com.sgmart.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "categories")
public class Category extends BaseEntity {

    @Column(nullable = false, length = 80)
    private String name;

    @Column(nullable = false, length = 16)
    private String icon;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @Column(nullable = false)
    private boolean active = true;

    protected Category() {
    }

    public Category(String name, String icon, int sortOrder) {
        this.name = name;
        this.icon = icon;
        this.sortOrder = sortOrder;
    }

    public String getName() { return name; }
    public String getIcon() { return icon; }
    public Integer getSortOrder() { return sortOrder; }
    public boolean isActive() { return active; }
}
