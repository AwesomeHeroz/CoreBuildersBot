package com.corebuilders.bot.web.auth;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** One-time OAuth state values used to prevent login CSRF. */
public final class OAuthStateStore {
    private final Map<String, Instant> states = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private final Clock clock;
    private final Duration lifetime;

    public OAuthStateStore() {
        this(Clock.systemUTC(), Duration.ofMinutes(10));
    }

    OAuthStateStore(Clock clock, Duration lifetime) {
        this.clock = clock;
        this.lifetime = lifetime;
    }

    public String create() {
        cleanup();
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        String state = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        states.put(state, clock.instant().plus(lifetime));
        return state;
    }

    public boolean consume(String state) {
        if (state == null || state.isBlank()) return false;
        Optional<Instant> expiry = Optional.ofNullable(states.remove(state));
        return expiry.filter(value -> value.isAfter(clock.instant())).isPresent();
    }

    private void cleanup() {
        Instant now = clock.instant();
        states.entrySet().removeIf(entry -> !entry.getValue().isAfter(now));
    }
}
