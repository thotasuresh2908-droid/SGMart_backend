package com.sgmart.store;

import com.sgmart.common.ApiException;
import com.sgmart.store.StoreDtos.StoreInfo;
import com.sgmart.store.StoreDtos.UpdateStoreRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StoreSettingsServiceTest {

    private final StoreSettingsRepository repository = mock(StoreSettingsRepository.class);
    private final StoreSettings settings = StoreSettings.defaults("9000012345"); // 07:00 to 23:00 IST

    @BeforeEach
    void setUp() {
        when(repository.findById(StoreSettings.SINGLE_ROW_ID)).thenReturn(Optional.of(settings));
        when(repository.saveAndFlush(any(StoreSettings.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private StoreSettingsService serviceAt(String utcInstant) {
        return new StoreSettingsService(repository, Clock.fixed(Instant.parse(utcInstant), ZoneOffset.UTC));
    }

    @Test
    void openAt0830India() {
        assertThat(serviceAt("2026-11-12T03:00:00Z").isOpenNow()).isTrue();   // 08:30 IST
    }

    @Test
    void closedAt2330India() {
        assertThat(serviceAt("2026-11-12T18:00:00Z").isOpenNow()).isFalse();  // 23:30 IST
    }

    @Test
    void hoursPastMidnightWork() {
        settings.setOpensAt(LocalTime.of(18, 0));
        settings.setClosesAt(LocalTime.of(2, 0));
        assertThat(serviceAt("2026-11-12T20:00:00Z").isOpenNow()).isTrue();   // 01:30 IST
        assertThat(serviceAt("2026-11-12T06:00:00Z").isOpenNow()).isFalse();  // 11:30 IST
    }

    @Test
    void deliveryFeeFollowsStoreSettings() {
        StoreSettingsService service = serviceAt("2026-11-12T03:00:00Z");
        assertThat(service.deliveryFeeFor(new BigDecimal("150.00"))).isEqualByComparingTo("25.00");
        assertThat(service.deliveryFeeFor(new BigDecimal("199.00"))).isEqualByComparingTo("0");
        assertThat(service.deliveryFeeFor(BigDecimal.ZERO)).isEqualByComparingTo("0");
    }

    @Test
    void ownerCanChangeTheFeeRuleLater() {
        settings.setDeliveryFee(new BigDecimal("30.00"));
        settings.setFreeDeliveryMin(new BigDecimal("299.00"));
        StoreSettingsService service = serviceAt("2026-11-12T03:00:00Z");
        assertThat(service.deliveryFeeFor(new BigDecimal("250.00"))).isEqualByComparingTo("30.00");
    }

    @Test
    void updateCleansThePhoneAndAllowsNoCap() {
        StoreInfo info = serviceAt("2026-11-12T03:00:00Z").update(new UpdateStoreRequest(
                "SGMart", "+91 98480 12345", LocalTime.of(6, 0), LocalTime.of(22, 0), 15,
                new BigDecimal("20.00"), new BigDecimal("249.00"), null), 1L);
        assertThat(info.phone()).isEqualTo("9848012345");
        assertThat(info.firstOrderCodLimit()).isNull();
        assertThat(info.etaMinutes()).isEqualTo(15);
    }

    @Test
    void sameOpeningAndClosingTimeIsRejected() {
        UpdateStoreRequest bad = new UpdateStoreRequest("SGMart", "9000012345", LocalTime.of(9, 0),
                LocalTime.of(9, 0), 10, new BigDecimal("25.00"), new BigDecimal("199.00"), null);
        assertThatThrownBy(() -> serviceAt("2026-11-12T03:00:00Z").update(bad, 1L))
                .isInstanceOf(ApiException.class)
                .extracting("code").isEqualTo("INVALID_HOURS");
    }
}
