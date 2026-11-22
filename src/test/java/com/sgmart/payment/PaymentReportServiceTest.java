package com.sgmart.payment;

import com.sgmart.order.OrderStatus;
import com.sgmart.order.PaymentMethod;
import com.sgmart.payment.PaymentReportDtos.DueOrder;
import com.sgmart.payment.PaymentReportDtos.Totals;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentReportServiceTest {

    private DueOrder due(String orderNo, String amount, OrderStatus status) {
        return new DueOrder(orderNo, "Kiran", "9000000003", status, PaymentMethod.CASH_ON_DELIVERY,
                new BigDecimal(amount), null, null, null);
    }

    @Test
    void totalsMatchThePrototypeTiles() {
        Totals t = PaymentReportService.totals(3, new BigDecimal("543.00"), new BigDecimal("235.00"),
                new BigDecimal("308.00"), List.of(due("SG1004", "200.00", OrderStatus.OUT_FOR_DELIVERY),
                        due("SG1005", "75.00", OrderStatus.ACCEPTED)));
        assertThat(t.collected()).isEqualByComparingTo("543.00");
        assertThat(t.averageBill()).isEqualByComparingTo("181");           // 543 / 3
        assertThat(t.stillToCollect()).isEqualByComparingTo("275.00");
        assertThat(t.activeOrders()).isEqualTo(2);
    }

    @Test
    void noPaymentsMeansNoAverage() {
        Totals t = PaymentReportService.totals(0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, List.of());
        assertThat(t.averageBill()).isNull();
        assertThat(t.stillToCollect()).isEqualByComparingTo("0");
    }
}
