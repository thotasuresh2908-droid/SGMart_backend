package com.sgmart.store;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;

/** Request and response shapes for the store endpoints. */
public final class StoreDtos {

    private StoreDtos() {
    }

    /** What every shopper sees: phone for "Call store", hours, ETA and the bill rules. */
    public record StoreInfo(
            String storeName,
            String phone,
            LocalTime opensAt,
            LocalTime closesAt,
            boolean openNow,
            int etaMinutes,
            BigDecimal deliveryFee,
            BigDecimal freeDeliveryMin,
            BigDecimal firstOrderCodLimit,
            Instant updatedAt) {
    }

    /** Owner edits these in Admin > Settings. firstOrderCodLimit may be null (= no cap). */
    public record UpdateStoreRequest(
            @NotBlank(message = "Enter the store name.")
            @Size(max = 80, message = "Keep the store name under 80 characters.")
            String storeName,

            @NotBlank(message = "Enter the store phone number.")
            String phone,

            @NotNull(message = "Enter the opening time.")
            LocalTime opensAt,

            @NotNull(message = "Enter the closing time.")
            LocalTime closesAt,

            @NotNull(message = "Enter the delivery time in minutes.")
            @Min(value = 1, message = "Delivery time must be at least 1 minute.")
            @Max(value = 240, message = "Delivery time can be at most 240 minutes.")
            Integer etaMinutes,

            @NotNull(message = "Enter the delivery fee (0 for free delivery).")
            @DecimalMin(value = "0.00", message = "Delivery fee can't be negative.")
            BigDecimal deliveryFee,

            @NotNull(message = "Enter the order amount for free delivery.")
            @DecimalMin(value = "0.00", message = "Free-delivery amount can't be negative.")
            BigDecimal freeDeliveryMin,

            @DecimalMin(value = "0.00", inclusive = false, message = "The first-order cap must be more than 0, or empty for no cap.")
            BigDecimal firstOrderCodLimit) {
    }
}
