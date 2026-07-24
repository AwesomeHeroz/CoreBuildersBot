package com.corebuilders.bot.application;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Small in-memory per-user and global sliding-window limiter for external API requests. */
public final class RequestRateLimiter {
    public record Decision(boolean allowed, long retryAfterSeconds) {
        public static Decision allowedNow() { return new Decision(true, 0); }
    }

    private final long userCooldownMillis;
    private final int globalLimit;
    private final long globalWindowMillis;
    private final Clock clock;
    private final Map<String, Long> nextAllowedByUser = new HashMap<>();
    private final ArrayDeque<Long> globalRequests = new ArrayDeque<>();

    public RequestRateLimiter(Duration userCooldown, int globalLimit, Duration globalWindow) {
        this(userCooldown, globalLimit, globalWindow, Clock.systemUTC());
    }

    RequestRateLimiter(Duration userCooldown, int globalLimit, Duration globalWindow, Clock clock) {
        this.userCooldownMillis = Math.max(0L, Objects.requireNonNull(userCooldown).toMillis());
        this.globalLimit = Math.max(1, globalLimit);
        this.globalWindowMillis = Math.max(1L, Objects.requireNonNull(globalWindow).toMillis());
        this.clock = Objects.requireNonNull(clock);
    }

    public synchronized Decision tryAcquire(String userId) {
        Objects.requireNonNull(userId, "userId");
        long now = clock.millis();
        nextAllowedByUser.entrySet().removeIf(entry -> entry.getValue() <= now);
        long cutoff = now - globalWindowMillis;
        while (!globalRequests.isEmpty() && globalRequests.peekFirst() <= cutoff) {
            globalRequests.removeFirst();
        }

        long userNext = nextAllowedByUser.getOrDefault(userId, 0L);
        if (userNext > now) {
            return denied(userNext - now);
        }
        if (globalRequests.size() >= globalLimit) {
            long retryAt = globalRequests.peekFirst() + globalWindowMillis;
            return denied(Math.max(1L, retryAt - now));
        }

        nextAllowedByUser.put(userId, now + userCooldownMillis);
        globalRequests.addLast(now);
        return Decision.allowedNow();
    }

    private static Decision denied(long retryAfterMillis) {
        return new Decision(false, Math.max(1L, (retryAfterMillis + 999L) / 1000L));
    }
}
