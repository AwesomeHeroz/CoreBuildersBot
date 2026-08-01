package com.corebuilders.bot.service;

import com.corebuilders.bot.db.QueryDslDatabase;
import com.corebuilders.bot.model.Domain.ContributionCategory;
import com.corebuilders.bot.model.Domain.SourceType;
import com.corebuilders.bot.model.Models.GroupStats;
import com.corebuilders.bot.model.Models.LeaderboardEntry;
import com.corebuilders.bot.model.Models.LedgerEntry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static com.corebuilders.bot.db.DbValues.*;
import static com.corebuilders.bot.db.Schema.*;

public final class LedgerService {
    private final QueryDslDatabase database;

    public LedgerService(QueryDslDatabase database) {
        this.database = database;
    }

    public long totalXp(UUID memberId) {
        return database.query(q -> value(q.select(XP.amount.sum().coalesce(0L))
                .from(XP)
                .where(XP.memberId.eq(uuid(memberId)))
                .fetchOne()));
    }

    public long weeklyXp(UUID memberId) {
        return database.query(q -> value(q.select(XP.amount.sum().coalesce(0L))
                .from(XP)
                .where(XP.memberId.eq(uuid(memberId)), XP.createdAt.goe(startOfCurrentWeek()))
                .fetchOne()));
    }

    public long categoryXp(UUID memberId, ContributionCategory category) {
        return database.query(q -> value(q.select(XP.amount.sum().coalesce(0L))
                .from(XP)
                .where(XP.memberId.eq(uuid(memberId)), XP.category.eq(category.name()))
                .fetchOne()));
    }

    public long creditBalance(UUID memberId) {
        return database.query(q -> value(q.select(CREDITS.amount.sum().coalesce(0L))
                .from(CREDITS)
                .where(CREDITS.memberId.eq(uuid(memberId)))
                .fetchOne()));
    }

    public UUID addXp(UUID memberId, long amount, ContributionCategory category, SourceType sourceType,
                      UUID referenceId, String reason, String actorDiscordId) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Point grants must be positive; use debitXpIfSufficient for debits.");
        }
        return database.inTransaction(() -> insertXp(memberId, amount, category, sourceType,
                referenceId, reason, actorDiscordId));
    }

    /**
     * Atomically removes XP while holding the member row, preventing concurrent
     * administrative adjustments from driving a member below zero.
     */
    public UUID debitXpIfSufficient(UUID memberId, long amount, ContributionCategory category,
                                    SourceType sourceType, UUID referenceId, String reason,
                                    String actorDiscordId) {
        if (amount <= 0) throw new IllegalArgumentException("Point debit amount must be positive.");
        return database.inTransaction(() -> {
            lockMember(memberId);
            if (totalXp(memberId) < amount) {
                throw new IllegalArgumentException("The member does not have enough points for this adjustment.");
            }
            return insertXp(memberId, -amount, category, sourceType, referenceId, reason, actorDiscordId);
        });
    }

    public UUID addCredits(UUID memberId, long amount, SourceType sourceType, UUID referenceId,
                           String reason, String actorDiscordId) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Coin grants must be positive; use debitIfSufficient for debits.");
        }
        return database.inTransaction(() -> insertCredits(memberId, amount, sourceType,
                referenceId, reason, actorDiscordId));
    }

    /**
     * Atomically removes credits while holding the member row. All purchase and
     * administrative debit paths must use this method rather than inserting a
     * negative ledger entry directly.
     */
    public UUID debitIfSufficient(UUID memberId, long amount, SourceType sourceType, UUID referenceId,
                                  String reason, String actorDiscordId) {
        if (amount <= 0) throw new IllegalArgumentException("Coin debit amount must be positive.");
        return database.inTransaction(() -> {
            lockMember(memberId);
            if (creditBalance(memberId) < amount) {
                throw new MarketplaceException(MarketplaceException.Code.INSUFFICIENT_FUNDS,
                        "Insufficient coins.");
            }
            return insertCredits(memberId, -amount, sourceType, referenceId, reason, actorDiscordId);
        });
    }

    private UUID insertXp(UUID memberId, long amount, ContributionCategory category, SourceType sourceType,
                          UUID referenceId, String reason, String actorDiscordId) {
        if (amount == 0) throw new IllegalArgumentException("Point amount cannot be zero.");
        UUID id = UUID.randomUUID();
        database.query(q -> {
            var insert = q.insert(XP)
                    .set(XP.id, uuid(id))
                    .set(XP.memberId, uuid(memberId))
                    .set(XP.amount, amount)
                    .set(XP.category, category.name())
                    .set(XP.sourceType, sourceType.name())
                    .set(XP.reason, limit(reason, 500))
                    .set(XP.actorDiscordId, actorDiscordId)
                    .set(XP.createdAt, now());
            if (referenceId == null) insert.setNull(XP.referenceId);
            else insert.set(XP.referenceId, uuid(referenceId));
            return insert.execute();
        });
        return id;
    }

    private UUID insertCredits(UUID memberId, long amount, SourceType sourceType, UUID referenceId,
                               String reason, String actorDiscordId) {
        if (amount == 0) throw new IllegalArgumentException("Coin amount cannot be zero.");
        UUID id = UUID.randomUUID();
        database.query(q -> {
            var insert = q.insert(CREDITS)
                    .set(CREDITS.id, uuid(id))
                    .set(CREDITS.memberId, uuid(memberId))
                    .set(CREDITS.amount, amount)
                    .set(CREDITS.sourceType, sourceType.name())
                    .set(CREDITS.reason, limit(reason, 500))
                    .set(CREDITS.actorDiscordId, actorDiscordId)
                    .set(CREDITS.createdAt, now());
            if (referenceId == null) insert.setNull(CREDITS.referenceId);
            else insert.set(CREDITS.referenceId, uuid(referenceId));
            return insert.execute();
        });
        return id;
    }

    public List<LeaderboardEntry> leaderboardOverall(int limit) {
        return database.query(q -> {
            var score = XP.amount.sum().coalesce(0L);
            return q.select(MEMBERS.discordUserId, MEMBERS.username, score)
                    .from(MEMBERS)
                    .leftJoin(XP).on(XP.memberId.eq(MEMBERS.id))
                    .where(MEMBERS.active.isTrue())
                    .groupBy(MEMBERS.id, MEMBERS.discordUserId, MEMBERS.username)
                    .orderBy(score.desc(), MEMBERS.username.asc())
                    .limit(clampLimit(limit))
                    .fetch()
                    .stream()
                    .map(t -> new LeaderboardEntry(
                            t.get(MEMBERS.discordUserId),
                            t.get(MEMBERS.username),
                            value(t.get(score))))
                    .toList();
        });
    }

    public List<LeaderboardEntry> leaderboardWeekly(int limit) {
        return database.query(q -> {
            var score = XP.amount.sum().coalesce(0L);
            return q.select(MEMBERS.discordUserId, MEMBERS.username, score)
                    .from(MEMBERS)
                    .leftJoin(XP).on(XP.memberId.eq(MEMBERS.id)
                            .and(XP.createdAt.goe(startOfCurrentWeek())))
                    .where(MEMBERS.active.isTrue())
                    .groupBy(MEMBERS.id, MEMBERS.discordUserId, MEMBERS.username)
                    .orderBy(score.desc(), MEMBERS.username.asc())
                    .limit(clampLimit(limit))
                    .fetch()
                    .stream()
                    .map(t -> new LeaderboardEntry(
                            t.get(MEMBERS.discordUserId),
                            t.get(MEMBERS.username),
                            value(t.get(score))))
                    .toList();
        });
    }

    public List<LeaderboardEntry> leaderboardCategory(ContributionCategory category, int limit) {
        return database.query(q -> {
            var score = XP.amount.sum().coalesce(0L);
            return q.select(MEMBERS.discordUserId, MEMBERS.username, score)
                    .from(MEMBERS)
                    .leftJoin(XP).on(XP.memberId.eq(MEMBERS.id)
                            .and(XP.category.eq(category.name())))
                    .where(MEMBERS.active.isTrue())
                    .groupBy(MEMBERS.id, MEMBERS.discordUserId, MEMBERS.username)
                    .orderBy(score.desc(), MEMBERS.username.asc())
                    .limit(clampLimit(limit))
                    .fetch()
                    .stream()
                    .map(t -> new LeaderboardEntry(
                            t.get(MEMBERS.discordUserId),
                            t.get(MEMBERS.username),
                            value(t.get(score))))
                    .toList();
        });
    }

    public List<LedgerEntry> recentTransactions(UUID memberId, int limit) {
        int safeLimit = clampLimit(limit);
        return database.query(q -> {
            List<LedgerEntry> entries = new ArrayList<>();
            q.select(XP.amount, XP.category, XP.reason, XP.createdAt)
                    .from(XP)
                    .where(XP.memberId.eq(uuid(memberId)))
                    .orderBy(XP.createdAt.desc())
                    .limit(safeLimit)
                    .fetch()
                    .forEach(t -> entries.add(new LedgerEntry(
                            "points", value(t.get(XP.amount)), t.get(XP.category),
                            t.get(XP.reason), instant(t.get(XP.createdAt)))));

            q.select(CREDITS.amount, CREDITS.reason, CREDITS.createdAt)
                    .from(CREDITS)
                    .where(CREDITS.memberId.eq(uuid(memberId)))
                    .orderBy(CREDITS.createdAt.desc())
                    .limit(safeLimit)
                    .fetch()
                    .forEach(t -> entries.add(new LedgerEntry(
                            "coins", value(t.get(CREDITS.amount)), null,
                            t.get(CREDITS.reason), instant(t.get(CREDITS.createdAt)))));

            return entries.stream()
                    .sorted(Comparator.comparing(LedgerEntry::createdAt).reversed())
                    .limit(safeLimit)
                    .toList();
        });
    }

    public GroupStats groupStats() {
        return database.query(q -> {
            long members = value(q.select(MEMBERS.id.count())
                    .from(MEMBERS).where(MEMBERS.active.isTrue()).fetchOne());
            long activeThisWeek = value(q.select(XP.memberId.countDistinct())
                    .from(XP).where(XP.createdAt.goe(startOfCurrentWeek())).fetchOne());
            long approvedContributions = value(q.select(CONTRIBUTIONS.id.count())
                    .from(CONTRIBUTIONS).where(CONTRIBUTIONS.status.eq("APPROVED")).fetchOne());
            long totalProjects = value(q.select(PROJECTS.id.count()).from(PROJECTS).fetchOne());
            long completedProjects = value(q.select(PROJECTS.id.count())
                    .from(PROJECTS).where(PROJECTS.status.eq("COMPLETED")).fetchOne());
            long totalCxpAwarded = value(q.select(XP.amount.sum().coalesce(0L)).from(XP).fetchOne());
            long creditsInCirculation = value(q.select(CREDITS.amount.sum().coalesce(0L)).from(CREDITS).fetchOne());
            return new GroupStats(members, activeThisWeek, approvedContributions, totalProjects,
                    completedProjects, totalCxpAwarded, creditsInCirculation);
        });
    }

    public void lockMember(UUID memberId) {
        String found = database.query(q -> q.select(MEMBERS.id)
                .from(MEMBERS)
                .where(MEMBERS.id.eq(uuid(memberId)))
                .forUpdate()
                .fetchOne());
        if (found == null) throw new IllegalArgumentException("Member profile not found.");
    }

    private static int clampLimit(int limit) {
        return Math.max(1, Math.min(limit, 25));
    }

    private static String limit(String value, int max) {
        if (value == null || value.isBlank()) return "No reason supplied";
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static long value(Long value) {
        return value == null ? 0L : value;
    }
}
