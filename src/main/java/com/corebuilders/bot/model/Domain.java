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
        ADMIN_ADJUSTMENT,
        REVERSAL
    }

    public enum AchievementMetric { TOTAL_XP, CATEGORY_XP, APPROVED_CONTRIBUTIONS }

    public enum RankTier {
        RECRUIT("Recruit", 0),
        MEMBER("Member", 500),
        CONTRIBUTOR("Contributor", 1_500),
        VETERAN("Veteran", 4_000),
        ELITE("Elite", 8_000),
        NOBLE("Noble", 15_000),
        CORE_LEGEND("Core Legend", 30_000);

        private final String display;
        private final long minimumXp;

        RankTier(String display, long minimumXp) {
            this.display = display;
            this.minimumXp = minimumXp;
        }

        public String display() { return display; }
        public long minimumXp() { return minimumXp; }

        public static RankTier fromXp(long xp) {
            RankTier result = RECRUIT;
            for (RankTier tier : values()) {
                if (xp >= tier.minimumXp) result = tier;
            }
            return result;
        }

        public RankTier next() {
            int i = ordinal() + 1;
            return i < values().length ? values()[i] : null;
        }
    }
}
