package dev.ratelimiterlab.core;

import java.time.Duration;
import java.util.Objects;

/**
 * Outcome of a single {@code tryAcquire} call.
 *
 * @param allowed    whether the request may proceed
 * @param remaining  permits left in the current window/bucket; always 0 when denied
 * @param retryAfter how long the caller should wait before retrying; always zero when allowed
 */
public record Decision(boolean allowed, long remaining, Duration retryAfter) {

    public Decision {
        Objects.requireNonNull(retryAfter, "retryAfter");
        if (remaining < 0) {
            throw new IllegalArgumentException("remaining must be >= 0, was " + remaining);
        }
        if (retryAfter.isNegative()) {
            throw new IllegalArgumentException("retryAfter must be >= 0, was " + retryAfter);
        }
        if (allowed && !retryAfter.isZero()) {
            throw new IllegalArgumentException("allowed decision cannot have retryAfter");
        }
        if (!allowed && remaining != 0) {
            throw new IllegalArgumentException("denied decision cannot have remaining permits");
        }
    }

    public static Decision allowed(long remaining) {
        return new Decision(true, remaining, Duration.ZERO);
    }

    public static Decision denied(Duration retryAfter) {
        return new Decision(false, 0, retryAfter);
    }
}
