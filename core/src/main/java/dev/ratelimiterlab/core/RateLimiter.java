package dev.ratelimiterlab.core;

/**
 * Decides whether a request identified by {@code key} may proceed.
 *
 * <p>Implementations must be thread-safe: concurrent calls for the same key must never
 * allow more requests than the configured limit.
 */
public interface RateLimiter {

    /**
     * Attempts to consume one permit for {@code key}.
     *
     * @param key identifies who is being limited (user id, IP, API key, ...)
     * @return the decision; never {@code null}
     */
    Decision tryAcquire(String key);
}
