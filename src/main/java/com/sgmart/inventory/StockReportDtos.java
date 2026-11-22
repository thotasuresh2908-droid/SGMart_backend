package com.sgmart.inventory;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class StockReportDtos {

    private StockReportDtos() {
    }

    /** The five numbers at the top of Today's stock. */
    public record Summary(int productsInShop, int liveProducts, int unitsAvailable, int unitsAdded,
                          int productsRestocked, int unitsDelivered, int ordersDelivered,
                          int outOfStock, int lowStock) {
    }

    /** One row of "Stock by product". */
    public record ProductRow(Long productId, String name, String unit, String icon, String imageUrl,
                             int startOfDay, int added, int removed, int ordered, int delivered,
                             int available, StockStatus status) {
    }

    /** One row of "Stock added today". change is negative for damaged or downward counts. */
    public record LogRow(Instant at, Long productId, String name, String unit, String icon, String imageUrl,
                         int change, int stockAfter, StockReason reason, String note, String addedBy) {
    }

    /** One row of "Delivered today". minutesTaken = from order placed to delivered. */
    public record DeliveryRow(Instant deliveredAt, String orderNo, String customerName, String customerMobile,
                              int units, String items, Long partnerId, String partnerName, String partnerMobile,
                              long minutesTaken, BigDecimal amount) {
    }

    /** A partner card in "Delivered today". */
    public record PartnerDayRow(Long partnerId, String name, String mobile, String vehicleType,
                                int deliveries, int units, BigDecimal amount) {
    }

    public record StockReport(LocalDate date, Summary summary, List<ProductRow> products, List<LogRow> restockLog,
                              List<PartnerDayRow> partners, List<DeliveryRow> deliveries) {
    }
}
