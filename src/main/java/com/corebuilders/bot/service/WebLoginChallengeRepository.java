package com.corebuilders.bot.service;

import java.time.Instant;
import java.util.UUID;

/**
 * Persistence port for one-time Minecraft verified website login challenges.
 *
 * Only hashes are persisted. The browser token and Minecraft verification code
 * exist in clear text only in the process that generated them and in the clients
 * that need to present them.
 */
public interface WebLoginChallengeRepository {
    void create(NewChallenge challenge);

    VerificationResult verify(
            String verificationCodeHash,
            UUID minecraftUuid,
            String minecraftName,
            Instant now
    );

    CompletionResult complete(String browserTokenHash, Instant now);

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
        COMPLETED,
        INVALID,
        EXPIRED,
        USED,
        INACTIVE
    }

    record CompletionResult(CompletionStatus status, UUID memberId) {}

    final class ChallengeCollisionException extends RuntimeException {
        public ChallengeCollisionException(Throwable cause) {
            super("Generated login challenge collided with an existing challenge.", cause);
        }
    }
}
