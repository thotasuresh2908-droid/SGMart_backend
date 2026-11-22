package com.sgmart.inventory;

import com.sgmart.catalog.ProductDtos;
import com.sgmart.common.StoreTime;
import com.sgmart.inventory.StockReportDtos.DeliveryRow;
import com.sgmart.inventory.StockReportDtos.LogRow;
import com.sgmart.inventory.StockReportDtos.PartnerDayRow;
import com.sgmart.inventory.StockReportDtos.ProductRow;
import com.sgmart.inventory.StockReportDtos.StockReport;
import com.sgmart.inventory.StockReportDtos.Summary;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Builds Admin > Today's stock from stock_movements and delivered orders. Works for any past day too. */
@Service
public class StockReportService {

    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    public StockReportService(NamedParameterJdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public StockReport report(LocalDate date) {
        LocalDate day = date != null ? date : LocalDate.now(clock.withZone(StoreTime.ZONE));
        Instant dayStart = day.atStartOfDay(StoreTime.ZONE).toInstant();
        Instant dayEnd = day.plusDays(1).atStartOfDay(StoreTime.ZONE).toInstant();
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("dayStart", Timestamp.from(dayStart))
                .addValue("dayEnd", Timestamp.from(dayEnd));

        Map<Long, Integer> delivered = new HashMap<>();
        jdbc.query(StockReportSql.DELIVERED_UNITS, params,
                rs -> { delivered.put(rs.getLong("product_id"), rs.getInt("units")); });

        List<ProductRow> rows = jdbc.query(StockReportSql.PRODUCT_ROWS, params, (rs, i) -> {
            long id = rs.getLong("id");
            int stockNow = rs.getInt("stock_qty");
            int available = stockNow - rs.getInt("change_since_end");
            return new ProductRow(id, rs.getString("name"), rs.getString("unit"), rs.getString("icon"),
                    ProductDtos.imageUrl(rs.getString("image_key")),
                    stockNow - rs.getInt("change_since_start"),
                    rs.getInt("added"), rs.getInt("removed"), rs.getInt("ordered"),
                    delivered.getOrDefault(id, 0), available,
                    StockStatus.of(rs.getBoolean("active"), available));
        });
        rows = rows.stream()
                .sorted(Comparator.comparingInt((ProductRow r) -> r.status().sortRank())
                        .thenComparing(ProductRow::name, String.CASE_INSENSITIVE_ORDER))
                .toList();

        List<LogRow> log = jdbc.query(StockReportSql.RESTOCK_LOG, params, (rs, i) -> new LogRow(
                rs.getTimestamp("created_at").toInstant(), rs.getLong("product_id"), rs.getString("name"),
                rs.getString("unit"), rs.getString("icon"), ProductDtos.imageUrl(rs.getString("image_key")),
                rs.getInt("change_qty"), rs.getInt("stock_after"), StockReason.valueOf(rs.getString("reason")),
                rs.getString("note"), rs.getString("admin_name") != null ? rs.getString("admin_name") : "System"));

        Integer ordersDelivered = jdbc.queryForObject(StockReportSql.DELIVERED_ORDERS, params, Integer.class);

        List<DeliveryRow> deliveries = jdbc.query(StockReportSql.DELIVERIES, params, (rs, i) -> {
            Instant deliveredAt = rs.getTimestamp("delivered_at").toInstant();
            Instant placedAt = rs.getTimestamp("placed_at").toInstant();
            return new DeliveryRow(deliveredAt, rs.getString("order_no"), rs.getString("customer_name"),
                    rs.getString("customer_mobile"), rs.getInt("units"), rs.getString("item_names"),
                    (Long) rs.getObject("partner_id", Long.class), rs.getString("partner_name"),
                    rs.getString("partner_mobile"), Math.max(1, Duration.between(placedAt, deliveredAt).toMinutes()),
                    rs.getBigDecimal("total"));
        });
        List<PartnerDayRow> partners = jdbc.query(StockReportSql.ACTIVE_PARTNERS, params, (rs, i) -> {
            long id = rs.getLong("id");
            List<DeliveryRow> mine = deliveries.stream().filter(d -> Long.valueOf(id).equals(d.partnerId())).toList();
            return new PartnerDayRow(id, rs.getString("name"), rs.getString("mobile"), rs.getString("vehicle_type"),
                    mine.size(), mine.stream().mapToInt(DeliveryRow::units).sum(),
                    mine.stream().map(DeliveryRow::amount).reduce(BigDecimal.ZERO, BigDecimal::add));
        });

        return new StockReport(day, summarize(rows, ordersDelivered == null ? 0 : ordersDelivered), rows, log,
                partners, deliveries);
    }

    static Summary summarize(List<ProductRow> rows, int ordersDelivered) {
        List<ProductRow> live = rows.stream().filter(r -> r.status() != StockStatus.HIDDEN).toList();
        int inShop = (int) live.stream().filter(r -> r.available() > 0).count();
        int out = (int) live.stream().filter(r -> r.status() == StockStatus.OUT_OF_STOCK).count();
        int low = (int) live.stream().filter(r -> r.status() == StockStatus.LOW_STOCK).count();
        int units = live.stream().mapToInt(ProductRow::available).sum();
        int added = rows.stream().mapToInt(ProductRow::added).sum();
        int restocked = (int) rows.stream().filter(r -> r.added() > 0).count();
        int deliveredUnits = rows.stream().mapToInt(ProductRow::delivered).sum();
        return new Summary(inShop, live.size(), units, added, restocked, deliveredUnits, ordersDelivered, out, low);
    }
}
