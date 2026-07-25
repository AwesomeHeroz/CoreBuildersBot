package com.corebuilders.bot.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe store for short-lived, multi-page application sessions.
 * Session values are generic so lifecycle policy stays independent of Discord/JDA.
 */
public final class ApplicationSessionStore<V> {
    private final Clock clock;
    private final Map<UUID, Session<V>> sessions = new ConcurrentHashMap<>();
    private final Map<String, UUID> activeSessionByUser = new ConcurrentHashMap<>();

    public ApplicationSessionStore() {
        this(Clock.systemUTC());
    }

    ApplicationSessionStore(Clock clock) {
        this.clock = clock;
    }

    public synchronized Session<V> create(String userId, String username, Duration lifetime) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId cannot be blank.");
        }
        if (lifetime == null || lifetime.isZero() || lifetime.isNegative()) {
            throw new IllegalArgumentException("Session lifetime must be positive.");
        }

        removeForUser(userId);
        UUID id = UUID.randomUUID();
        Session<V> session = new Session<>(id, userId, username, clock.instant().plus(lifetime));
        sessions.put(id, session);
        activeSessionByUser.put(userId, id);
        return session;
    }

    public Optional<Session<V>> findValid(UUID sessionId, String userId) {
        Session<V> session = sessions.get(sessionId);
        if (session == null) {
            return Optional.empty();
        }
        if (!session.userId().equals(userId)) {
            return Optional.empty();
        }
        if (isExpired(session)) {
            remove(session);
            return Optional.empty();
        }
        return Optional.of(session);
    }

    public Optional<Session<V>> findForUser(String userId) {
        UUID id = activeSessionByUser.get(userId);
        return id == null ? Optional.empty() : findValid(id, userId);
    }

    public synchronized void remove(Session<V> session) {
        if (session == null) return;
        sessions.remove(session.id(), session);
        activeSessionByUser.remove(session.userId(), session.id());
    }

    public synchronized void removeForUser(String userId) {
        UUID id = activeSessionByUser.remove(userId);
        if (id != null) {
            sessions.remove(id);
        }
    }

    public synchronized boolean removeIfValid(UUID sessionId, String userId) {
        if (sessionId == null || userId == null || userId.isBlank()) {
            return false;
        }

        Session<V> session = sessions.get(sessionId);
        if (session == null || !session.userId().equals(userId)) {
            return false;
        }

        boolean valid = !isExpired(session);
        remove(session);
        return valid;
    }

    public synchronized int cleanupExpired() {
        int before = sessions.size();
        sessions.values().stream().filter(this::isExpired).toList().forEach(this::remove);
        return before - sessions.size();
    }

    public int size() {
        return sessions.size();
    }

    public synchronized void clear() {
        sessions.clear();
        activeSessionByUser.clear();
    }

    private boolean isExpired(Session<V> session) {
        return !clock.instant().isBefore(session.expiresAt());
    }

    public static final class Session<V> {
        private final UUID id;
        private final String userId;
        private final String username;
        private final Instant expiresAt;
        private final Map<String, V> values = new ConcurrentHashMap<>();

        private Session(UUID id, String userId, String username, Instant expiresAt) {
            this.id = id;
            this.userId = userId;
            this.username = username == null ? "" : username;
            this.expiresAt = expiresAt;
        }

        public UUID id() { return id; }
        public String userId() { return userId; }
        public String username() { return username; }
        public Instant expiresAt() { return expiresAt; }
        public Map<String, V> values() { return values; }
    }
}
