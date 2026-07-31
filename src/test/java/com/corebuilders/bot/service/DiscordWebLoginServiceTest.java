package com.corebuilders.bot.service;

import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

final class DiscordWebLoginServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-31T01:00:00Z");
    private static final byte[] PEPPER = new byte[32];

    @Test
    void createsHmacProtectedChallengeAndDiscordSlashCommand() {
        FakeRepository repository = new FakeRepository();
        DiscordWebLoginService service = service(repository);

        DiscordWebLoginService.StartChallenge result = service.createChallenge();

        assertNotNull(result.challengeToken());
        assertTrue(result.code().matches("[A-Z2-9]{4}-[A-Z2-9]{4}"));
        assertEquals("/core web-login code:" + result.code(), result.command());
        assertEquals(NOW.plus(Duration.ofMinutes(10)), result.expiresAt());
        assertEquals(64, repository.created.browserTokenHash().length());
        assertEquals(64, repository.created.verificationCodeHash().length());
        assertNotEquals(result.challengeToken(), repository.created.browserTokenHash());
    }

    @Test
    void normalizesCodeAndPassesDiscordIdentity() {
        FakeRepository repository = new FakeRepository();
        DiscordWebLoginService service = service(repository);

        service.verifyFromDiscord(
                " abcd-2345 ",
                "123456789012345678",
                "Builder",
                "https://cdn.example/avatar.png"
        );

        assertEquals(service.hash("ABCD2345"), repository.verificationHash);
        assertEquals("123456789012345678", repository.discordUserId);
        assertEquals("Builder", repository.discordUsername);
        assertEquals("https://cdn.example/avatar.png", repository.discordAvatarUrl);
    }

    @Test
    void hashesBrowserTokenAndPassesConfirmationChoice() {
        FakeRepository repository = new FakeRepository();
        DiscordWebLoginService service = service(repository);

        service.complete("browser-secret", true);

        assertEquals(service.hash("browser-secret"), repository.browserHash);
        assertTrue(repository.consume);
    }

    @Test
    void rejectsMalformedCodesAndDiscordIds() {
        DiscordWebLoginService service = service(new FakeRepository());
        assertThrows(IllegalArgumentException.class,
                () -> service.verifyFromDiscord("ABCD-O123", "123456789012345678", "Builder", ""));
        assertThrows(IllegalArgumentException.class,
                () -> service.verifyFromDiscord("ABCD-2345", "not-an-id", "Builder", ""));
    }

    private static DiscordWebLoginService service(FakeRepository repository) {
        return new DiscordWebLoginService(
                repository,
                Duration.ofMinutes(10),
                Clock.fixed(NOW, ZoneOffset.UTC),
                new SecureRandom(new byte[] {1, 2, 3, 4}),
                PEPPER
        );
    }

    private static final class FakeRepository implements DiscordWebLoginChallengeRepository {
        private NewChallenge created;
        private String verificationHash;
        private String discordUserId;
        private String discordUsername;
        private String discordAvatarUrl;
        private String browserHash;
        private boolean consume;

        @Override public void create(NewChallenge challenge) { this.created = challenge; }

        @Override
        public VerificationResult verify(
                String hash,
                String discordUserId,
                String discordUsername,
                String discordAvatarUrl,
                Instant now
        ) {
            this.verificationHash = hash;
            this.discordUserId = discordUserId;
            this.discordUsername = discordUsername;
            this.discordAvatarUrl = discordAvatarUrl;
            return new VerificationResult(VerificationStatus.VERIFIED, UUID.randomUUID());
        }

        @Override public CompletionResult complete(String hash, Instant now, boolean consume) {
            this.browserHash = hash;
            this.consume = consume;
            return new CompletionResult(CompletionStatus.PENDING, null, null, null);
        }
    }
}
