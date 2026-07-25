package com.corebuilders.bot.application;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class ApplicationSessionStoreTest {
    @Test
    void keepsOnlyOneActiveSessionPerUser() {
        ApplicationSessionStore<String> store = new ApplicationSessionStore<>();

        var first = store.create("1", "Player", Duration.ofMinutes(10));
        var second = store.create("1", "Player", Duration.ofMinutes(10));

        assertTrue(store.findValid(first.id(), "1").isEmpty());
        assertEquals(second.id(), store.findForUser("1").orElseThrow().id());
        assertEquals(1, store.size());
    }

    @Test
    void anotherUserCannotInvalidateAValidSession() {
        ApplicationSessionStore<String> store = new ApplicationSessionStore<>();
        var session = store.create("1", "Player", Duration.ofMinutes(10));

        assertTrue(store.findValid(session.id(), "2").isEmpty());
        assertEquals(1, store.size());
        assertTrue(store.findValid(session.id(), "1").isPresent());
    }

    @Test
    void expiresAndCleansSessionsUsingInjectedClock() {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-23T00:00:00Z"));
        ApplicationSessionStore<String> store = new ApplicationSessionStore<>(clock);
        var session = store.create("1", "Player", Duration.ofMinutes(10));

        clock.advance(Duration.ofMinutes(11));

        assertTrue(store.findValid(session.id(), "1").isEmpty());
        assertEquals(0, store.size());
    }


    @Test
    void startingAgainReplacesSessionLeftByNativeModalCancel() {
        ApplicationSessionStore<String> store = new ApplicationSessionStore<>();

        var abandoned = store.create("1", "Player", Duration.ofMinutes(10));
        abandoned.values().put("question", "partial answer");

        var restarted = store.create("1", "Player", Duration.ofMinutes(10));

        assertNotEquals(abandoned.id(), restarted.id());
        assertTrue(store.findValid(abandoned.id(), "1").isEmpty());
        assertEquals(restarted.id(), store.findForUser("1").orElseThrow().id());
        assertTrue(restarted.values().isEmpty());
        assertEquals(1, store.size());
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
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
            return instant;
        }
    }
    @Test
    void concurrentCreatesLeaveOnlyOneActiveSessionPerUser() throws Exception {
        ApplicationSessionStore<String> store = new ApplicationSessionStore<>();
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(8);
        try {
            java.util.List<java.util.concurrent.Future<?>> futures = new java.util.ArrayList<>();
            for (int i = 0; i < 20; i++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    store.create("user", "name", Duration.ofMinutes(5));
                    return null;
                }));
            }
            start.countDown();
            for (var future : futures) future.get();

            assertEquals(1, store.size());
            assertTrue(store.findForUser("user").isPresent());
        } finally {
            executor.shutdownNow();
        }
    }

}
