package com.sgmart.payment;

import com.sgmart.delivery.PartnerStatus;
import com.sgmart.delivery.VehicleType;
import com.sgmart.order.OrderStatus;
import com.sgmart.order.PaymentMethod;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class PaymentReportDtos {

    private PaymentReportDtos() {
    }

    /** date is null for "All time". */
    public record PaymentReport(LocalDate date, Totals totals, List<PartnerTotals> byPartner,
                                List<DueOrder> stillToCollect, List<PaymentRecord> records) {
    }

    public record Totals(int payments, BigDecimal collected, BigDecimal cash, BigDecimal upi, BigDecimal averageBill,
                         BigDecimal stillToCollect, int activeOrders) {
    }

    /** cashToHandOver = cash this partner collected in the period. onTheWay = orders they are delivering now. */
    public record PartnerTotals(Long partnerId, String name, String mobile, VehicleType vehicleType,
                                PartnerStatus status, int deliveries, BigDecimal cashToHandOver, BigDecimal upi,
                                List<DueOrder> onTheWay) {
    }

    public record DueOrder(String orderNo, String customerName, String customerMobile, OrderStatus status,
                           PaymentMethod paymentMethod, BigDecimal amountDue, Long partnerId, String partnerName,
                           String partnerMobile) {
    }

    public record PaymentRecord(Instant at, String orderNo, String paidBy, PaymentMode method, String upiRef,
                                BigDecimal amount, String collectedBy, String confirmedBy) {
    }
}
