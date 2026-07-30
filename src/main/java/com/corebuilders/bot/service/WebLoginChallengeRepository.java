package com.corebuilders.bot.service;

import java.time.Instant;
import java.util.UUID;

/** Persistence port for one-time Minecraft-verified website login challenges. */
public interface WebLoginChallengeRepository {
    void create(NewChallenge challenge);

    VerificationResult verify(
            String verificationCodeHash,
            UUID minecraftUuid,
            String minecraftName,
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

    record CompletionResult(CompletionStatus status, UUID memberId, String minecraftName) {}

    final class ChallengeCollisionException extends RuntimeException {
        public ChallengeCollisionException(Throwable cause) {
            super("Generated login challenge collided with an existing challenge.", cause);
        }
    }
}
