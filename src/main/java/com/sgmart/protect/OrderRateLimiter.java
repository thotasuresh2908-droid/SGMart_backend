package com.sgmart.protect;

import com.sgmart.common.ApiException;
import com.sgmart.config.SgMartProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stops one device or script from placing many orders: at most N orders per IP address in 10 minutes.
 * Kept in memory, which is fine for a single server.
 */
@Component
public class OrderRateLimiter {

    private static final Duration WINDOW = Duration.ofMinutes(10);

    private final int maxPerWindow;
    private final Clock clock;
    private final Map<String, Deque<Instant>> recent = new ConcurrentHashMap<>();

    public OrderRateLimiter(SgMartProperties props, Clock clock) {
        this.maxPerWindow = props.maxOrdersPerIpPer10Minutes();
        this.clock = clock;
    }

    /** Throws TOO_MANY_ORDERS (HTTP 429) when the limit is reached; otherwise counts this attempt. */
    public void check(String clientIp) {
        String key = clientIp == null ? "unknown" : clientIp;
        Instant now = Instant.now(clock);
        Deque<Instant> times = recent.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (times) {
            while (!times.isEmpty() && times.peekFirst().isBefore(now.minus(WINDOW))) {
                times.pollFirst();
            }
            if (times.size() >= maxPerWindow) {
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_ORDERS",
                        "Too many orders from this device. Please wait a few minutes or call the store.");
            }
            times.addLast(now);
        }
        if (recent.size() > 10_000) {
            recent.entrySet().removeIf(e -> e.getValue().isEmpty());
        }
    }
}
