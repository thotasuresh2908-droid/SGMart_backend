package com.sgmart.protect;

import com.sgmart.common.ApiException;
import com.sgmart.config.SgMartProperties;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderRateLimiterTest {

    /** A clock the test can move forward. */
    static final class TestClock extends Clock {
        Instant now = Instant.parse("2026-11-12T03:00:00Z");
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    private final TestClock clock = new TestClock();
    private final OrderRateLimiter limiter = new OrderRateLimiter(
            new SgMartProperties(null, null, null, null, new SgMartProperties.Orders(5)), clock);

    @Test
    void sixthOrderInTenMinutesIsStoppedThenAllowedAgainLater() {
        for (int i = 0; i < 5; i++) {
            limiter.check("203.0.113.7");
        }
        assertThatThrownBy(() -> limiter.check("203.0.113.7"))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("TOO_MANY_ORDERS");
        assertThatCode(() -> limiter.check("198.51.100.2")).doesNotThrowAnyException();   // other devices unaffected

        clock.now = clock.now.plus(Duration.ofMinutes(11));
        assertThatCode(() -> limiter.check("203.0.113.7")).doesNotThrowAnyException();
    }
}
