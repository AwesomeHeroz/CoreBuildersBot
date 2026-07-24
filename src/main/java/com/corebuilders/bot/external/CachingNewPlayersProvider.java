package com.corebuilders.bot.external;

import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Short-lived cache that protects the upstream player API from repeated identical requests. */
public final class CachingNewPlayersProvider implements NewPlayersProvider {
    private final NewPlayersProvider delegate;
    private final long ttlMillis;
    private final Clock clock;
    private final Map<Key, Entry> cache = new HashMap<>();

    public CachingNewPlayersProvider(NewPlayersProvider delegate, Duration ttl) {
        this(delegate, ttl, Clock.systemUTC());
    }

    CachingNewPlayersProvider(NewPlayersProvider delegate, Duration ttl, Clock clock) {
        this.delegate = Objects.requireNonNull(delegate);
        this.ttlMillis = Math.max(0L, Objects.requireNonNull(ttl).toMillis());
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public synchronized NewPlayersResponse fetchNewPlayers(String server, int page, int size) {
        if (ttlMillis == 0L) return delegate.fetchNewPlayers(server, page, size);
        long now = clock.millis();
        Key key = new Key(server == null ? "" : server.trim().toLowerCase(Locale.ROOT), page, size);
        Entry current = cache.get(key);
        if (current != null && current.expiresAtMillis > now) return current.response;

        NewPlayersResponse response = delegate.fetchNewPlayers(server, page, size);
        cache.put(key, new Entry(response, now + ttlMillis));
        cache.entrySet().removeIf(entry -> entry.getValue().expiresAtMillis <= now);
        return response;
    }

    private record Key(String server, int page, int size) {}
    private record Entry(NewPlayersResponse response, long expiresAtMillis) {}
}
