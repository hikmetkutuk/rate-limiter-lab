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
    void stillDeniesOneMillisecondBeforeWindowEnds() {
        exhaust(ALICE);
        clock.advance(Duration.ofMillis(999));

        assertFalse(limiter.tryAcquire(ALICE).allowed());
    }

    @Test
    void windowRolloverResetsTheCount() {
        exhaust(ALICE);
        clock.advance(Duration.ofSeconds(1));

        assertEquals(Decision.allowed(2), limiter.tryAcquire(ALICE));
        assertEquals(Decision.allowed(1), limiter.tryAcquire(ALICE));
        assertEquals(Decision.allowed(0), limiter.tryAcquire(ALICE));
        assertFalse(limiter.tryAcquire(ALICE).allowed());
    }

    @Test
    void retryAfterIsTimeUntilWindowEnds() {
        exhaust(ALICE);

        assertEquals(Duration.ofSeconds(1), limiter.tryAcquire(ALICE).retryAfter());

        clock.advance(Duration.ofMillis(300));
        assertEquals(Duration.ofMillis(700), limiter.tryAcquire(ALICE).retryAfter());

        clock.advance(Duration.ofMillis(699));
        assertEquals(Duration.ofMillis(1), limiter.tryAcquire(ALICE).retryAfter());
    }

    @Test
    void retryAfterIsAlignedToWindowNotFirstRequest() {
        clock.advance(Duration.ofMillis(400));
        exhaust(ALICE);

        assertEquals(Duration.ofMillis(600), limiter.tryAcquire(ALICE).retryAfter());
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
        RateLimiter shared = new FixedWindowRateLimiter(limit, Duration.ofSeconds(1), clock);
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
        // Each permit was handed out exactly once: remaining values are exactly 0..limit-1.
        assertEquals(LongStream.range(0, limit).boxed().collect(Collectors.toSet()), remainingSeen);
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
