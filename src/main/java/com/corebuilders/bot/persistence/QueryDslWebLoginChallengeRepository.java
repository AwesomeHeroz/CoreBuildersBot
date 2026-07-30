package com.corebuilders.bot.persistence;

import com.corebuilders.bot.db.QueryDslDatabase;
import com.corebuilders.bot.service.WebLoginChallengeRepository;
import com.querydsl.core.Tuple;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import static com.corebuilders.bot.db.DbValues.*;
import static com.corebuilders.bot.db.Schema.MEMBERS;
import static com.corebuilders.bot.db.Schema.WEB_LOGIN_CHALLENGES;

/**
 * QueryDSL persistence adapter for Minecraft-verified website logins.
 */
public final class QueryDslWebLoginChallengeRepository implements WebLoginChallengeRepository {
    private final QueryDslDatabase database;

    public QueryDslWebLoginChallengeRepository(QueryDslDatabase database) {
        this.database = Objects.requireNonNull(database, "database");
    }

    @Override
    public void create(NewChallenge challenge) {
        Objects.requireNonNull(challenge, "challenge");
        try {
            database.inTransaction(() -> {
                Instant staleBefore = challenge.createdAt().minusSeconds(86_400);
                database.query(q -> q.delete(WEB_LOGIN_CHALLENGES)
                        .where(WEB_LOGIN_CHALLENGES.expiresAt.lt(time(challenge.createdAt()))
                                .and(WEB_LOGIN_CHALLENGES.createdAt.lt(time(staleBefore)))
                                .or(WEB_LOGIN_CHALLENGES.consumedAt.isNotNull()
                                        .and(WEB_LOGIN_CHALLENGES.consumedAt.lt(time(staleBefore)))))
                        .execute());
                database.query(q -> q.insert(WEB_LOGIN_CHALLENGES)
                        .set(WEB_LOGIN_CHALLENGES.id, uuid(challenge.id()))
                        .set(WEB_LOGIN_CHALLENGES.browserTokenHash, challenge.browserTokenHash())
                        .set(WEB_LOGIN_CHALLENGES.verificationCodeHash, challenge.verificationCodeHash())
                        .set(WEB_LOGIN_CHALLENGES.expiresAt, time(challenge.expiresAt()))
                        .set(WEB_LOGIN_CHALLENGES.createdAt, time(challenge.createdAt()))
                        .execute());
            });
        } catch (RuntimeException error) {
            if (database.isDuplicateKey(error)) {
                throw new ChallengeCollisionException(error);
            }
            throw error;
        }
    }

    @Override
    public VerificationResult verify(
            String verificationCodeHash,
            UUID minecraftUuid,
            String minecraftName,
            Instant now
    ) {
        return database.inTransaction(() -> {
            Tuple challenge = database.query(q -> q.select(
                            WEB_LOGIN_CHALLENGES.id,
                            WEB_LOGIN_CHALLENGES.memberId,
                            WEB_LOGIN_CHALLENGES.expiresAt,
                            WEB_LOGIN_CHALLENGES.verifiedAt,
                            WEB_LOGIN_CHALLENGES.consumedAt)
                    .from(WEB_LOGIN_CHALLENGES)
                    .where(WEB_LOGIN_CHALLENGES.verificationCodeHash.eq(verificationCodeHash))
                    .forUpdate()
                    .fetchOne());
            if (challenge == null) {
                return new VerificationResult(VerificationStatus.INVALID, null);
            }

            String challengeId = challenge.get(WEB_LOGIN_CHALLENGES.id);
            Instant expiresAt = instant(challenge.get(WEB_LOGIN_CHALLENGES.expiresAt));
            if (challenge.get(WEB_LOGIN_CHALLENGES.consumedAt) != null) {
                return new VerificationResult(
                        VerificationStatus.USED,
                        uuid(challenge.get(WEB_LOGIN_CHALLENGES.memberId))
                );
            }
            if (expiresAt == null || !expiresAt.isAfter(now)) {
                return new VerificationResult(VerificationStatus.EXPIRED, null);
            }
            if (challenge.get(WEB_LOGIN_CHALLENGES.verifiedAt) != null) {
                return new VerificationResult(
                        VerificationStatus.ALREADY_VERIFIED,
                        uuid(challenge.get(WEB_LOGIN_CHALLENGES.memberId))
                );
            }

            String minecraftUuidText = uuid(minecraftUuid);
            Tuple member = database.query(q -> q.select(MEMBERS.id, MEMBERS.active)
                    .from(MEMBERS)
                    .where(MEMBERS.minecraftUuid.eq(minecraftUuidText))
                    .forUpdate()
                    .fetchOne());

            String memberId;
            if (member == null) {
                memberId = UUID.randomUUID().toString();
                database.query(q -> q.insert(MEMBERS)
                        .set(MEMBERS.id, memberId)
                        .setNull(MEMBERS.discordUserId)
                        .set(MEMBERS.username, minecraftName)
                        .set(MEMBERS.reputation, "UNVERIFIED")
                        .setNull(MEMBERS.primaryRole)
                        .set(MEMBERS.active, true)
                        .set(MEMBERS.minecraftUuid, minecraftUuidText)
                        .set(MEMBERS.minecraftName, minecraftName)
                        .set(MEMBERS.minecraftLoginProvisional, true)
                        .set(MEMBERS.createdAt, time(now))
                        .set(MEMBERS.updatedAt, time(now))
                        .execute());
            } else {
                memberId = member.get(MEMBERS.id);
                if (!Boolean.TRUE.equals(member.get(MEMBERS.active))) {
                    return new VerificationResult(VerificationStatus.INACTIVE, uuid(memberId));
                }
                database.query(q -> q.update(MEMBERS)
                        .set(MEMBERS.minecraftName, minecraftName)
                        .set(MEMBERS.updatedAt, time(now))
                        .where(MEMBERS.id.eq(memberId))
                        .execute());
            }

            database.query(q -> q.update(WEB_LOGIN_CHALLENGES)
                    .set(WEB_LOGIN_CHALLENGES.memberId, memberId)
                    .set(WEB_LOGIN_CHALLENGES.minecraftUuid, minecraftUuidText)
                    .set(WEB_LOGIN_CHALLENGES.minecraftName, minecraftName)
                    .set(WEB_LOGIN_CHALLENGES.verifiedAt, time(now))
                    .where(WEB_LOGIN_CHALLENGES.id.eq(challengeId),
                            WEB_LOGIN_CHALLENGES.verifiedAt.isNull(),
                            WEB_LOGIN_CHALLENGES.consumedAt.isNull())
                    .execute());
            return new VerificationResult(VerificationStatus.VERIFIED, uuid(memberId));
        });
    }

    @Override
    public CompletionResult complete(String browserTokenHash, Instant now) {
        return database.inTransaction(() -> {
            Tuple challenge = database.query(q -> q.select(
                            WEB_LOGIN_CHALLENGES.id,
                            WEB_LOGIN_CHALLENGES.memberId,
                            WEB_LOGIN_CHALLENGES.expiresAt,
                            WEB_LOGIN_CHALLENGES.verifiedAt,
                            WEB_LOGIN_CHALLENGES.consumedAt)
                    .from(WEB_LOGIN_CHALLENGES)
                    .where(WEB_LOGIN_CHALLENGES.browserTokenHash.eq(browserTokenHash))
                    .forUpdate()
                    .fetchOne());
            if (challenge == null) {
                return new CompletionResult(CompletionStatus.INVALID, null);
            }

            UUID memberId = uuid(challenge.get(WEB_LOGIN_CHALLENGES.memberId));
            if (challenge.get(WEB_LOGIN_CHALLENGES.consumedAt) != null) {
                return new CompletionResult(CompletionStatus.USED, memberId);
            }
            Instant expiresAt = instant(challenge.get(WEB_LOGIN_CHALLENGES.expiresAt));
            if (expiresAt == null || !expiresAt.isAfter(now)) {
                return new CompletionResult(CompletionStatus.EXPIRED, memberId);
            }
            if (challenge.get(WEB_LOGIN_CHALLENGES.verifiedAt) == null || memberId == null) {
                return new CompletionResult(CompletionStatus.PENDING, null);
            }

            Boolean active = database.query(q -> q.select(MEMBERS.active)
                    .from(MEMBERS)
                    .where(MEMBERS.id.eq(uuid(memberId)))
                    .fetchOne());
            if (!Boolean.TRUE.equals(active)) {
                return new CompletionResult(CompletionStatus.INACTIVE, memberId);
            }

            long updated = database.query(q -> q.update(WEB_LOGIN_CHALLENGES)
                    .set(WEB_LOGIN_CHALLENGES.consumedAt, time(now))
                    .where(WEB_LOGIN_CHALLENGES.id.eq(challenge.get(WEB_LOGIN_CHALLENGES.id)),
                            WEB_LOGIN_CHALLENGES.consumedAt.isNull())
                    .execute());
            return updated == 1
                    ? new CompletionResult(CompletionStatus.COMPLETED, memberId)
                    : new CompletionResult(CompletionStatus.USED, memberId);
        });
    }
}
