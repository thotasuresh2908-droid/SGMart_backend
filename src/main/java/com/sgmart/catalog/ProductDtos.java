package com.sgmart.catalog;

import com.sgmart.inventory.StockStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

public final class ProductDtos {

    private ProductDtos() {
    }

    /** A product card in the shop. stockLeft drives "Only N left" when lowStock is true. */
    public record ShopProduct(Long id, Long categoryId, String name, String unit, BigDecimal price, BigDecimal mrp,
                              Integer discountPercent, int stockLeft, boolean lowStock, String icon, String imageUrl) {

        public static ShopProduct of(Product p) {
            return new ShopProduct(p.getId(), p.getCategory().getId(), p.getName(), p.getUnit(), p.getPrice(),
                    p.getMrp(), ProductDtos.discountPercent(p.getPrice(), p.getMrp()), p.getStockQty(),
                    p.getStockQty() <= StockStatus.LOW_STOCK_THRESHOLD, p.getIcon(), ProductDtos.imageUrl(p.getImageKey()));
        }
    }

    /** A row in Admin > Products. */
    public record AdminProduct(Long id, Long categoryId, String categoryName, String name, String unit,
                               BigDecimal price, BigDecimal mrp, Integer discountPercent, int stockQty,
                               StockStatus status, boolean active, String icon, String imageUrl, Instant updatedAt) {

        public static AdminProduct of(Product p) {
            return new AdminProduct(p.getId(), p.getCategory().getId(), p.getCategory().getName(), p.getName(),
                    p.getUnit(), p.getPrice(), p.getMrp(), ProductDtos.discountPercent(p.getPrice(), p.getMrp()),
                    p.getStockQty(), StockStatus.of(p.isActive(), p.getStockQty()), p.isActive(), p.getIcon(),
                    ProductDtos.imageUrl(p.getImageKey()), p.getUpdatedAt());
        }
    }

    public record CreateProductRequest(
            @NotBlank(message = "Enter the product name.")
            @Size(max = 120, message = "Keep the name under 120 characters.")
            String name,
            @NotNull(message = "Choose a category.")
            Long categoryId,
            @NotBlank(message = "Enter the pack size, like 500 g or 1 L.")
            @Size(max = 40, message = "Keep the pack size under 40 characters.")
            String unit,
            @NotNull(message = "Enter the selling price.")
            @DecimalMin(value = "0.01", message = "Enter a price above ₹0.")
            @Digits(integer = 8, fraction = 2, message = "Use at most 2 decimal places.")
            BigDecimal price,
            @DecimalMin(value = "0.01", message = "Enter an MRP above ₹0, or leave it empty.")
            @Digits(integer = 8, fraction = 2, message = "Use at most 2 decimal places.")
            BigDecimal mrp,
            @NotNull(message = "Enter how many are in stock.")
            @Min(value = 0, message = "Stock can't be negative.")
            @Max(value = 100000, message = "Stock looks too large. Please check it.")
            Integer stockQty,
            @Size(max = 16, message = "Choose one icon.")
            String icon) {
    }

    /** Edit product window: everything except stock (stock has its own Update stock window). */
    public record UpdateProductRequest(
            @NotBlank(message = "Enter the product name.")
            @Size(max = 120, message = "Keep the name under 120 characters.")
            String name,
            @NotNull(message = "Choose a category.")
            Long categoryId,
            @NotBlank(message = "Enter the pack size, like 500 g or 1 L.")
            @Size(max = 40, message = "Keep the pack size under 40 characters.")
            String unit,
            @NotNull(message = "Enter the selling price.")
            @DecimalMin(value = "0.01", message = "Enter a price above ₹0.")
            @Digits(integer = 8, fraction = 2, message = "Use at most 2 decimal places.")
            BigDecimal price,
            @DecimalMin(value = "0.01", message = "Enter an MRP above ₹0, or leave it empty.")
            @Digits(integer = 8, fraction = 2, message = "Use at most 2 decimal places.")
            BigDecimal mrp,
            @Size(max = 16, message = "Choose one icon.")
            String icon,
            @NotNull(message = "Choose Live in shop or Hidden.")
            Boolean active) {
    }

    /** "15% off" badge: whole percent, rounded like the prototype. Null when there is no discount. */
    public static Integer discountPercent(BigDecimal price, BigDecimal mrp) {
        if (mrp == null || price == null || mrp.compareTo(price) <= 0) {
            return null;
        }
        return mrp.subtract(price).multiply(BigDecimal.valueOf(100))
                .divide(mrp, 0, RoundingMode.HALF_UP).intValue();
    }

    public static String imageUrl(String imageKey) {
        return imageKey == null ? null : "/api/v1/images/" + imageKey;
    }
}
