package dev.ratelimiterlab.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;

class SlidingWindowCounterRateLimiterTest {

    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");
    private static final String ALICE = "alice";
    private static final String BOB = "bob";

    private final MutableClock clock = MutableClock.at(START);
    private final RateLimiter limiter = new SlidingWindowCounterRateLimiter(3, Duration.ofSeconds(1), clock);

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
    void previousWindowStillCountsRightAfterRollover() {
        exhaust(ALICE);
        clock.advance(Duration.ofSeconds(1));

        // previous = 3 at full weight: estimated 3, nothing fits yet.
        assertFalse(limiter.tryAcquire(ALICE).allowed());
    }

    @Test
    void previousWindowWeightDecaysOverCurrentWindow() {
        exhaust(ALICE);
        clock.advance(Duration.ofMillis(1500));

        // previous = 3 at weight 0.5 -> 1.5; one more makes 2.5 <= 3, a second would be 3.5.
        assertTrue(limiter.tryAcquire(ALICE).allowed());
        assertFalse(limiter.tryAcquire(ALICE).allowed());
    }

    @Test
    void skippingAWholeWindowResetsTheCount() {
        exhaust(ALICE);
        clock.advance(Duration.ofSeconds(2));

        assertEquals(Decision.allowed(2), limiter.tryAcquire(ALICE));
    }

    @Test
    void retryAfterCoversRestOfWindowPlusDecayInNextWhenCurrentIsFull() {
        exhaust(ALICE);

        // Next window: 3 * (1000 - e) / 1000 + 1 <= 3  ->  e >= 334ms.
        assertEquals(Duration.ofMillis(1334), limiter.tryAcquire(ALICE).retryAfter());
    }

    @Test
    void retryAfterIsTimeUntilPreviousWeightDecaysEnough() {
        exhaust(ALICE);
        clock.advance(Duration.ofMillis(1100));

        assertEquals(Duration.ofMillis(234), limiter.tryAcquire(ALICE).retryAfter());
    }

    @Test
    void retryAfterIsExact() {
        exhaust(ALICE);
        clock.advance(Duration.ofMillis(1333));
        assertFalse(limiter.tryAcquire(ALICE).allowed());

        clock.advance(Duration.ofMillis(1));
        assertTrue(limiter.tryAcquire(ALICE).allowed());
    }

    @Test
    void deniedRequestsAreNotCounted() {
        exhaust(ALICE);
        for (int i = 0; i < 10; i++) {
            limiter.tryAcquire(ALICE);
        }
        clock.advance(Duration.ofMillis(1334));

        assertTrue(limiter.tryAcquire(ALICE).allowed());
    }

    @Test
    void keysAreLimitedIndependently() {
        exhaust(ALICE);

        assertTrue(limiter.tryAcquire(BOB).allowed());
    }

    @Test
    void concurrentRequestsOnOneKeyNeverExceedLimit() throws Exception {
        int limit = 100;
        int threads = 10_000;
        RateLimiter shared = new SlidingWindowCounterRateLimiter(limit, Duration.ofSeconds(1), clock);
        CountDownLatch startGate = new CountDownLatch(1);
        Set<Long> remainingSeen = ConcurrentHashMap.newKeySet();

        List<Future<Decision>> results = new ArrayList<>(threads);
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < threads; i++) {
                results.add(executor.submit(() -> {
                    startGate.await();
                    return shared.tryAcquire(ALICE);
                }));
            }
            startGate.countDown();
        }

        int allowed = 0;
        for (Future<Decision> result : results) {
            Decision decision = result.get();
            if (decision.allowed()) {
                allowed++;
                assertTrue(remainingSeen.add(decision.remaining()), "duplicate remaining " + decision.remaining());
            }
        }

        assertEquals(limit, allowed);
        assertEquals(LongStream.range(0, limit).boxed().collect(Collectors.toSet()), remainingSeen);
    }

    @Test
    void rejectsInvalidConfiguration() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new SlidingWindowCounterRateLimiter(0, Duration.ofSeconds(1), clock));
        assertThrows(
                IllegalArgumentException.class, () -> new SlidingWindowCounterRateLimiter(1, Duration.ZERO, clock));
        assertThrows(
                NullPointerException.class, () -> new SlidingWindowCounterRateLimiter(1, Duration.ofSeconds(1), null));
    }

    private void exhaust(String key) {
        for (int i = 0; i < 3; i++) {
            limiter.tryAcquire(key);
        }
    }
}
