package com.corebuilders.bot.service;

import com.corebuilders.bot.db.QueryDslDatabase;
import com.corebuilders.bot.model.Domain.AchievementMetric;
import com.corebuilders.bot.model.Domain.ContributionCategory;
import com.corebuilders.bot.model.Domain.SourceType;
import com.corebuilders.bot.model.Models.Achievement;
import com.corebuilders.bot.model.Models.Member;
import com.querydsl.sql.SQLExpressions;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.corebuilders.bot.db.DbMappers.achievementColumns;
import static com.corebuilders.bot.db.DbValues.now;
import static com.corebuilders.bot.db.DbValues.uuid;
import static com.corebuilders.bot.db.Schema.ACHIEVEMENTS;
import static com.corebuilders.bot.db.Schema.MEMBER_ACHIEVEMENTS;

public final class AchievementService {
    private final QueryDslDatabase database;
    private final LedgerService ledger;
    private final ContributionService contributionService;
    private final AuditService audit;

    public AchievementService(QueryDslDatabase database, LedgerService ledger,
                              ContributionService contributionService, AuditService audit) {
        this.database = database;
        this.ledger = ledger;
        this.contributionService = contributionService;
        this.audit = audit;
    }

    public List<Achievement> unlocked(UUID memberId) {
        return database.query(q -> q.select(achievementColumns())
                .from(ACHIEVEMENTS)
                .join(MEMBER_ACHIEVEMENTS)
                .on(MEMBER_ACHIEVEMENTS.achievementCode.eq(ACHIEVEMENTS.code))
                .where(MEMBER_ACHIEVEMENTS.memberId.eq(uuid(memberId)))
                .orderBy(MEMBER_ACHIEVEMENTS.unlockedAt.asc())
                .fetch()
                .stream()
                .map(com.corebuilders.bot.db.DbMappers::achievement)
                .toList());
    }

    public List<Achievement> evaluate(Member member, String actorDiscordId) {
        return database.inTransaction(() -> {
            List<Achievement> newlyUnlocked = new ArrayList<>();
            boolean changed;
            int safety = 0;
            do {
                changed = false;
                for (Achievement achievement : lockedCandidates(member.id())) {
                    if (qualifies(member.id(), achievement) && unlock(member.id(), achievement.code())) {
                        newlyUnlocked.add(achievement);
                        changed = true;
                        if (achievement.rewardCxp() > 0) {
                            ledger.addXp(member.id(), achievement.rewardCxp(), ContributionCategory.BONUS,
                                    SourceType.ACHIEVEMENT, null, "Achievement: " + achievement.name(), "SYSTEM");
                        }
                        if (achievement.rewardCredits() > 0) {
                            ledger.addCredits(member.id(), achievement.rewardCredits(),
                                    SourceType.ACHIEVEMENT, null, "Achievement: " + achievement.name(), "SYSTEM");
                        }
                        audit.log(actorDiscordId == null ? "SYSTEM" : actorDiscordId,
                                "ACHIEVEMENT_UNLOCKED", member.discordUserId(),
                                "ACHIEVEMENT", achievement.code(),
                                achievement.name() + " unlocked. Rewards: "
                                        + achievement.rewardCxp() + " points, "
                                        + achievement.rewardCredits() + " coins");
                    }
                }
            } while (changed && ++safety < 20);
            return newlyUnlocked;
        });
    }

    private List<Achievement> lockedCandidates(UUID memberId) {
        var alreadyUnlocked = SQLExpressions.selectOne()
                .from(MEMBER_ACHIEVEMENTS)
                .where(MEMBER_ACHIEVEMENTS.memberId.eq(uuid(memberId))
                        .and(MEMBER_ACHIEVEMENTS.achievementCode.eq(ACHIEVEMENTS.code)));

        return database.query(q -> q.select(achievementColumns())
                .from(ACHIEVEMENTS)
                .where(ACHIEVEMENTS.active.isTrue(), alreadyUnlocked.notExists())
                .orderBy(ACHIEVEMENTS.threshold.asc())
                .fetch()
                .stream()
                .map(com.corebuilders.bot.db.DbMappers::achievement)
                .toList());
    }

    private boolean qualifies(UUID memberId, Achievement achievement) {
        return switch (achievement.metric()) {
            case TOTAL_XP -> ledger.totalXp(memberId) >= achievement.threshold();
            case CATEGORY_XP -> ledger.categoryXp(memberId, achievement.category()) >= achievement.threshold();
            case APPROVED_CONTRIBUTIONS ->
                    contributionService.approvedCount(memberId, achievement.category()) >= achievement.threshold();
        };
    }

    private boolean unlock(UUID memberId, String code) {
        try {
            return database.query(q -> q.insert(MEMBER_ACHIEVEMENTS)
                    .set(MEMBER_ACHIEVEMENTS.memberId, uuid(memberId))
                    .set(MEMBER_ACHIEVEMENTS.achievementCode, code)
                    .set(MEMBER_ACHIEVEMENTS.unlockedAt, now())
                    .execute()) == 1;
        } catch (RuntimeException error) {
            if (database.isDuplicateKey(error)) return false;
            throw error;
        }
    }
}
