package com.sgmart.delivery;

import com.sgmart.common.ApiException;
import com.sgmart.common.PhoneNumbers;
import com.sgmart.common.StoreTime;
import com.sgmart.delivery.DeliveryPartnerDtos.CreatePartnerRequest;
import com.sgmart.delivery.DeliveryPartnerDtos.PartnerView;
import com.sgmart.delivery.DeliveryPartnerDtos.UpdatePartnerRequest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class DeliveryPartnerService {

    static final String COUNTS_SQL = """
            SELECT o.delivery_partner_id AS partner_id,
                   COUNT(*) AS total_delivered,
                   COALESCE(SUM(o.delivered_at >= :dayStart AND o.delivered_at < :dayEnd), 0) AS today_delivered
            FROM orders o
            WHERE o.status = 'DELIVERED' AND o.delivery_partner_id IS NOT NULL
            GROUP BY o.delivery_partner_id
            """;

    static final String CURRENT_SQL = """
            SELECT delivery_partner_id AS partner_id, order_no
            FROM orders WHERE status = 'OUT_FOR_DELIVERY' AND delivery_partner_id IS NOT NULL
            ORDER BY out_for_delivery_at
            """;

    private final DeliveryPartnerRepository repository;
    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    public DeliveryPartnerService(DeliveryPartnerRepository repository, NamedParameterJdbcTemplate jdbc, Clock clock) {
        this.repository = repository;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<PartnerView> list() {
        LocalDate today = LocalDate.now(clock.withZone(StoreTime.ZONE));
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("dayStart", Timestamp.from(today.atStartOfDay(StoreTime.ZONE).toInstant()))
                .addValue("dayEnd", Timestamp.from(today.plusDays(1).atStartOfDay(StoreTime.ZONE).toInstant()));
        Map<Long, int[]> counts = new HashMap<>();
        jdbc.query(COUNTS_SQL, params, rs -> {
            counts.put(rs.getLong("partner_id"), new int[]{rs.getInt("today_delivered"), rs.getInt("total_delivered")});
        });
        Map<Long, List<String>> current = new HashMap<>();
        jdbc.query(CURRENT_SQL, params, rs -> {
            current.computeIfAbsent(rs.getLong("partner_id"), k -> new ArrayList<>()).add(rs.getString("order_no"));
        });
        return repository.findAllByOrderByActiveDescNameAsc().stream().map(p -> {
            int[] c = counts.getOrDefault(p.getId(), new int[]{0, 0});
            return new PartnerView(p.getId(), p.getName(), p.getMobile(), p.getVehicleType(), p.getStatus(),
                    p.isActive(), c[0], c[1], current.getOrDefault(p.getId(), List.of()));
        }).toList();
    }

    @Transactional
    public PartnerView create(CreatePartnerRequest request) {
        String mobile = PhoneNumbers.normalize(request.mobile(), "mobile");
        requireUniqueMobile(mobile, null);
        DeliveryPartner p = repository.save(new DeliveryPartner(request.name().trim(), mobile, request.vehicleType()));
        return simpleView(p);
    }

    @Transactional
    public PartnerView update(Long id, UpdatePartnerRequest request) {
        DeliveryPartner p = load(id);
        String mobile = PhoneNumbers.normalize(request.mobile(), "mobile");
        requireUniqueMobile(mobile, id);
        if (!request.active() && p.getStatus() == PartnerStatus.ON_DELIVERY) {
            throw ApiException.conflict("PARTNER_BUSY", p.getName() + " is on a delivery. Finish it first.");
        }
        p.setName(request.name().trim());
        p.setMobile(mobile);
        p.setVehicleType(request.vehicleType());
        p.setActive(request.active());
        return simpleView(p);
    }

    /** "Mark as available" / "Mark as off duty". A partner on a delivery is freed only by delivering. */
    @Transactional
    public PartnerView setStatus(Long id, PartnerStatus status) {
        if (status == PartnerStatus.ON_DELIVERY) {
            throw ApiException.badRequest("INVALID_STATUS", "Choose Available or Off duty.", "status");
        }
        DeliveryPartner p = load(id);
        if (p.getStatus() == PartnerStatus.ON_DELIVERY) {
            throw ApiException.conflict("PARTNER_BUSY", p.getName() + " is on a delivery. Mark the order delivered first.");
        }
        p.setStatus(status);
        return simpleView(p);
    }

    private void requireUniqueMobile(String mobile, Long selfId) {
        repository.findByMobile(mobile)
                .filter(other -> !other.getId().equals(selfId))
                .ifPresent(other -> {
                    throw ApiException.conflict("PARTNER_EXISTS", "This mobile number belongs to " + other.getName() + ".");
                });
    }

    private DeliveryPartner load(Long id) {
        return repository.findById(id).orElseThrow(() -> ApiException.notFound("Delivery partner not found."));
    }

    private static PartnerView simpleView(DeliveryPartner p) {
        return new PartnerView(p.getId(), p.getName(), p.getMobile(), p.getVehicleType(), p.getStatus(),
                p.isActive(), 0, 0, List.of());
    }
}
