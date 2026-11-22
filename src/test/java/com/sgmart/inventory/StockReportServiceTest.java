package com.sgmart.inventory;

import com.sgmart.inventory.StockReportDtos.ProductRow;
import com.sgmart.inventory.StockReportDtos.Summary;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StockReportServiceTest {

    private ProductRow row(String name, int available, int added, int delivered, StockStatus status) {
        return new ProductRow(1L, name, "1 kg", "🛒", null, available, added, 0, 0, delivered, available, status);
    }

    @Test
    void summaryCountsLikeThePrototype() {
        Summary s = StockReportService.summarize(List.of(
                row("Milk", 115, 10, 2, StockStatus.IN_SHOP),
                row("Green Tea", 1, 0, 0, StockStatus.LOW_STOCK),
                row("Curd", 20, 20, 0, StockStatus.IN_SHOP),
                row("Paneer", 0, 0, 0, StockStatus.OUT_OF_STOCK),
                row("Cola", 48, 0, 0, StockStatus.HIDDEN)), 1);

        assertThat(s.liveProducts()).isEqualTo(4);          // hidden Cola not counted
        assertThat(s.productsInShop()).isEqualTo(3);        // Paneer has 0 stock
        assertThat(s.unitsAvailable()).isEqualTo(136);      // 115 + 1 + 20 + 0
        assertThat(s.unitsAdded()).isEqualTo(30);
        assertThat(s.productsRestocked()).isEqualTo(2);
        assertThat(s.unitsDelivered()).isEqualTo(2);
        assertThat(s.ordersDelivered()).isEqualTo(1);
        assertThat(s.outOfStock()).isEqualTo(1);
        assertThat(s.lowStock()).isEqualTo(1);
    }
}
