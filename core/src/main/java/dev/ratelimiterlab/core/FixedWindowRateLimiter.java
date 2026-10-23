package dev.ratelimiterlab.core;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Allows up to {@code limit} requests per key in each fixed, epoch-aligned window.
 *
 * <p>Simple and cheap, but a client can send {@code limit} requests at the end of one window and
 * {@code limit} more at the start of the next: up to 2x the limit in a short burst.
 */
public final class FixedWindowRateLimiter implements RateLimiter {

    private record Window(long index, long count) {}

    private final long limit;
    private final long windowMillis;
    private final Clock clock;
    private final ConcurrentMap<String, Window> windows = new ConcurrentHashMap<>();

    public FixedWindowRateLimiter(long limit, Duration window, Clock clock) {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be > 0, was " + limit);
        }
        if (window.toMillis() <= 0) {
            throw new IllegalArgumentException("window must be at least 1ms, was " + window);
        }
        this.limit = limit;
        this.windowMillis = window.toMillis();
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public Decision tryAcquire(String key) {
        Objects.requireNonNull(key, "key");
        long now = clock.millis();
        long index = now / windowMillis;

        // count is capped at limit + 1 so "over the limit" is distinguishable from "exactly at it".
        Window window = windows.compute(key, (k, current) -> {
            if (current == null || current.index() != index) {
                return new Window(index, 1);
            }
            return new Window(index, Math.min(current.count() + 1, limit + 1));
        });

        if (window.count() <= limit) {
            return Decision.allowed(limit - window.count());
        }
        long windowEnd = (index + 1) * windowMillis;
        return Decision.denied(Duration.ofMillis(windowEnd - now));
    }
}
