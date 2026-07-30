package com.corebuilders.bot.web.auth;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/** One-time OAuth state values bound to an authenticated website session. */
public final class OAuthStateStore {
    private record State(String binding, Instant expiresAt) {}

    private final Map<String, State> states = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private final Clock clock;
    private final Duration lifetime;

    public OAuthStateStore() {
        this(Clock.systemUTC(), Duration.ofMinutes(10));
    }

    OAuthStateStore(Clock clock, Duration lifetime) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.lifetime = Objects.requireNonNull(lifetime, "lifetime");
    }

    /** Legacy unbound state support retained for source compatibility. */
    public String create() {
        return create("");
    }

    public String create(String binding) {
        cleanup();
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        String value = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        states.put(value, new State(requireBinding(binding), clock.instant().plus(lifetime)));
        return value;
    }

    /** Legacy unbound state support retained for source compatibility. */
    public boolean consume(String state) {
        return consume(state, "");
    }

    public boolean consume(String state, String binding) {
        if (state == null || state.isBlank()) {
            return false;
        }
        State stored = states.remove(state);
        return stored != null
                && stored.expiresAt().isAfter(clock.instant())
                && stored.binding().equals(requireBinding(binding));
    }

    private void cleanup() {
        Instant now = clock.instant();
        states.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
    }

    private static String requireBinding(String binding) {
        return binding == null ? "" : binding;
    }
}
