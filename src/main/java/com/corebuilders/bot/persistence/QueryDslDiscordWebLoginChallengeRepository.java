package com.corebuilders.bot.persistence;

import com.corebuilders.bot.db.QueryDslDatabase;
import com.corebuilders.bot.service.DiscordWebLoginChallengeRepository;
import com.querydsl.core.Tuple;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import static com.corebuilders.bot.db.DbValues.*;
import static com.corebuilders.bot.db.Schema.DISCORD_WEB_LOGIN_CHALLENGES;
import static com.corebuilders.bot.db.Schema.MEMBERS;

/** QueryDSL persistence adapter for Discord-bot-verified website logins. */
public final class QueryDslDiscordWebLoginChallengeRepository implements DiscordWebLoginChallengeRepository {
    private final QueryDslDatabase database;

    public QueryDslDiscordWebLoginChallengeRepository(QueryDslDatabase database) {
        this.database = Objects.requireNonNull(database, "database");
    }

    @Override
    public void create(NewChallenge challenge) {
        Objects.requireNonNull(challenge, "challenge");
        try {
            database.inTransaction(() -> {
                Instant staleBefore = challenge.createdAt().minusSeconds(86_400);
                database.query(q -> q.delete(DISCORD_WEB_LOGIN_CHALLENGES)
                        .where(DISCORD_WEB_LOGIN_CHALLENGES.expiresAt.lt(time(challenge.createdAt()))
                                .and(DISCORD_WEB_LOGIN_CHALLENGES.createdAt.lt(time(staleBefore)))
                                .or(DISCORD_WEB_LOGIN_CHALLENGES.consumedAt.isNotNull()
                                        .and(DISCORD_WEB_LOGIN_CHALLENGES.consumedAt.lt(time(staleBefore)))))
                        .execute());
                database.query(q -> q.insert(DISCORD_WEB_LOGIN_CHALLENGES)
                        .set(DISCORD_WEB_LOGIN_CHALLENGES.id, uuid(challenge.id()))
                        .set(DISCORD_WEB_LOGIN_CHALLENGES.browserTokenHash, challenge.browserTokenHash())
                        .set(DISCORD_WEB_LOGIN_CHALLENGES.verificationCodeHash, challenge.verificationCodeHash())
                        .set(DISCORD_WEB_LOGIN_CHALLENGES.expiresAt, time(challenge.expiresAt()))
                        .set(DISCORD_WEB_LOGIN_CHALLENGES.createdAt, time(challenge.createdAt()))
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
            String discordUserId,
            String discordUsername,
            String discordAvatarUrl,
            Instant now
    ) {
        return database.inTransaction(() -> {
            Tuple challenge = database.query(q -> q.select(
                            DISCORD_WEB_LOGIN_CHALLENGES.id,
                            DISCORD_WEB_LOGIN_CHALLENGES.memberId,
                            DISCORD_WEB_LOGIN_CHALLENGES.expiresAt,
                            DISCORD_WEB_LOGIN_CHALLENGES.verifiedAt,
                            DISCORD_WEB_LOGIN_CHALLENGES.consumedAt)
                    .from(DISCORD_WEB_LOGIN_CHALLENGES)
                    .where(DISCORD_WEB_LOGIN_CHALLENGES.verificationCodeHash.eq(verificationCodeHash))
                    .forUpdate()
                    .fetchOne());
            if (challenge == null) {
                return new VerificationResult(VerificationStatus.INVALID, null);
            }

            String challengeId = challenge.get(DISCORD_WEB_LOGIN_CHALLENGES.id);
            UUID existingMemberId = uuid(challenge.get(DISCORD_WEB_LOGIN_CHALLENGES.memberId));
            if (challenge.get(DISCORD_WEB_LOGIN_CHALLENGES.consumedAt) != null) {
                return new VerificationResult(VerificationStatus.USED, existingMemberId);
            }
            Instant expiresAt = instant(challenge.get(DISCORD_WEB_LOGIN_CHALLENGES.expiresAt));
            if (expiresAt == null || !expiresAt.isAfter(now)) {
                return new VerificationResult(VerificationStatus.EXPIRED, null);
            }
            if (challenge.get(DISCORD_WEB_LOGIN_CHALLENGES.verifiedAt) != null) {
                return new VerificationResult(VerificationStatus.ALREADY_VERIFIED, existingMemberId);
            }

            Tuple member = database.query(q -> q.select(MEMBERS.id, MEMBERS.active)
                    .from(MEMBERS)
                    .where(MEMBERS.discordUserId.eq(discordUserId))
                    .forUpdate()
                    .fetchOne());
            if (member == null) {
                return new VerificationResult(VerificationStatus.INVALID, null);
            }
            String memberId = member.get(MEMBERS.id);
            if (!Boolean.TRUE.equals(member.get(MEMBERS.active))) {
                return new VerificationResult(VerificationStatus.INACTIVE, uuid(memberId));
            }

            database.query(q -> q.update(MEMBERS)
                    .set(MEMBERS.discordUsername, discordUsername)
                    .set(MEMBERS.discordAvatarUrl, discordAvatarUrl)
                    .set(MEMBERS.updatedAt, time(now))
                    .where(MEMBERS.id.eq(memberId))
                    .execute());

            database.query(q -> q.update(DISCORD_WEB_LOGIN_CHALLENGES)
                    .set(DISCORD_WEB_LOGIN_CHALLENGES.memberId, memberId)
                    .set(DISCORD_WEB_LOGIN_CHALLENGES.discordUserId, discordUserId)
                    .set(DISCORD_WEB_LOGIN_CHALLENGES.discordUsername, discordUsername)
                    .set(DISCORD_WEB_LOGIN_CHALLENGES.discordAvatarUrl, discordAvatarUrl)
                    .set(DISCORD_WEB_LOGIN_CHALLENGES.verifiedAt, time(now))
                    .where(DISCORD_WEB_LOGIN_CHALLENGES.id.eq(challengeId),
                            DISCORD_WEB_LOGIN_CHALLENGES.verifiedAt.isNull(),
                            DISCORD_WEB_LOGIN_CHALLENGES.consumedAt.isNull())
                    .execute());
            return new VerificationResult(VerificationStatus.VERIFIED, uuid(memberId));
        });
    }

    @Override
    public CompletionResult complete(String browserTokenHash, Instant now, boolean consume) {
        return database.inTransaction(() -> {
            Tuple challenge = database.query(q -> q.select(
                            DISCORD_WEB_LOGIN_CHALLENGES.id,
                            DISCORD_WEB_LOGIN_CHALLENGES.memberId,
                            DISCORD_WEB_LOGIN_CHALLENGES.discordUsername,
                            DISCORD_WEB_LOGIN_CHALLENGES.discordAvatarUrl,
                            DISCORD_WEB_LOGIN_CHALLENGES.expiresAt,
                            DISCORD_WEB_LOGIN_CHALLENGES.verifiedAt,
                            DISCORD_WEB_LOGIN_CHALLENGES.consumedAt)
                    .from(DISCORD_WEB_LOGIN_CHALLENGES)
                    .where(DISCORD_WEB_LOGIN_CHALLENGES.browserTokenHash.eq(browserTokenHash))
                    .forUpdate()
                    .fetchOne());
            if (challenge == null) {
                return new CompletionResult(CompletionStatus.INVALID, null, null, null);
            }

            UUID memberId = uuid(challenge.get(DISCORD_WEB_LOGIN_CHALLENGES.memberId));
            String discordUsername = challenge.get(DISCORD_WEB_LOGIN_CHALLENGES.discordUsername);
            String discordAvatarUrl = challenge.get(DISCORD_WEB_LOGIN_CHALLENGES.discordAvatarUrl);
            if (challenge.get(DISCORD_WEB_LOGIN_CHALLENGES.consumedAt) != null) {
                return new CompletionResult(CompletionStatus.USED, memberId, discordUsername, discordAvatarUrl);
            }
            Instant expiresAt = instant(challenge.get(DISCORD_WEB_LOGIN_CHALLENGES.expiresAt));
            if (expiresAt == null || !expiresAt.isAfter(now)) {
                return new CompletionResult(CompletionStatus.EXPIRED, memberId, discordUsername, discordAvatarUrl);
            }
            if (challenge.get(DISCORD_WEB_LOGIN_CHALLENGES.verifiedAt) == null || memberId == null) {
                return new CompletionResult(CompletionStatus.PENDING, null, null, null);
            }

            Boolean active = database.query(q -> q.select(MEMBERS.active)
                    .from(MEMBERS)
                    .where(MEMBERS.id.eq(uuid(memberId)))
                    .fetchOne());
            if (!Boolean.TRUE.equals(active)) {
                return new CompletionResult(CompletionStatus.INACTIVE, memberId, discordUsername, discordAvatarUrl);
            }
            if (!consume) {
                return new CompletionResult(CompletionStatus.READY, memberId, discordUsername, discordAvatarUrl);
            }

            long updated = database.query(q -> q.update(DISCORD_WEB_LOGIN_CHALLENGES)
                    .set(DISCORD_WEB_LOGIN_CHALLENGES.consumedAt, time(now))
                    .where(DISCORD_WEB_LOGIN_CHALLENGES.id.eq(challenge.get(DISCORD_WEB_LOGIN_CHALLENGES.id)),
                            DISCORD_WEB_LOGIN_CHALLENGES.consumedAt.isNull())
                    .execute());
            return updated == 1
                    ? new CompletionResult(CompletionStatus.COMPLETED, memberId, discordUsername, discordAvatarUrl)
                    : new CompletionResult(CompletionStatus.USED, memberId, discordUsername, discordAvatarUrl);
        });
    }
}
