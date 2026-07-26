package com.corebuilders.bot.service;

import com.corebuilders.bot.db.QueryDslDatabase;
import com.corebuilders.bot.model.Domain.ContributionCategory;
import com.corebuilders.bot.model.Domain.Reputation;
import com.corebuilders.bot.model.Models.Achievement;
import com.corebuilders.bot.model.Models.Member;
import com.corebuilders.bot.model.Models.ProfileSnapshot;
import com.corebuilders.bot.model.RankCatalog;
import com.corebuilders.bot.model.RankDefinition;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static com.corebuilders.bot.db.DbMappers.member;
import static com.corebuilders.bot.db.DbMappers.memberColumns;
import static com.corebuilders.bot.db.DbValues.now;
import static com.corebuilders.bot.db.Schema.MEMBERS;

public final class MemberService {
    private final QueryDslDatabase database;
    private final LedgerService ledgerService;
    private final RankCatalog ranks;

    public MemberService(QueryDslDatabase database, LedgerService ledgerService) {
        this(database, ledgerService, RankCatalog.defaults());
    }

    public MemberService(QueryDslDatabase database, LedgerService ledgerService, RankCatalog ranks) {
        this.database = Objects.requireNonNull(database, "database");
        this.ledgerService = Objects.requireNonNull(ledgerService, "ledgerService");
        this.ranks = Objects.requireNonNull(ranks, "ranks");
    }

    public Member ensureMember(String discordUserId, String username) {
        return database.inTransaction(() -> {
            String safeUsername = safeUsername(username);
            Optional<Member> existing = findByDiscordId(discordUserId);
            if (existing.isPresent()) {
                database.query(q -> q.update(MEMBERS)
                        .set(MEMBERS.username, safeUsername)
                        .set(MEMBERS.updatedAt, now())
                        .where(MEMBERS.discordUserId.eq(discordUserId))
                        .execute());
                return requireByDiscordId(discordUserId);
            }

            try {
                database.query(q -> q.insert(MEMBERS)
                        .set(MEMBERS.id, UUID.randomUUID().toString())
                        .set(MEMBERS.discordUserId, discordUserId)
                        .set(MEMBERS.username, safeUsername)
                        .set(MEMBERS.reputation, Reputation.UNVERIFIED.name())
                        .set(MEMBERS.active, true)
                        .set(MEMBERS.createdAt, now())
                        .set(MEMBERS.updatedAt, now())
                        .execute());
            } catch (RuntimeException error) {
                if (!database.isDuplicateKey(error)) throw error;
                database.query(q -> q.update(MEMBERS)
                        .set(MEMBERS.username, safeUsername)
                        .set(MEMBERS.updatedAt, now())
                        .where(MEMBERS.discordUserId.eq(discordUserId))
                        .execute());
            }
            return requireByDiscordId(discordUserId);
        });
    }

    public Optional<Member> findByDiscordId(String discordUserId) {
        return database.query(q -> Optional.ofNullable(q.select(memberColumns())
                        .from(MEMBERS)
                        .where(MEMBERS.discordUserId.eq(discordUserId))
                        .fetchOne())
                .map(com.corebuilders.bot.db.DbMappers::member));
    }

    public Member requireByDiscordId(String discordUserId) {
        return findByDiscordId(discordUserId)
                .orElseThrow(() -> new IllegalArgumentException("That Discord user has no Core Builders profile yet."));
    }

    public Member setReputation(String discordUserId, Reputation reputation, String actorDiscordId, String reason,
                                AuditService auditService) {
        return database.inTransaction(() -> {
            long changed = database.query(q -> q.update(MEMBERS)
                    .set(MEMBERS.reputation, reputation.name())
                    .set(MEMBERS.updatedAt, now())
                    .where(MEMBERS.discordUserId.eq(discordUserId))
                    .execute());
            if (changed == 0) throw new IllegalArgumentException("Member profile not found.");
            auditService.log(actorDiscordId, "REPUTATION_SET", discordUserId, "MEMBER", discordUserId,
                    "Reputation changed to " + reputation.name() + ". Reason: " + reason);
            return requireByDiscordId(discordUserId);
        });
    }

    public Member setPrimaryRole(String discordUserId, String primaryRole, String actorDiscordId,
                                 AuditService auditService) {
        return database.inTransaction(() -> {
            String role = primaryRole == null || primaryRole.isBlank() ? null : primaryRole.trim();
            long changed = database.query(q -> {
                var update = q.update(MEMBERS).set(MEMBERS.updatedAt, now());
                if (role == null) update.setNull(MEMBERS.primaryRole);
                else update.set(MEMBERS.primaryRole, role);
                return update.where(MEMBERS.discordUserId.eq(discordUserId)).execute();
            });
            if (changed == 0) throw new IllegalArgumentException("Member profile not found.");
            auditService.log(actorDiscordId, "PRIMARY_ROLE_SET", discordUserId, "MEMBER", discordUserId,
                    "Primary role set to: " + (role == null ? "None" : role));
            return requireByDiscordId(discordUserId);
        });
    }

    public Member setActive(String discordUserId, boolean active, String actorDiscordId, AuditService auditService) {
        return database.inTransaction(() -> {
            long changed = database.query(q -> q.update(MEMBERS)
                    .set(MEMBERS.active, active)
                    .set(MEMBERS.updatedAt, now())
                    .where(MEMBERS.discordUserId.eq(discordUserId))
                    .execute());
            if (changed == 0) throw new IllegalArgumentException("Member profile not found.");
            auditService.log(actorDiscordId, active ? "MEMBER_ACTIVATED" : "MEMBER_DEACTIVATED", discordUserId,
                    "MEMBER", discordUserId, "Active status changed to " + active);
            return requireByDiscordId(discordUserId);
        });
    }

    public ProfileSnapshot snapshot(String discordUserId, AchievementService achievementService) {
        Member member = requireByDiscordId(discordUserId);
        long totalXp = ledgerService.totalXp(member.id());
        long credits = ledgerService.creditBalance(member.id());
        long weeklyXp = ledgerService.weeklyXp(member.id());
        Map<ContributionCategory, Long> categories = new EnumMap<>(ContributionCategory.class);
        for (ContributionCategory category : ContributionCategory.values()) {
            categories.put(category, ledgerService.categoryXp(member.id(), category));
        }
        List<Achievement> achievements = achievementService.unlocked(member.id());
        RankDefinition rank = ranks.rankForXp(totalXp);
        return new ProfileSnapshot(
                member,
                totalXp,
                credits,
                weeklyXp,
                rank,
                ranks.next(rank),
                ranks.levelForXp(totalXp),
                categories,
                achievements
        );
    }

    private static String safeUsername(String value) {
        if (value == null || value.isBlank()) return "Unknown";
        return value.length() <= 100 ? value : value.substring(0, 100);
    }
}
