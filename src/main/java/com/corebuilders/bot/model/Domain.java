package com.corebuilders.bot.model;

import java.util.Arrays;

public final class Domain {
    private Domain() {}

    public enum ContributionCategory {
        BUILDING("Building"),
        INFRASTRUCTURE("Infrastructure"),
        SPAWN_HELP("Spawn Help"),
        RESOURCES("Resources"),
        COMMUNITY("Community"),
        SPECIAL_OPERATIONS("Special Operations"),
        BONUS("Bonus");

        private final String display;
        ContributionCategory(String display) { this.display = display; }
        public String display() { return display; }

        public static ContributionCategory parse(String value) {
            return Arrays.stream(values())
                    .filter(v -> v.name().equalsIgnoreCase(value) || v.display.equalsIgnoreCase(value))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Unknown contribution category: " + value));
        }
    }

    public enum ContributionStatus { PENDING, APPROVED, REJECTED }
    public enum ProjectStatus { OPEN, IN_PROGRESS, COMPLETED, CANCELLED }
    public enum TaskStatus { OPEN, COMPLETED, CANCELLED }
    public enum MissionStatus { OPEN, IN_PROGRESS, COMPLETED, CANCELLED }
    public enum OrderStatus { PENDING, COMPLETED, CANCELLED, REFUNDED }
    public enum ApplicationStatus { PENDING, ACCEPTED, REJECTED }
    public enum Reputation {
        UNVERIFIED("Unverified"),
        RECOGNIZED("Recognized"),
        TRUSTED("Trusted"),
        HIGHLY_TRUSTED("Highly Trusted"),
        CORE_TRUSTED("Core Trusted");

        private final String display;
        Reputation(String display) { this.display = display; }
        public String display() { return display; }
    }

    public enum SourceType {
        STAFF_AWARD,
        CONTRIBUTION,
        PROJECT_TASK,
        MISSION,
        ACHIEVEMENT,
        SHOP_PURCHASE,
        MARKETPLACE_PURCHASE,
        MARKETPLACE_SALE,
        MARKETPLACE_REFUND,
        ADMIN_ADJUSTMENT,
        REVERSAL
    }

    public enum AchievementMetric { TOTAL_XP, CATEGORY_XP, APPROVED_CONTRIBUTIONS }


}
