package dev.ratelimiterlab.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * A client sends {@code limit} requests just before a window boundary and {@code limit} more just
 * after it. Fixed window lets all of them through: 2x the limit within 2ms. Sliding window counter
 * still sees the previous window at almost full weight and holds the total to the limit.
 */
class BoundaryBurstTest {

    private static final int LIMIT = 3;
    private static final Duration WINDOW = Duration.ofSeconds(1);
    private static final String ALICE = "alice";

    private final MutableClock clock = MutableClock.at(Instant.parse("2026-01-01T00:00:00Z"));

    @Test
    void fixedWindowAllowsTwiceTheLimitAcrossTheBoundary() {
        RateLimiter limiter = new FixedWindowRateLimiter(LIMIT, WINDOW, clock);

        assertEquals(2 * LIMIT, allowedInBurstAcrossBoundary(limiter));
    }

    @Test
    void slidingWindowCounterHoldsTheLimitAcrossTheBoundary() {
        RateLimiter limiter = new SlidingWindowCounterRateLimiter(LIMIT, WINDOW, clock);

        assertEquals(LIMIT, allowedInBurstAcrossBoundary(limiter));
    }

    /** Fires {@code LIMIT} requests at 999ms and {@code LIMIT} at 1000ms; returns how many were allowed. */
    private int allowedInBurstAcrossBoundary(RateLimiter limiter) {
        clock.advance(Duration.ofMillis(999));
        int allowed = burst(limiter);
        clock.advance(Duration.ofMillis(1));
        return allowed + burst(limiter);
    }

    private int burst(RateLimiter limiter) {
        int allowed = 0;
        for (int i = 0; i < LIMIT; i++) {
            if (limiter.tryAcquire(ALICE).allowed()) {
                allowed++;
            }
        }
        return allowed;
    }
}
