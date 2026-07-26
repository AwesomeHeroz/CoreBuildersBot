package com.corebuilders.bot.model;

import com.corebuilders.bot.model.Domain.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class Models {
    private Models() {}

    public record Member(
            UUID id,
            String discordUserId,
            String username,
            Reputation reputation,
            String primaryRole,
            boolean active,
            Instant createdAt
    ) {}

    public record ProfileSnapshot(
            Member member,
            long totalXp,
            long credits,
            long weeklyXp,
            RankDefinition rank,
            RankDefinition nextRank,
            int level,
            Map<ContributionCategory, Long> categoryXp,
            List<Achievement> achievements
    ) {}

    public record LeaderboardEntry(String discordUserId, String username, long score) {}

    public record Contribution(
            UUID id,
            UUID memberId,
            String discordUserId,
            String username,
            ContributionCategory category,
            String description,
            String projectName,
            String evidenceUrl,
            ContributionStatus status,
            long suggestedCxp,
            long suggestedCredits,
            Long awardedCxp,
            Long awardedCredits,
            String reviewerDiscordId,
            String reviewReason,
            Instant createdAt
    ) {}

    public record Achievement(
            String code,
            String name,
            String description,
            AchievementMetric metric,
            ContributionCategory category,
            long threshold,
            long rewardCxp,
            long rewardCredits
    ) {}

    public record Project(
            UUID id,
            String name,
            String description,
            ProjectStatus status,
            String leadDiscordId,
            String createdByDiscordId,
            Instant createdAt,
            Instant completedAt
    ) {}

    public record ProjectTask(
            UUID id,
            UUID projectId,
            String title,
            TaskStatus status,
            UUID assignedMemberId,
            String assignedDiscordId,
            long rewardCxp,
            long rewardCredits,
            UUID completedByMemberId,
            Instant createdAt,
            Instant completedAt
    ) {}

    public record Mission(
            UUID id,
            String name,
            String description,
            MissionStatus status,
            long rewardCxp,
            long rewardCredits,
            int maxSlots,
            Instant deadline,
            String createdByDiscordId,
            Instant createdAt
    ) {}

    public record ShopItem(String code, String name, String description, long price, Integer stock, boolean active) {}

    public record ShopOrder(
            UUID id,
            UUID memberId,
            String discordUserId,
            String username,
            String itemCode,
            String itemName,
            long price,
            OrderStatus status,
            String fulfillmentNote,
            Instant createdAt
    ) {}

    public record LedgerEntry(
            String type,
            long amount,
            String category,
            String reason,
            Instant createdAt
    ) {}

    public record GroupStats(
            long members,
            long activeThisWeek,
            long approvedContributions,
            long totalProjects,
            long completedProjects,
            long totalCxpAwarded,
            long creditsInCirculation
    ) {}

    public record AuditEntry(
            UUID id,
            String actorDiscordId,
            String action,
            String targetDiscordId,
            String entityType,
            String entityId,
            String details,
            Instant createdAt
    ) {}
    public record ApplicationFile(
            String questionId,
            String questionLabel,
            String fileName,
            String contentType,
            long sizeBytes,
            String url,
            String messageId
    ) {}

    public record ApplicationAnswer(
            String questionId,
            String questionLabel,
            String type,
            String text,
            List<ApplicationFile> files
    ) {}

    public record ApplicationRecord(
            UUID id,
            String discordUserId,
            String username,
            ApplicationStatus status,
            List<ApplicationAnswer> answers,
            String pendingChannelId,
            String pendingMessageId,
            String ticketChannelId,
            String reviewerDiscordId,
            String reviewReason,
            Instant createdAt,
            Instant reviewedAt
    ) {}
}
