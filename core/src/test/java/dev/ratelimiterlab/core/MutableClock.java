package dev.ratelimiterlab.core;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

final class MutableClock extends Clock {

    private final AtomicReference<Instant> now;
    private final ZoneId zone;

    private MutableClock(AtomicReference<Instant> now, ZoneId zone) {
        this.now = now;
        this.zone = zone;
    }

    static MutableClock at(Instant start) {
        return new MutableClock(new AtomicReference<>(Objects.requireNonNull(start, "start")), ZoneOffset.UTC);
    }

    void advance(Duration amount) {
        if (amount.isNegative()) {
            throw new IllegalArgumentException("clock cannot go backwards: " + amount);
        }
        now.updateAndGet(current -> current.plus(amount));
    }

    @Override
    public Instant instant() {
        return now.get();
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return new MutableClock(now, zone);
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof MutableClock other && now == other.now && zone.equals(other.zone);
    }

    @Override
    public int hashCode() {
        return 31 * System.identityHashCode(now) + zone.hashCode();
    }
}
