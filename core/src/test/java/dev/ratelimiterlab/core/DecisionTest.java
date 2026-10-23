package dev.ratelimiterlab.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class DecisionTest {

    @Test
    void allowedHasRemainingAndNoRetryAfter() {
        Decision decision = Decision.allowed(4);

        assertTrue(decision.allowed());
        assertEquals(4, decision.remaining());
        assertEquals(Duration.ZERO, decision.retryAfter());
    }

    @Test
    void deniedHasRetryAfterAndNoRemaining() {
        Decision decision = Decision.denied(Duration.ofSeconds(3));

        assertFalse(decision.allowed());
        assertEquals(0, decision.remaining());
        assertEquals(Duration.ofSeconds(3), decision.retryAfter());
    }

    @Test
    void rejectsNegativeRemaining() {
        assertThrows(IllegalArgumentException.class, () -> Decision.allowed(-1));
    }

    @Test
    void rejectsNegativeRetryAfter() {
        assertThrows(IllegalArgumentException.class, () -> Decision.denied(Duration.ofMillis(-1)));
    }

    @Test
    void rejectsNullRetryAfter() {
        assertThrows(NullPointerException.class, () -> Decision.denied(null));
    }

    @Test
    void rejectsAllowedWithRetryAfter() {
        assertThrows(IllegalArgumentException.class, () -> new Decision(true, 1, Duration.ofSeconds(1)));
    }

    @Test
    void rejectsDeniedWithRemaining() {
        assertThrows(IllegalArgumentException.class, () -> new Decision(false, 1, Duration.ofSeconds(1)));
    }
}
