package dev.ratelimiterlab.core;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Approximates a sliding window by weighting the previous fixed window's count by how much of it
 * still overlaps the sliding window:
 *
 * <pre>estimated = previous * (window - elapsed) / window + current</pre>
 *
 * <p>Smooths out the fixed window's 2x boundary burst while keeping only two counters per key.
 * Denied requests are not counted. All arithmetic is scaled by the window length to stay in
 * integers.
 */
public final class SlidingWindowCounterRateLimiter implements RateLimiter {

    private record Window(long index, long current, long previous) {

        Window rollTo(long newIndex) {
            if (newIndex == index) {
                return this;
            }
            return new Window(newIndex, 0, newIndex == index + 1 ? current : 0);
        }
    }

    private final long limit;
    private final long windowMillis;
    private final Clock clock;
    private final ConcurrentMap<String, Window> windows = new ConcurrentHashMap<>();

    public SlidingWindowCounterRateLimiter(long limit, Duration window, Clock clock) {
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
        long elapsed = now - index * windowMillis;

        Decision[] decision = new Decision[1];
        windows.compute(key, (k, stored) -> {
            Window window = (stored == null ? new Window(index, 0, 0) : stored).rollTo(index);
            long weightedPrevious = window.previous() * (windowMillis - elapsed);
            long usedAfter = weightedPrevious + (window.current() + 1) * windowMillis;
            if (usedAfter <= limit * windowMillis) {
                decision[0] = Decision.allowed(limit - Math.ceilDiv(usedAfter, windowMillis));
                return new Window(index, window.current() + 1, window.previous());
            }
            decision[0] = Decision.denied(Duration.ofMillis(retryAfterMillis(window, elapsed)));
            return window;
        });
        return decision[0];
    }

    /** Time until one more request fits, assuming no other requests arrive meanwhile. */
    private long retryAfterMillis(Window window, long elapsed) {
        if (window.current() + 1 <= limit) {
            // Fits once the previous window's weight decays enough within this window.
            long allowedWeight = (limit - window.current() - 1) * windowMillis / window.previous();
            return windowMillis - allowedWeight - elapsed;
        }
        // Current window is full: it becomes "previous" next window and must decay there.
        long allowedWeight = (limit - 1) * windowMillis / window.current();
        return (windowMillis - elapsed) + (windowMillis - allowedWeight);
    }
}
