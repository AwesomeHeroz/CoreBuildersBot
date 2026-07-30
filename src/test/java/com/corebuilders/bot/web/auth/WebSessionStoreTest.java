package com.corebuilders.bot.web.auth;

import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.time.*;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class WebSessionStoreTest {
    @Test
    void createsFindsAndDestroysSessions() {
        WebSessionStore store = new WebSessionStore(Duration.ofHours(1), Duration.ofMinutes(30));
        SessionPrincipal principal = new SessionPrincipal(UUID.randomUUID(), "123", "Builder", null, 0L);
        WebSessionStore.Session session = store.create(principal);
        assertEquals(principal, store.find(session.id()).orElseThrow().principal());
        assertFalse(session.csrfToken().isBlank());
        store.destroy(session.id());
        assertTrue(store.find(session.id()).isEmpty());
    }

    @Test
    void expiresAbsoluteAndIdleSessions() {
        Instant now = Instant.parse("2026-07-26T00:00:00Z");
        MutableClock clock = new MutableClock(now);
        WebSessionStore absolute = new WebSessionStore(Duration.ofMinutes(5), Duration.ofHours(1), clock, new SecureRandom());
        WebSessionStore.Session first = absolute.create(new SessionPrincipal(UUID.randomUUID(), "123", "Builder", null, 0L));
        clock.instant = now.plus(Duration.ofMinutes(6));
        assertTrue(absolute.find(first.id()).isEmpty());

        clock.instant = now;
        WebSessionStore idle = new WebSessionStore(Duration.ofHours(1), Duration.ofMinutes(5), clock, new SecureRandom());
        WebSessionStore.Session second = idle.create(new SessionPrincipal(UUID.randomUUID(), "123", "Builder", null, 0L));
        clock.instant = now.plus(Duration.ofMinutes(6));
        assertTrue(idle.find(second.id()).isEmpty());
    }

    private static final class MutableClock extends Clock {
        private Instant instant;
        private MutableClock(Instant instant) { this.instant = instant; }
        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }
}
