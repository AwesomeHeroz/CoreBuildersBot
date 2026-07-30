package com.corebuilders.bot.service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

import static com.corebuilders.bot.service.WebLoginChallengeRepository.*;

/** Framework-free application service for website login verified from Minecraft. */
public final class WebLoginService {
    private static final char[] CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final int CODE_LENGTH = 8;
    private static final int MAX_CREATE_ATTEMPTS = 10;

    private final WebLoginChallengeRepository repository;
    private final Duration challengeLifetime;
    private final Clock clock;
    private final SecureRandom random;
    private final byte[] hashPepper;

    public WebLoginService(WebLoginChallengeRepository repository, Duration challengeLifetime) {
        this(repository, challengeLifetime, Clock.systemUTC(), new SecureRandom(), randomPepper());
    }

    WebLoginService(WebLoginChallengeRepository repository, Duration challengeLifetime,
                    Clock clock, SecureRandom random, byte[] hashPepper) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.challengeLifetime = Objects.requireNonNull(challengeLifetime, "challengeLifetime");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.random = Objects.requireNonNull(random, "random");
        this.hashPepper = Objects.requireNonNull(hashPepper, "hashPepper").clone();
        if (challengeLifetime.isZero() || challengeLifetime.isNegative()) {
            throw new IllegalArgumentException("Challenge lifetime must be positive.");
        }
        if (this.hashPepper.length < 32) throw new IllegalArgumentException("Login hash pepper must be at least 32 bytes.");
    }

    public static WebLoginService disabled() {
        WebLoginChallengeRepository unavailable = new WebLoginChallengeRepository() {
            @Override public void create(NewChallenge challenge) {
                throw new IllegalStateException("Minecraft website login is not configured.");
            }
            @Override public VerificationResult verify(String hash, UUID uuid, String name, Instant now) {
                return new VerificationResult(VerificationStatus.INVALID, null);
            }
            @Override public CompletionResult complete(String hash, Instant now, boolean consume) {
                return new CompletionResult(CompletionStatus.INVALID, null, null);
            }
        };
        return new WebLoginService(unavailable, Duration.ofMinutes(10));
    }

    public StartChallenge createChallenge() {
        Instant createdAt = clock.instant();
        Instant expiresAt = createdAt.plus(challengeLifetime);
        RuntimeException lastCollision = null;
        for (int attempt = 0; attempt < MAX_CREATE_ATTEMPTS; attempt++) {
            String compactCode = randomCode();
            String displayCode = compactCode.substring(0, 4) + "-" + compactCode.substring(4);
            String browserToken = randomToken(32);
            try {
                repository.create(new NewChallenge(UUID.randomUUID(), hash(browserToken), hash(compactCode),
                        expiresAt, createdAt));
                return new StartChallenge(browserToken, displayCode, "/core login " + displayCode, expiresAt);
            } catch (ChallengeCollisionException collision) {
                lastCollision = collision;
            }
        }
        throw new IllegalStateException("Could not generate a unique login challenge. Try again.", lastCollision);
    }

    public VerificationResult verifyFromGame(String rawCode, UUID minecraftUuid, String minecraftName) {
        Objects.requireNonNull(minecraftUuid, "minecraftUuid");
        return repository.verify(hash(normalizeCode(rawCode)), minecraftUuid,
                safeMinecraftName(minecraftName), clock.instant());
    }

    public CompletionResult complete(String rawBrowserToken, boolean confirm) {
        return repository.complete(hash(requireBrowserToken(rawBrowserToken)), clock.instant(), confirm);
    }

    static String normalizeCode(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("A website login code is required.");
        String normalized = value.replace("-", "").replace(" ", "").trim().toUpperCase(Locale.ROOT);
        if (normalized.length() != CODE_LENGTH) {
            throw new IllegalArgumentException("The website login code must contain 8 characters.");
        }
        for (int i = 0; i < normalized.length(); i++) {
            boolean accepted = false;
            for (char allowed : CODE_ALPHABET) if (normalized.charAt(i) == allowed) { accepted = true; break; }
            if (!accepted) throw new IllegalArgumentException("The website login code contains an invalid character.");
        }
        return normalized;
    }

    String hash(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hashPepper, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException impossible) {
            throw new IllegalStateException("HMAC-SHA-256 is not available.", impossible);
        }
    }

    private String randomCode() {
        StringBuilder result = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) result.append(CODE_ALPHABET[random.nextInt(CODE_ALPHABET.length)]);
        return result.toString();
    }

    private String randomToken(int bytes) {
        byte[] value = new byte[bytes];
        random.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private static byte[] randomPepper() {
        byte[] value = new byte[32];
        new SecureRandom().nextBytes(value);
        return value;
    }

    private static String requireBrowserToken(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("A login challenge token is required.");
        String trimmed = value.trim();
        if (trimmed.length() > 200) throw new IllegalArgumentException("The login challenge token is invalid.");
        return trimmed;
    }

    private static String safeMinecraftName(String value) {
        if (value == null || value.isBlank()) return "Unknown";
        String trimmed = value.trim();
        return trimmed.length() <= 100 ? trimmed : trimmed.substring(0, 100);
    }

    public record StartChallenge(String challengeToken, String code, String command, Instant expiresAt) {}
}
