package com.corebuilders.bot.application;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class RequestRateLimiterTest {
    @Test
    void enforcesPerUserCooldown() {
        MutableClock clock = new MutableClock();
        RequestRateLimiter limiter = new RequestRateLimiter(
                Duration.ofSeconds(10), 20, Duration.ofMinutes(1), clock
        );

        assertTrue(limiter.tryAcquire("a").allowed());
        var denied = limiter.tryAcquire("a");
        assertFalse(denied.allowed());
        assertEquals(10, denied.retryAfterSeconds());

        clock.advance(Duration.ofSeconds(10));
        assertTrue(limiter.tryAcquire("a").allowed());
    }

    @Test
    void enforcesGlobalWindowAcrossDifferentUsers() {
        MutableClock clock = new MutableClock();
        RequestRateLimiter limiter = new RequestRateLimiter(
                Duration.ZERO, 2, Duration.ofMinutes(1), clock
        );

        assertTrue(limiter.tryAcquire("a").allowed());
        assertTrue(limiter.tryAcquire("b").allowed());
        assertFalse(limiter.tryAcquire("c").allowed());

        clock.advance(Duration.ofMinutes(1).plusMillis(1));
        assertTrue(limiter.tryAcquire("c").allowed());
    }

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-07-23T00:00:00Z");
        void advance(Duration duration) { now = now.plus(duration); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
