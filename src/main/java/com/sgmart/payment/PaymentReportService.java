package com.sgmart.payment;

import com.sgmart.common.Money;
import com.sgmart.common.StoreTime;
import com.sgmart.delivery.PartnerStatus;
import com.sgmart.delivery.VehicleType;
import com.sgmart.order.OrderStatus;
import com.sgmart.order.PaymentMethod;
import com.sgmart.payment.PaymentReportDtos.DueOrder;
import com.sgmart.payment.PaymentReportDtos.PartnerTotals;
import com.sgmart.payment.PaymentReportDtos.PaymentRecord;
import com.sgmart.payment.PaymentReportDtos.PaymentReport;
import com.sgmart.payment.PaymentReportDtos.Totals;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Admin > Payments: totals, per delivery partner, still to collect, payment records. */
@Service
public class PaymentReportService {

    private static final Instant ALL_TIME_FROM = Instant.parse("2000-01-01T00:00:00Z");
    private static final Instant ALL_TIME_TO = Instant.parse("2100-01-01T00:00:00Z");

    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    public PaymentReportService(NamedParameterJdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** One day (India time; today if date is null), or everything when allTime is true. */
    @Transactional(readOnly = true)
    public PaymentReport report(LocalDate date, boolean allTime) {
        LocalDate day = allTime ? null : (date != null ? date : LocalDate.now(clock.withZone(StoreTime.ZONE)));
        Instant from = day == null ? ALL_TIME_FROM : day.atStartOfDay(StoreTime.ZONE).toInstant();
        Instant to = day == null ? ALL_TIME_TO : day.plusDays(1).atStartOfDay(StoreTime.ZONE).toInstant();
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("fromTime", Timestamp.from(from))
                .addValue("toTime", Timestamp.from(to));

        List<DueOrder> due = jdbc.query(PaymentReportSql.STILL_TO_COLLECT, params, (rs, i) -> new DueOrder(
                rs.getString("order_no"), rs.getString("customer_name"), rs.getString("customer_mobile"),
                OrderStatus.valueOf(rs.getString("status")), PaymentMethod.valueOf(rs.getString("payment_method")),
                rs.getBigDecimal("total"), (Long) rs.getObject("delivery_partner_id", Long.class),
                rs.getString("partner_name"), rs.getString("partner_mobile")));
        Map<Long, List<DueOrder>> onTheWay = due.stream()
                .filter(d -> d.status() == OrderStatus.OUT_FOR_DELIVERY && d.partnerId() != null)
                .collect(Collectors.groupingBy(DueOrder::partnerId));

        List<PartnerTotals> byPartner = jdbc.query(PaymentReportSql.BY_PARTNER, params, (rs, i) -> new PartnerTotals(
                rs.getLong("id"), rs.getString("name"), rs.getString("mobile"),
                VehicleType.valueOf(rs.getString("vehicle_type")), PartnerStatus.valueOf(rs.getString("status")),
                rs.getInt("deliveries"), rs.getBigDecimal("cash"), rs.getBigDecimal("upi"),
                onTheWay.getOrDefault(rs.getLong("id"), List.of())));

        List<PaymentRecord> records = jdbc.query(PaymentReportSql.RECORDS, params, (rs, i) -> new PaymentRecord(
                rs.getTimestamp("confirmed_at").toInstant(), rs.getString("order_no"), rs.getString("paid_by_name"),
                PaymentMode.valueOf(rs.getString("method")), rs.getString("upi_ref"), rs.getBigDecimal("amount"),
                rs.getString("collected_by"), rs.getString("confirmed_by")));

        Totals totals = jdbc.queryForObject(PaymentReportSql.TOTALS, params, (rs, i) -> totals(
                rs.getInt("payments"), rs.getBigDecimal("collected"), rs.getBigDecimal("cash"),
                rs.getBigDecimal("upi"), due));
        return new PaymentReport(day, totals, byPartner, due, records);
    }

    static Totals totals(int count, BigDecimal collected, BigDecimal cash, BigDecimal upi, List<DueOrder> due) {
        BigDecimal average = count == 0 ? null : collected.divide(BigDecimal.valueOf(count), 0, RoundingMode.HALF_UP);
        BigDecimal dueTotal = due.stream().map(DueOrder::amountDue).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Totals(count, Money.of(collected), Money.of(cash), Money.of(upi),
                average == null ? null : Money.of(average), Money.of(dueTotal), due.size());
    }
}
