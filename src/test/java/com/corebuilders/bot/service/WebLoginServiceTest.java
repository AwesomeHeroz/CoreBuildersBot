package com.corebuilders.bot.service;

import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

final class WebLoginServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-29T12:00:00Z");
    private static final byte[] PEPPER = new byte[32];

    @Test
    void createsHmacProtectedChallengeAndMinecraftCommand() {
        FakeRepository repository = new FakeRepository();
        WebLoginService service = service(repository);

        WebLoginService.StartChallenge result = service.createChallenge();

        assertNotNull(result.challengeToken());
        assertTrue(result.code().matches("[A-Z2-9]{4}-[A-Z2-9]{4}"));
        assertEquals("/core login " + result.code(), result.command());
        assertEquals(NOW.plus(Duration.ofMinutes(10)), result.expiresAt());
        assertEquals(64, repository.created.browserTokenHash().length());
        assertEquals(64, repository.created.verificationCodeHash().length());
        assertNotEquals(result.challengeToken(), repository.created.browserTokenHash());
    }

    @Test
    void normalizesDisplayedCodeBeforeVerification() {
        FakeRepository repository = new FakeRepository();
        WebLoginService service = service(repository);
        UUID player = UUID.randomUUID();

        service.verifyFromGame(" abcd-2345 ", player, "Player");

        assertEquals(service.hash("ABCD2345"), repository.verificationHash);
        assertEquals(player, repository.minecraftUuid);
        assertEquals("Player", repository.minecraftName);
    }

    @Test
    void hashesBrowserTokenAndPassesConfirmationChoice() {
        FakeRepository repository = new FakeRepository();
        WebLoginService service = service(repository);

        service.complete("browser-secret", true);

        assertEquals(service.hash("browser-secret"), repository.browserHash);
        assertTrue(repository.consume);
    }

    @Test
    void rejectsAmbiguousOrMalformedCodes() {
        WebLoginService service = service(new FakeRepository());
        assertThrows(IllegalArgumentException.class,
                () -> service.verifyFromGame("ABCD-O123", UUID.randomUUID(), "Player"));
        assertThrows(IllegalArgumentException.class,
                () -> service.verifyFromGame("ABC", UUID.randomUUID(), "Player"));
    }

    private static WebLoginService service(FakeRepository repository) {
        return new WebLoginService(repository, Duration.ofMinutes(10),
                Clock.fixed(NOW, ZoneOffset.UTC), new SecureRandom(new byte[] {1, 2, 3, 4}), PEPPER);
    }

    private static final class FakeRepository implements WebLoginChallengeRepository {
        private NewChallenge created;
        private String verificationHash;
        private UUID minecraftUuid;
        private String minecraftName;
        private String browserHash;
        private boolean consume;

        @Override public void create(NewChallenge challenge) { this.created = challenge; }
        @Override public VerificationResult verify(String hash, UUID uuid, String name, Instant now) {
            verificationHash = hash; minecraftUuid = uuid; minecraftName = name;
            return new VerificationResult(VerificationStatus.VERIFIED, UUID.randomUUID());
        }
        @Override public CompletionResult complete(String hash, Instant now, boolean consume) {
            browserHash = hash; this.consume = consume;
            return new CompletionResult(CompletionStatus.PENDING, null, null);
        }
    }
}
