package com.corebuilders.bot.web.auth;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** In-memory, expiring browser sessions. A plugin restart intentionally signs all users out. */
public final class WebSessionStore {
    public record Session(String id, SessionPrincipal principal, String csrfToken, Instant expiresAt) {}

    private final Duration lifetime;
    private final Clock clock;
    private final SecureRandom random;
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final AtomicLong accesses = new AtomicLong();

    public WebSessionStore(Duration lifetime) {
        this(lifetime, Clock.systemUTC(), new SecureRandom());
    }

    WebSessionStore(Duration lifetime, Clock clock, SecureRandom random) {
        this.lifetime = Objects.requireNonNull(lifetime, "lifetime");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.random = Objects.requireNonNull(random, "random");
        if (lifetime.isZero() || lifetime.isNegative()) {
            throw new IllegalArgumentException("Session lifetime must be positive.");
        }
    }

    public Session create(SessionPrincipal principal) {
        Objects.requireNonNull(principal, "principal");
        cleanupOccasionally();
        Session session = new Session(token(32), principal, token(24), clock.instant().plus(lifetime));
        sessions.put(session.id(), session);
        return session;
    }

    public Optional<Session> find(String id) {
        cleanupOccasionally();
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        Session session = sessions.get(id);
        if (session == null) {
            return Optional.empty();
        }
        if (!session.expiresAt().isAfter(clock.instant())) {
            sessions.remove(id, session);
            return Optional.empty();
        }
        return Optional.of(session);
    }

    public Optional<Session> replacePrincipal(String id, SessionPrincipal principal) {
        Objects.requireNonNull(principal, "principal");
        return find(id).map(current -> {
            Session updated = new Session(current.id(), principal, current.csrfToken(), current.expiresAt());
            sessions.replace(current.id(), current, updated);
            return sessions.getOrDefault(current.id(), updated);
        });
    }

    public void destroy(String id) {
        if (id != null) {
            sessions.remove(id);
        }
    }

    public void clear() {
        sessions.clear();
    }

    private void cleanupOccasionally() {
        if ((accesses.incrementAndGet() & 127L) != 0L) {
            return;
        }
        Instant now = clock.instant();
        sessions.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
    }

    private String token(int bytes) {
        byte[] data = new byte[bytes];
        random.nextBytes(data);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }
}
