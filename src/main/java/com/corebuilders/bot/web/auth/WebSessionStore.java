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
    public record Session(
            String id,
            SessionPrincipal principal,
            String csrfToken,
            Instant createdAt,
            Instant lastAccessedAt,
            Instant expiresAt
    ) {}

    private final Duration lifetime;
    private final Duration idleLifetime;
    private final Clock clock;
    private final SecureRandom random;
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final AtomicLong accesses = new AtomicLong();

    public WebSessionStore(Duration lifetime) {
        this(lifetime, Duration.ofMinutes(30));
    }

    public WebSessionStore(Duration lifetime, Duration idleLifetime) {
        this(lifetime, idleLifetime, Clock.systemUTC(), new SecureRandom());
    }

    WebSessionStore(Duration lifetime, Duration idleLifetime, Clock clock, SecureRandom random) {
        this.lifetime = positive(lifetime, "Session lifetime");
        this.idleLifetime = positive(idleLifetime, "Idle session lifetime");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.random = Objects.requireNonNull(random, "random");
    }

    public Session create(SessionPrincipal principal) {
        Objects.requireNonNull(principal, "principal");
        cleanupOccasionally();
        Instant now = clock.instant();
        Session session = new Session(token(32), principal, token(24), now, now, now.plus(lifetime));
        sessions.put(session.id(), session);
        return session;
    }

    public Optional<Session> find(String id) {
        cleanupOccasionally();
        if (id == null || id.isBlank()) return Optional.empty();
        Instant now = clock.instant();
        while (true) {
            Session current = sessions.get(id);
            if (current == null) return Optional.empty();
            if (expired(current, now)) {
                sessions.remove(id, current);
                return Optional.empty();
            }
            Session touched = new Session(current.id(), current.principal(), current.csrfToken(),
                    current.createdAt(), now, current.expiresAt());
            if (sessions.replace(id, current, touched)) return Optional.of(touched);
        }
    }

    /** Rotates both the session identifier and CSRF token after an identity change. */
    public Optional<Session> rotate(String id, SessionPrincipal principal) {
        Objects.requireNonNull(principal, "principal");
        Optional<Session> current = find(id);
        if (current.isEmpty()) return Optional.empty();
        if (!sessions.remove(id, current.get())) return Optional.empty();
        return Optional.of(create(principal));
    }

    public void destroy(String id) {
        if (id != null) sessions.remove(id);
    }

    public void clear() {
        sessions.clear();
    }

    private boolean expired(Session session, Instant now) {
        return !session.expiresAt().isAfter(now)
                || !session.lastAccessedAt().plus(idleLifetime).isAfter(now);
    }

    private void cleanupOccasionally() {
        if ((accesses.incrementAndGet() & 127L) != 0L) return;
        Instant now = clock.instant();
        sessions.entrySet().removeIf(entry -> expired(entry.getValue(), now));
    }

    private String token(int bytes) {
        byte[] data = new byte[bytes];
        random.nextBytes(data);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }

    private static Duration positive(Duration value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isZero() || value.isNegative()) throw new IllegalArgumentException(name + " must be positive.");
        return value;
    }
}
