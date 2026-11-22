package com.sgmart.inventory;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StockStatusTest {

    @Test
    void badgesMatchThePrototype() {
        assertThat(StockStatus.of(true, 0)).isEqualTo(StockStatus.OUT_OF_STOCK);
        assertThat(StockStatus.of(true, 5)).isEqualTo(StockStatus.LOW_STOCK);
        assertThat(StockStatus.of(true, 6)).isEqualTo(StockStatus.IN_SHOP);
        assertThat(StockStatus.of(false, 50)).isEqualTo(StockStatus.HIDDEN);
    }

    @Test
    void problemsSortFirst() {
        assertThat(StockStatus.OUT_OF_STOCK.sortRank()).isLessThan(StockStatus.LOW_STOCK.sortRank());
        assertThat(StockStatus.LOW_STOCK.sortRank()).isLessThan(StockStatus.IN_SHOP.sortRank());
        assertThat(StockStatus.IN_SHOP.sortRank()).isLessThan(StockStatus.HIDDEN.sortRank());
    }
}
