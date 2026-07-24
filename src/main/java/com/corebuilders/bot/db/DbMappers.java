package com.corebuilders.bot.db;

import com.corebuilders.bot.model.Domain.*;
import com.corebuilders.bot.model.Models.*;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.Expression;

import static com.corebuilders.bot.db.DbValues.instant;
import static com.corebuilders.bot.db.DbValues.uuid;
import static com.corebuilders.bot.db.Schema.*;

public final class DbMappers {
    private DbMappers() {}

    public static Expression<?>[] memberColumns() {
        return new Expression<?>[]{MEMBERS.id, MEMBERS.discordUserId, MEMBERS.username, MEMBERS.reputation,
                MEMBERS.primaryRole, MEMBERS.active, MEMBERS.createdAt};
    }

    public static Member member(Tuple t) {
        return new Member(
                uuid(t.get(MEMBERS.id)),
                t.get(MEMBERS.discordUserId),
                t.get(MEMBERS.username),
                Reputation.valueOf(t.get(MEMBERS.reputation)),
                t.get(MEMBERS.primaryRole),
                Boolean.TRUE.equals(t.get(MEMBERS.active)),
                instant(t.get(MEMBERS.createdAt))
        );
    }

    public static Expression<?>[] achievementColumns() {
        return new Expression<?>[]{ACHIEVEMENTS.code, ACHIEVEMENTS.name, ACHIEVEMENTS.description,
                ACHIEVEMENTS.metric, ACHIEVEMENTS.category, ACHIEVEMENTS.threshold,
                ACHIEVEMENTS.rewardCxp, ACHIEVEMENTS.rewardCredits};
    }

    public static Achievement achievement(Tuple t) {
        String category = t.get(ACHIEVEMENTS.category);
        return new Achievement(
                t.get(ACHIEVEMENTS.code),
                t.get(ACHIEVEMENTS.name),
                t.get(ACHIEVEMENTS.description),
                AchievementMetric.valueOf(t.get(ACHIEVEMENTS.metric)),
                category == null ? null : ContributionCategory.valueOf(category),
                value(t.get(ACHIEVEMENTS.threshold)),
                value(t.get(ACHIEVEMENTS.rewardCxp)),
                value(t.get(ACHIEVEMENTS.rewardCredits))
        );
    }

    public static Expression<?>[] contributionColumns() {
        return new Expression<?>[]{CONTRIBUTIONS.id, CONTRIBUTIONS.memberId, MEMBERS.discordUserId, MEMBERS.username,
                CONTRIBUTIONS.category, CONTRIBUTIONS.description, CONTRIBUTIONS.projectName,
                CONTRIBUTIONS.evidenceUrl, CONTRIBUTIONS.status, CONTRIBUTIONS.suggestedCxp,
                CONTRIBUTIONS.suggestedCredits, CONTRIBUTIONS.awardedCxp, CONTRIBUTIONS.awardedCredits,
                CONTRIBUTIONS.reviewerDiscordId, CONTRIBUTIONS.reviewReason, CONTRIBUTIONS.createdAt};
    }

    public static Contribution contribution(Tuple t) {
        return new Contribution(
                uuid(t.get(CONTRIBUTIONS.id)),
                uuid(t.get(CONTRIBUTIONS.memberId)),
                t.get(MEMBERS.discordUserId),
                t.get(MEMBERS.username),
                ContributionCategory.valueOf(t.get(CONTRIBUTIONS.category)),
                t.get(CONTRIBUTIONS.description),
                t.get(CONTRIBUTIONS.projectName),
                t.get(CONTRIBUTIONS.evidenceUrl),
                ContributionStatus.valueOf(t.get(CONTRIBUTIONS.status)),
                value(t.get(CONTRIBUTIONS.suggestedCxp)),
                value(t.get(CONTRIBUTIONS.suggestedCredits)),
                t.get(CONTRIBUTIONS.awardedCxp),
                t.get(CONTRIBUTIONS.awardedCredits),
                t.get(CONTRIBUTIONS.reviewerDiscordId),
                t.get(CONTRIBUTIONS.reviewReason),
                instant(t.get(CONTRIBUTIONS.createdAt))
        );
    }

    public static Expression<?>[] projectColumns() {
        return new Expression<?>[]{PROJECTS.id, PROJECTS.name, PROJECTS.description, PROJECTS.status,
                PROJECTS.leadDiscordId, PROJECTS.createdByDiscordId, PROJECTS.createdAt, PROJECTS.completedAt};
    }

    public static Project project(Tuple t) {
        return new Project(
                uuid(t.get(PROJECTS.id)),
                t.get(PROJECTS.name),
                t.get(PROJECTS.description),
                ProjectStatus.valueOf(t.get(PROJECTS.status)),
                t.get(PROJECTS.leadDiscordId),
                t.get(PROJECTS.createdByDiscordId),
                instant(t.get(PROJECTS.createdAt)),
                instant(t.get(PROJECTS.completedAt))
        );
    }

    public static Expression<?>[] projectTaskColumns() {
        return new Expression<?>[]{PROJECT_TASKS.id, PROJECT_TASKS.projectId, PROJECT_TASKS.title,
                PROJECT_TASKS.status, PROJECT_TASKS.assignedMemberId, MEMBERS.discordUserId,
                PROJECT_TASKS.rewardCxp, PROJECT_TASKS.rewardCredits, PROJECT_TASKS.completedByMemberId,
                PROJECT_TASKS.createdAt, PROJECT_TASKS.completedAt};
    }

    public static ProjectTask projectTask(Tuple t) {
        return new ProjectTask(
                uuid(t.get(PROJECT_TASKS.id)),
                uuid(t.get(PROJECT_TASKS.projectId)),
                t.get(PROJECT_TASKS.title),
                TaskStatus.valueOf(t.get(PROJECT_TASKS.status)),
                uuid(t.get(PROJECT_TASKS.assignedMemberId)),
                t.get(MEMBERS.discordUserId),
                value(t.get(PROJECT_TASKS.rewardCxp)),
                value(t.get(PROJECT_TASKS.rewardCredits)),
                uuid(t.get(PROJECT_TASKS.completedByMemberId)),
                instant(t.get(PROJECT_TASKS.createdAt)),
                instant(t.get(PROJECT_TASKS.completedAt))
        );
    }

    public static Expression<?>[] missionColumns() {
        return new Expression<?>[]{MISSIONS.id, MISSIONS.name, MISSIONS.description, MISSIONS.status,
                MISSIONS.rewardCxp, MISSIONS.rewardCredits, MISSIONS.maxSlots, MISSIONS.deadline,
                MISSIONS.createdByDiscordId, MISSIONS.createdAt};
    }

    public static Mission mission(Tuple t) {
        Integer maxSlots = t.get(MISSIONS.maxSlots);
        return new Mission(
                uuid(t.get(MISSIONS.id)),
                t.get(MISSIONS.name),
                t.get(MISSIONS.description),
                MissionStatus.valueOf(t.get(MISSIONS.status)),
                value(t.get(MISSIONS.rewardCxp)),
                value(t.get(MISSIONS.rewardCredits)),
                maxSlots == null ? 0 : maxSlots,
                instant(t.get(MISSIONS.deadline)),
                t.get(MISSIONS.createdByDiscordId),
                instant(t.get(MISSIONS.createdAt))
        );
    }

    public static Expression<?>[] shopItemColumns() {
        return new Expression<?>[]{SHOP_ITEMS.code, SHOP_ITEMS.name, SHOP_ITEMS.description,
                SHOP_ITEMS.price, SHOP_ITEMS.stock, SHOP_ITEMS.active};
    }

    public static ShopItem shopItem(Tuple t) {
        return new ShopItem(
                t.get(SHOP_ITEMS.code),
                t.get(SHOP_ITEMS.name),
                t.get(SHOP_ITEMS.description),
                value(t.get(SHOP_ITEMS.price)),
                t.get(SHOP_ITEMS.stock),
                Boolean.TRUE.equals(t.get(SHOP_ITEMS.active))
        );
    }

    public static Expression<?>[] shopOrderColumns() {
        return new Expression<?>[]{SHOP_ORDERS.id, SHOP_ORDERS.memberId, MEMBERS.discordUserId, MEMBERS.username,
                SHOP_ORDERS.itemCode, SHOP_ITEMS.name, SHOP_ORDERS.price, SHOP_ORDERS.status,
                SHOP_ORDERS.fulfillmentNote, SHOP_ORDERS.createdAt};
    }

    public static ShopOrder shopOrder(Tuple t) {
        return new ShopOrder(
                uuid(t.get(SHOP_ORDERS.id)),
                uuid(t.get(SHOP_ORDERS.memberId)),
                t.get(MEMBERS.discordUserId),
                t.get(MEMBERS.username),
                t.get(SHOP_ORDERS.itemCode),
                t.get(SHOP_ITEMS.name),
                value(t.get(SHOP_ORDERS.price)),
                OrderStatus.valueOf(t.get(SHOP_ORDERS.status)),
                t.get(SHOP_ORDERS.fulfillmentNote),
                instant(t.get(SHOP_ORDERS.createdAt))
        );
    }

    public static Expression<?>[] auditColumns() {
        return new Expression<?>[]{AUDIT_LOGS.id, AUDIT_LOGS.actorDiscordId, AUDIT_LOGS.action,
                AUDIT_LOGS.targetDiscordId, AUDIT_LOGS.entityType, AUDIT_LOGS.entityId,
                AUDIT_LOGS.details, AUDIT_LOGS.createdAt};
    }

    public static AuditEntry audit(Tuple t) {
        return new AuditEntry(
                uuid(t.get(AUDIT_LOGS.id)),
                t.get(AUDIT_LOGS.actorDiscordId),
                t.get(AUDIT_LOGS.action),
                t.get(AUDIT_LOGS.targetDiscordId),
                t.get(AUDIT_LOGS.entityType),
                t.get(AUDIT_LOGS.entityId),
                t.get(AUDIT_LOGS.details),
                instant(t.get(AUDIT_LOGS.createdAt))
        );
    }

    private static long value(Long value) {
        return value == null ? 0L : value;
    }
}
