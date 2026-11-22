package com.sgmart.common;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Rupee amounts in messages: ₹126, ₹126.50. */
public final class Money {

    private Money() {
    }

    public static BigDecimal of(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    public static String format(BigDecimal amount) {
        BigDecimal a = of(amount);
        return "₹" + (a.remainder(BigDecimal.ONE).signum() == 0 ? a.toBigInteger().toString() : a.toPlainString());
    }
}
