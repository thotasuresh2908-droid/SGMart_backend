package com.sgmart.common;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class MoneyTest {

    @Test
    void wholeRupeesHaveNoDecimals() {
        assertThat(Money.format(new BigDecimal("126.00"))).isEqualTo("₹126");
        assertThat(Money.format(new BigDecimal("126.5"))).isEqualTo("₹126.50");
        assertThat(Money.format(new BigDecimal("1000"))).isEqualTo("₹1000");
    }
}
