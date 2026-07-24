package com.corebuilders.bot.service;

import com.corebuilders.bot.db.QueryDslDatabase;
import com.corebuilders.bot.model.Domain.ContributionCategory;
import com.corebuilders.bot.model.Domain.ContributionStatus;
import com.corebuilders.bot.model.Domain.SourceType;
import com.corebuilders.bot.model.Models.Contribution;
import com.corebuilders.bot.model.Models.Member;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.corebuilders.bot.db.DbMappers.contributionColumns;
import static com.corebuilders.bot.db.DbValues.now;
import static com.corebuilders.bot.db.DbValues.uuid;
import static com.corebuilders.bot.db.Schema.CONTRIBUTIONS;
import static com.corebuilders.bot.db.Schema.MEMBERS;

public final class ContributionService {
    private final QueryDslDatabase database;
    private final LedgerService ledger;
    private final AuditService audit;

    public ContributionService(QueryDslDatabase database, LedgerService ledger, AuditService audit) {
        this.database = database;
        this.ledger = ledger;
        this.audit = audit;
    }

    public Contribution submit(Member member, ContributionCategory category, String description,
                               String projectName, String evidenceUrl) {
        return database.inTransaction(() -> {
            Reward suggestion = defaultReward(category);
            UUID id = UUID.randomUUID();
            database.query(q -> {
                var insert = q.insert(CONTRIBUTIONS)
                        .set(CONTRIBUTIONS.id, uuid(id))
                        .set(CONTRIBUTIONS.memberId, uuid(member.id()))
                        .set(CONTRIBUTIONS.category, category.name())
                        .set(CONTRIBUTIONS.description, limit(description, 1000))
                        .set(CONTRIBUTIONS.status, ContributionStatus.PENDING.name())
                        .set(CONTRIBUTIONS.suggestedCxp, suggestion.cxp())
                        .set(CONTRIBUTIONS.suggestedCredits, suggestion.credits())
                        .set(CONTRIBUTIONS.createdAt, now());
                String project = nullableLimit(projectName, 200);
                String evidence = nullableLimit(evidenceUrl, 1000);
                if (project == null) insert.setNull(CONTRIBUTIONS.projectName); else insert.set(CONTRIBUTIONS.projectName, project);
                if (evidence == null) insert.setNull(CONTRIBUTIONS.evidenceUrl); else insert.set(CONTRIBUTIONS.evidenceUrl, evidence);
                return insert.execute();
            });

            audit.log(member.discordUserId(), "CONTRIBUTION_SUBMITTED", member.discordUserId(),
                    "CONTRIBUTION", id.toString(), category.name() + ": " + description);
            return get(id);
        });
    }

    public List<Contribution> pending(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 25));
        return database.query(q -> q.select(contributionColumns())
                .from(CONTRIBUTIONS)
                .join(MEMBERS).on(MEMBERS.id.eq(CONTRIBUTIONS.memberId))
                .where(CONTRIBUTIONS.status.eq(ContributionStatus.PENDING.name()))
                .orderBy(CONTRIBUTIONS.createdAt.asc())
                .limit(safeLimit)
                .fetch()
                .stream()
                .map(com.corebuilders.bot.db.DbMappers::contribution)
                .toList());
    }

    public Contribution get(UUID id) {
        return database.query(q -> Optional.ofNullable(q.select(contributionColumns())
                        .from(CONTRIBUTIONS)
                        .join(MEMBERS).on(MEMBERS.id.eq(CONTRIBUTIONS.memberId))
                        .where(CONTRIBUTIONS.id.eq(uuid(id)))
                        .fetchOne())
                .map(com.corebuilders.bot.db.DbMappers::contribution)
                .orElseThrow(() -> new IllegalArgumentException("Contribution not found.")));
    }

    public Contribution approve(UUID id, long cxp, long credits, String reviewerDiscordId, String reason) {
        return database.inTransaction(() -> {
            Contribution contribution = lock(id);
            if (contribution.status() != ContributionStatus.PENDING) {
                throw new IllegalStateException("This contribution has already been reviewed.");
            }
            if (cxp < 0 || credits < 0) throw new IllegalArgumentException("Awards cannot be negative.");

            database.query(q -> {
                var update = q.update(CONTRIBUTIONS)
                        .set(CONTRIBUTIONS.status, ContributionStatus.APPROVED.name())
                        .set(CONTRIBUTIONS.awardedCxp, cxp)
                        .set(CONTRIBUTIONS.awardedCredits, credits)
                        .set(CONTRIBUTIONS.reviewerDiscordId, reviewerDiscordId)
                        .set(CONTRIBUTIONS.reviewedAt, now());
                String review = nullableLimit(reason, 500);
                if (review == null) update.setNull(CONTRIBUTIONS.reviewReason); else update.set(CONTRIBUTIONS.reviewReason, review);
                return update.where(CONTRIBUTIONS.id.eq(uuid(id))).execute();
            });

            if (cxp > 0) {
                ledger.addXp(contribution.memberId(), cxp, contribution.category(), SourceType.CONTRIBUTION,
                        id, "Approved contribution: " + contribution.description(), reviewerDiscordId);
            }
            if (credits > 0) {
                ledger.addCredits(contribution.memberId(), credits, SourceType.CONTRIBUTION,
                        id, "Approved contribution: " + contribution.description(), reviewerDiscordId);
            }

            audit.log(reviewerDiscordId, "CONTRIBUTION_APPROVED", contribution.discordUserId(),
                    "CONTRIBUTION", id.toString(), "Awarded " + cxp + " CXP and " + credits + " CC. " + nullToEmpty(reason));
            return get(id);
        });
    }

    public Contribution reject(UUID id, String reviewerDiscordId, String reason) {
        return database.inTransaction(() -> {
            Contribution contribution = lock(id);
            if (contribution.status() != ContributionStatus.PENDING) {
                throw new IllegalStateException("This contribution has already been reviewed.");
            }
            database.query(q -> q.update(CONTRIBUTIONS)
                    .set(CONTRIBUTIONS.status, ContributionStatus.REJECTED.name())
                    .set(CONTRIBUTIONS.reviewerDiscordId, reviewerDiscordId)
                    .set(CONTRIBUTIONS.reviewReason, limit(reason, 500))
                    .set(CONTRIBUTIONS.reviewedAt, now())
                    .where(CONTRIBUTIONS.id.eq(uuid(id)))
                    .execute());

            audit.log(reviewerDiscordId, "CONTRIBUTION_REJECTED", contribution.discordUserId(),
                    "CONTRIBUTION", id.toString(), reason);
            return get(id);
        });
    }

    public long approvedCount(UUID memberId, ContributionCategory category) {
        Long count = database.query(q -> q.select(CONTRIBUTIONS.id.count())
                .from(CONTRIBUTIONS)
                .where(CONTRIBUTIONS.memberId.eq(uuid(memberId)),
                        CONTRIBUTIONS.category.eq(category.name()),
                        CONTRIBUTIONS.status.eq(ContributionStatus.APPROVED.name()))
                .fetchOne());
        return count == null ? 0L : count;
    }

    private Contribution lock(UUID id) {
        String locked = database.query(q -> q.select(CONTRIBUTIONS.id)
                .from(CONTRIBUTIONS)
                .where(CONTRIBUTIONS.id.eq(uuid(id)))
                .forUpdate()
                .fetchOne());
        if (locked == null) throw new IllegalArgumentException("Contribution not found.");
        return get(id);
    }

    private Reward defaultReward(ContributionCategory category) {
        return switch (category) {
            case BUILDING -> new Reward(250, 75);
            case INFRASTRUCTURE -> new Reward(200, 60);
            case SPAWN_HELP -> new Reward(50, 20);
            case RESOURCES -> new Reward(100, 40);
            case COMMUNITY -> new Reward(100, 40);
            case SPECIAL_OPERATIONS -> new Reward(300, 100);
            case BONUS -> new Reward(0, 0);
        };
    }

    private record Reward(long cxp, long credits) {}

    private static String limit(String value, int max) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("A description/reason is required.");
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static String nullableLimit(String value, int max) {
        if (value == null || value.isBlank()) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
