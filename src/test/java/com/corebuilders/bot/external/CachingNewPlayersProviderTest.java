package com.corebuilders.bot.external;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CachingNewPlayersProviderTest {
    @Test
    void cachesIdenticalRequestsUntilTtlExpires() {
        MutableClock clock = new MutableClock();
        AtomicInteger calls = new AtomicInteger();
        NewPlayersProvider delegate = (server, page, size) -> {
            calls.incrementAndGet();
            return new NewPlayersResponse(page, size, 0, 0, List.of());
        };
        var cache = new CachingNewPlayersProvider(delegate, Duration.ofSeconds(30), clock);

        cache.fetchNewPlayers("2b2t", 1, 5);
        cache.fetchNewPlayers("2B2T", 1, 5);
        assertEquals(1, calls.get());

        clock.advance(Duration.ofSeconds(31));
        cache.fetchNewPlayers("2b2t", 1, 5);
        assertEquals(2, calls.get());
    }

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-07-23T00:00:00Z");
        void advance(Duration duration) { now = now.plus(duration); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
