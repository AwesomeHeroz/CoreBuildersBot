package com.corebuilders.bot.web.auth;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

final class OAuthStateStoreBindingTest {
    @Test
    void stateIsBoundToTheWebsiteSessionAndCanBeConsumedOnce() {
        OAuthStateStore store = new OAuthStateStore(
                Clock.fixed(Instant.parse("2026-07-29T12:00:00Z"), ZoneOffset.UTC),
                Duration.ofMinutes(10)
        );

        String state = store.create("session-a");

        assertFalse(store.consume(state, "session-b"));
        assertFalse(store.consume(state, "session-a"), "A failed binding attempt still consumes the one-time state");

        String second = store.create("session-a");
        assertTrue(store.consume(second, "session-a"));
        assertFalse(store.consume(second, "session-a"));
    }
}
