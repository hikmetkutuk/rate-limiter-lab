package dev.ratelimiterlab.core;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Each key has a bucket of up to {@code capacity} tokens, refilled at {@code refillTokens} per
 * {@code refillPeriod}. A request takes one token; an empty bucket denies. New keys start full.
 *
 * <p>Refill is lazy: computed from elapsed time on each request, no background thread. Tokens are
 * stored scaled by the refill period in millis so partial tokens accumulate exactly in integers.
 */
public final class TokenBucketRateLimiter implements RateLimiter {

    /** {@code scaledTokens} is tokens * periodMillis. */
    private record Bucket(long scaledTokens, long lastRefillMillis) {}

    private final long capacity;
    private final long refillTokens;
    private final long periodMillis;
    private final Clock clock;
    private final ConcurrentMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    public TokenBucketRateLimiter(long capacity, long refillTokens, Duration refillPeriod, Clock clock) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be > 0, was " + capacity);
        }
        if (refillTokens <= 0) {
            throw new IllegalArgumentException("refillTokens must be > 0, was " + refillTokens);
        }
        if (refillPeriod.toMillis() <= 0) {
            throw new IllegalArgumentException("refillPeriod must be at least 1ms, was " + refillPeriod);
        }
        this.capacity = capacity;
        this.refillTokens = refillTokens;
        this.periodMillis = refillPeriod.toMillis();
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public Decision tryAcquire(String key) {
        Objects.requireNonNull(key, "key");
        long now = clock.millis();
        long maxScaled = capacity * periodMillis;

        Decision[] decision = new Decision[1];
        buckets.compute(key, (k, stored) -> {
            long scaled = maxScaled;
            if (stored != null) {
                long elapsed = Math.max(0, now - stored.lastRefillMillis());
                scaled = Math.min(maxScaled, stored.scaledTokens() + elapsed * refillTokens);
            }
            if (scaled >= periodMillis) {
                scaled -= periodMillis;
                decision[0] = Decision.allowed(scaled / periodMillis);
            } else {
                long missing = periodMillis - scaled;
                decision[0] = Decision.denied(Duration.ofMillis(Math.ceilDiv(missing, refillTokens)));
            }
            return new Bucket(scaled, now);
        });
        return decision[0];
    }
}
