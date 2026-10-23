package dev.ratelimiterlab.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class MutableClockTest {

    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void staysPutUntilAdvanced() {
        MutableClock clock = MutableClock.at(START);

        assertEquals(START, clock.instant());
        assertEquals(START.toEpochMilli(), clock.millis());
    }

    @Test
    void advancesByGivenAmount() {
        MutableClock clock = MutableClock.at(START);

        clock.advance(Duration.ofMillis(1500));

        assertEquals(START.plusMillis(1500), clock.instant());
    }

    @Test
    void rejectsGoingBackwards() {
        MutableClock clock = MutableClock.at(START);

        assertThrows(IllegalArgumentException.class, () -> clock.advance(Duration.ofSeconds(-1)));
    }

    @Test
    void zonedCopySharesTime() {
        MutableClock clock = MutableClock.at(START);
        var zoned = clock.withZone(ZoneId.of("Europe/Istanbul"));

        clock.advance(Duration.ofSeconds(10));

        assertEquals(START.plusSeconds(10), zoned.instant());
    }

    @Test
    void equalOnlyWhenSharingTimeSourceAndZone() {
        MutableClock clock = MutableClock.at(START);
        MutableClock independent = MutableClock.at(START);

        assertEquals(clock, clock.withZone(ZoneOffset.UTC));
        assertEquals(clock.hashCode(), clock.withZone(ZoneOffset.UTC).hashCode());
        assertNotEquals(clock, independent);
        assertNotEquals(clock, clock.withZone(ZoneId.of("Europe/Istanbul")));
    }
}
