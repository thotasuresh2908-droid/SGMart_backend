package com.sgmart.inventory;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public final class StockDtos {

    private StockDtos() {
    }

    /** "Add new stock" adds qty; "Set exact count" replaces the stock with qty. */
    public enum Mode { ADD, SET }

    public record StockUpdateRequest(
            @NotNull(message = "Choose Add new stock or Set exact count.")
            Mode mode,
            @NotNull(message = "Enter a quantity.")
            @Min(value = 0, message = "Quantity can't be negative.")
            @Max(value = 100000, message = "Quantity looks too large. Please check it.")
            Integer qty,
            /** Only for SET: SHELF_COUNT, DAMAGED or CORRECTION. Defaults to SHELF_COUNT. */
            StockReason reason) {
    }

    /** What the Update stock window shows after saving: "3 -> 13". */
    public record StockChangeResult(Long productId, String productName, int stockBefore, int stockAfter,
                                    boolean changed, StockStatus status) {
    }
}
