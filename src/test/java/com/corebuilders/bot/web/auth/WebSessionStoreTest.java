package com.corebuilders.bot.web.auth;

import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class WebSessionStoreTest {
    @Test
    void createsFindsAndDestroysSessions() {
        WebSessionStore store = new WebSessionStore(Duration.ofHours(1));
        SessionPrincipal principal = new SessionPrincipal(UUID.randomUUID(), "123", "Builder", "https://example.com/a.png");
        WebSessionStore.Session session = store.create(principal);

        assertEquals(principal, store.find(session.id()).orElseThrow().principal());
        assertFalse(session.csrfToken().isBlank());
        store.destroy(session.id());
        assertTrue(store.find(session.id()).isEmpty());
    }

    @Test
    void expiresSessions() {
        Instant now = Instant.parse("2026-07-26T00:00:00Z");
        MutableClock clock = new MutableClock(now);
        WebSessionStore store = new WebSessionStore(Duration.ofMinutes(5), clock, new SecureRandom());
        WebSessionStore.Session session = store.create(new SessionPrincipal(UUID.randomUUID(), "123", "Builder", null));

        clock.instant = now.plus(Duration.ofMinutes(6));
        assertTrue(store.find(session.id()).isEmpty());
    }

    private static final class MutableClock extends Clock {
        private Instant instant;
        private MutableClock(Instant instant) { this.instant = instant; }
        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }
}
