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

    @Test
    void createsHashedChallengeAndMinecraftCommand() {
        FakeRepository repository = new FakeRepository();
        WebLoginService service = service(repository);

        WebLoginService.StartChallenge result = service.createChallenge();

        assertNotNull(result.challengeToken());
        assertTrue(result.code().matches("[A-Z2-9]{4}-[A-Z2-9]{4}"));
        assertEquals("/core login " + result.code(), result.command());
        assertEquals(NOW.plus(Duration.ofMinutes(10)), result.expiresAt());
        assertNotNull(repository.created);
        assertEquals(64, repository.created.browserTokenHash().length());
        assertEquals(64, repository.created.verificationCodeHash().length());
        assertNotEquals(result.challengeToken(), repository.created.browserTokenHash());
        assertNotEquals(result.code().replace("-", ""), repository.created.verificationCodeHash());
    }

    @Test
    void normalizesDisplayedCodeBeforeVerification() {
        FakeRepository repository = new FakeRepository();
        WebLoginService service = service(repository);
        UUID player = UUID.randomUUID();

        service.verifyFromGame(" abcd-2345 ", player, "Player");

        assertEquals(WebLoginService.sha256("ABCD2345"), repository.verificationHash);
        assertEquals(player, repository.minecraftUuid);
        assertEquals("Player", repository.minecraftName);
    }

    @Test
    void hashesBrowserTokenBeforeCompletion() {
        FakeRepository repository = new FakeRepository();
        WebLoginService service = service(repository);

        service.complete("browser-secret");

        assertEquals(WebLoginService.sha256("browser-secret"), repository.browserHash);
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
        return new WebLoginService(
                repository,
                Duration.ofMinutes(10),
                Clock.fixed(NOW, ZoneOffset.UTC),
                new SecureRandom(new byte[] {1, 2, 3, 4})
        );
    }

    private static final class FakeRepository implements WebLoginChallengeRepository {
        private NewChallenge created;
        private String verificationHash;
        private UUID minecraftUuid;
        private String minecraftName;
        private String browserHash;

        @Override
        public void create(NewChallenge challenge) {
            this.created = challenge;
        }

        @Override
        public VerificationResult verify(
                String verificationCodeHash,
                UUID minecraftUuid,
                String minecraftName,
                Instant now
        ) {
            this.verificationHash = verificationCodeHash;
            this.minecraftUuid = minecraftUuid;
            this.minecraftName = minecraftName;
            return new VerificationResult(VerificationStatus.VERIFIED, UUID.randomUUID());
        }

        @Override
        public CompletionResult complete(String browserTokenHash, Instant now) {
            this.browserHash = browserTokenHash;
            return new CompletionResult(CompletionStatus.PENDING, null);
        }
    }
}
