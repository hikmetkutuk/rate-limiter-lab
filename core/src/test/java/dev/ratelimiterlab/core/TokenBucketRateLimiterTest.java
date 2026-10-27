package dev.ratelimiterlab.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class TokenBucketRateLimiterTest {

    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");
    private static final String ALICE = "alice";
    private static final String BOB = "bob";

    private final MutableClock clock = MutableClock.at(START);
    // Capacity 3, refill 2 tokens per second: one token every 500ms.
    private final RateLimiter limiter = new TokenBucketRateLimiter(3, 2, Duration.ofSeconds(1), clock);

    @Test
    void newBucketStartsFull() {
        assertEquals(Decision.allowed(2), limiter.tryAcquire(ALICE));
        assertEquals(Decision.allowed(1), limiter.tryAcquire(ALICE));
        assertEquals(Decision.allowed(0), limiter.tryAcquire(ALICE));
    }

    @Test
    void deniesWhenEmpty() {
        drain(ALICE);

        Decision decision = limiter.tryAcquire(ALICE);

        assertFalse(decision.allowed());
        assertEquals(0, decision.remaining());
    }

    @Test
    void refillsOneTokenPerInterval() {
        drain(ALICE);

        clock.advance(Duration.ofMillis(499));
        assertFalse(limiter.tryAcquire(ALICE).allowed());

        clock.advance(Duration.ofMillis(1));
        assertEquals(Decision.allowed(0), limiter.tryAcquire(ALICE));
        assertFalse(limiter.tryAcquire(ALICE).allowed());
    }

    @Test
    void partialRefillAccumulatesAcrossRequests() {
        drain(ALICE);

        clock.advance(Duration.ofMillis(250));
        assertFalse(limiter.tryAcquire(ALICE).allowed());
        clock.advance(Duration.ofMillis(250));

        assertTrue(limiter.tryAcquire(ALICE).allowed());
    }

    @Test
    void refillIsCappedAtCapacity() {
        drain(ALICE);
        clock.advance(Duration.ofMinutes(10));

        assertEquals(Decision.allowed(2), limiter.tryAcquire(ALICE));
        assertEquals(Decision.allowed(1), limiter.tryAcquire(ALICE));
        assertEquals(Decision.allowed(0), limiter.tryAcquire(ALICE));
        assertFalse(limiter.tryAcquire(ALICE).allowed());
    }

    @Test
    void retryAfterIsTimeUntilNextToken() {
        drain(ALICE);

        assertEquals(Duration.ofMillis(500), limiter.tryAcquire(ALICE).retryAfter());

        clock.advance(Duration.ofMillis(200));
        assertEquals(Duration.ofMillis(300), limiter.tryAcquire(ALICE).retryAfter());
    }

    @Test
    void retryAfterRoundsUpToWholeMillis() {
        RateLimiter slow = new TokenBucketRateLimiter(1, 3, Duration.ofSeconds(1), clock);
        slow.tryAcquire(ALICE);

        // One token every 333.33ms: waiting 333ms would not be enough.
        assertEquals(Duration.ofMillis(334), slow.tryAcquire(ALICE).retryAfter());
        clock.advance(Duration.ofMillis(334));
        assertTrue(slow.tryAcquire(ALICE).allowed());
    }

    @Test
    void keysAreLimitedIndependently() {
        drain(ALICE);

        assertTrue(limiter.tryAcquire(BOB).allowed());
    }

    @Test
    void rejectsInvalidConfiguration() {
        Duration second = Duration.ofSeconds(1);
        assertThrows(IllegalArgumentException.class, () -> new TokenBucketRateLimiter(0, 1, second, clock));
        assertThrows(IllegalArgumentException.class, () -> new TokenBucketRateLimiter(1, 0, second, clock));
        assertThrows(IllegalArgumentException.class, () -> new TokenBucketRateLimiter(1, 1, Duration.ZERO, clock));
        assertThrows(NullPointerException.class, () -> new TokenBucketRateLimiter(1, 1, second, null));
    }

    private void drain(String key) {
        for (int i = 0; i < 3; i++) {
            limiter.tryAcquire(key);
        }
    }
}
