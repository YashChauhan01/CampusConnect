package edu.campusconnect.common;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import edu.campusconnect.config.AppProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class RateLimiterTest {

    /** Clock whose time can be advanced by tests. */
    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-01-01T00:00:00Z");

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private static AppProperties props(boolean enabled) {
        return new AppProperties("http://x", "s".repeat(32), false, List.of("college.edu"), List.of(), 30, 30, 15, 7, 15, 4,
                new AppProperties.Mail("log", "a@b.c"), new AppProperties.RateLimit(enabled));
    }

    @Test
    void blocksAfterLimitAndReportsRetryAfter() {
        MutableClock clock = new MutableClock();
        RateLimiter limiter = new RateLimiter(clock, props(true));

        for (int i = 0; i < 3; i++) {
            limiter.check("k", 3, Duration.ofMinutes(1));
        }
        ApiException e = assertThrows(ApiException.class, () -> limiter.check("k", 3, Duration.ofMinutes(1)));
        assertEquals(429, e.status().value());
        assertEquals(60, e.retryAfterSeconds());
    }

    @Test
    void windowResetsAndKeysAreIndependent() {
        MutableClock clock = new MutableClock();
        RateLimiter limiter = new RateLimiter(clock, props(true));

        limiter.check("a", 1, Duration.ofMinutes(1));
        assertDoesNotThrow(() -> limiter.check("b", 1, Duration.ofMinutes(1)));
        assertThrows(ApiException.class, () -> limiter.check("a", 1, Duration.ofMinutes(1)));

        clock.advance(Duration.ofMinutes(1));
        assertDoesNotThrow(() -> limiter.check("a", 1, Duration.ofMinutes(1)));
    }

    @Test
    void disabledLimiterNeverBlocks() {
        RateLimiter limiter = new RateLimiter(new MutableClock(), props(false));
        assertDoesNotThrow(() -> {
            for (int i = 0; i < 100; i++) {
                limiter.check("k", 1, Duration.ofMinutes(1));
            }
        });
    }
}
