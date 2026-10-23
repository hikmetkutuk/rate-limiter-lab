package dev.ratelimiterlab.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class FixedWindowRateLimiterTest {

    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");
    private static final String ALICE = "alice";
    private static final String BOB = "bob";

    private final MutableClock clock = MutableClock.at(START);
    private final RateLimiter limiter = new FixedWindowRateLimiter(3, Duration.ofSeconds(1), clock);

    @Test
    void allowsUpToLimitWithDecreasingRemaining() {
        assertEquals(Decision.allowed(2), limiter.tryAcquire(ALICE));
        assertEquals(Decision.allowed(1), limiter.tryAcquire(ALICE));
        assertEquals(Decision.allowed(0), limiter.tryAcquire(ALICE));
    }

    @Test
    void deniesOnceLimitIsReached() {
        exhaust(ALICE);

        Decision decision = limiter.tryAcquire(ALICE);

        assertFalse(decision.allowed());
        assertEquals(0, decision.remaining());
    }

    @Test
    void keepsDenyingWhileWindowLasts() {
        exhaust(ALICE);

        for (int i = 0; i < 10; i++) {
            assertFalse(limiter.tryAcquire(ALICE).allowed());
        }
    }

    @Test
    void keysAreLimitedIndependently() {
        exhaust(ALICE);

        assertTrue(limiter.tryAcquire(BOB).allowed());
    }

    @Test
    void rejectsInvalidConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> new FixedWindowRateLimiter(0, Duration.ofSeconds(1), clock));
        assertThrows(IllegalArgumentException.class, () -> new FixedWindowRateLimiter(1, Duration.ZERO, clock));
        assertThrows(NullPointerException.class, () -> new FixedWindowRateLimiter(1, Duration.ofSeconds(1), null));
    }

    private void exhaust(String key) {
        for (int i = 0; i < 3; i++) {
            limiter.tryAcquire(key);
        }
    }
}
