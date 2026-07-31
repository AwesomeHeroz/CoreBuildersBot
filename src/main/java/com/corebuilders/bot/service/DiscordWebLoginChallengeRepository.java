package com.corebuilders.bot.service;

import java.time.Instant;
import java.util.UUID;

/** Persistence port for one-time website login challenges verified by the Discord bot. */
public interface DiscordWebLoginChallengeRepository {
    void create(NewChallenge challenge);

    VerificationResult verify(
            String verificationCodeHash,
            String discordUserId,
            String discordUsername,
            String discordAvatarUrl,
            Instant now
    );

    /**
     * Reads challenge state. When consume is false, a verified challenge is returned as READY.
     * When consume is true, the same challenge is atomically consumed and returned as COMPLETED.
     */
    CompletionResult complete(String browserTokenHash, Instant now, boolean consume);

    record NewChallenge(
            UUID id,
            String browserTokenHash,
            String verificationCodeHash,
            Instant expiresAt,
            Instant createdAt
    ) {}

    enum VerificationStatus {
        VERIFIED,
        ALREADY_VERIFIED,
        INVALID,
        EXPIRED,
        USED,
        INACTIVE
    }

    record VerificationResult(VerificationStatus status, UUID memberId) {}

    enum CompletionStatus {
        PENDING,
        READY,
        COMPLETED,
        INVALID,
        EXPIRED,
        USED,
        INACTIVE
    }

    record CompletionResult(
            CompletionStatus status,
            UUID memberId,
            String discordUsername,
            String discordAvatarUrl
    ) {}

    final class ChallengeCollisionException extends RuntimeException {
        public ChallengeCollisionException(Throwable cause) {
            super("Generated Discord login challenge collided with an existing challenge.", cause);
        }
    }
}
