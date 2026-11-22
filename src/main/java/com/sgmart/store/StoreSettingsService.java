package com.sgmart.store;

import com.sgmart.common.ApiException;
import com.sgmart.common.PhoneNumbers;
import com.sgmart.common.StoreTime;
import com.sgmart.store.StoreDtos.StoreInfo;
import com.sgmart.store.StoreDtos.UpdateStoreRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalTime;
import java.time.ZonedDateTime;

/**
 * Store settings and the bill rules every other module uses.
 * The delivery fee and first-order cap are still to be confirmed by the business,
 * so they are always read from the database, never hard-coded.
 */
@Service
public class StoreSettingsService {

    private final StoreSettingsRepository repository;
    private final Clock clock;

    public StoreSettingsService(StoreSettingsRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public StoreSettings current() {
        return repository.findById(StoreSettings.SINGLE_ROW_ID)
                .orElseThrow(() -> new IllegalStateException(
                        "store_settings row is missing. Check that Flyway migration V2 ran."));
    }

    @Transactional(readOnly = true)
    public StoreInfo info() {
        return toInfo(current());
    }

    @Transactional
    public StoreInfo update(UpdateStoreRequest request, Long adminId) {
        if (request.opensAt().equals(request.closesAt())) {
            throw ApiException.badRequest("INVALID_HOURS", "Opening and closing times can't be the same.", "closesAt");
        }
        StoreSettings s = current();
        s.setStoreName(request.storeName().trim());
        s.setPhone(PhoneNumbers.normalize(request.phone(), "phone"));
        s.setOpensAt(request.opensAt());
        s.setClosesAt(request.closesAt());
        s.setEtaMinutes(request.etaMinutes());
        s.setDeliveryFee(request.deliveryFee());
        s.setFreeDeliveryMin(request.freeDeliveryMin());
        s.setFirstOrderCodLimit(request.firstOrderCodLimit());
        s.setUpdatedByAdminId(adminId);
        return toInfo(repository.saveAndFlush(s));
    }

    /** Delivery fee for a cart: free for an empty cart or at/above the free-delivery amount. */
    public BigDecimal deliveryFeeFor(BigDecimal itemTotal) {
        StoreSettings s = current();
        if (itemTotal.signum() <= 0 || itemTotal.compareTo(s.getFreeDeliveryMin()) >= 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        return s.getDeliveryFee();
    }

    /** True when "now" in India is inside opening hours. Handles hours past midnight (e.g. 18:00 to 02:00). */
    public boolean isOpenNow() {
        return isOpenAt(current(), ZonedDateTime.now(clock.withZone(StoreTime.ZONE)).toLocalTime());
    }

    static boolean isOpenAt(StoreSettings s, LocalTime time) {
        LocalTime opens = s.getOpensAt();
        LocalTime closes = s.getClosesAt();
        if (opens.isBefore(closes)) {
            return !time.isBefore(opens) && time.isBefore(closes);
        }
        return !time.isBefore(opens) || time.isBefore(closes);
    }

    private StoreInfo toInfo(StoreSettings s) {
        boolean openNow = isOpenAt(s, ZonedDateTime.now(clock.withZone(StoreTime.ZONE)).toLocalTime());
        return new StoreInfo(s.getStoreName(), s.getPhone(), s.getOpensAt(), s.getClosesAt(), openNow,
                s.getEtaMinutes(), s.getDeliveryFee(), s.getFreeDeliveryMin(), s.getFirstOrderCodLimit(),
                s.getUpdatedAt());
    }
}
