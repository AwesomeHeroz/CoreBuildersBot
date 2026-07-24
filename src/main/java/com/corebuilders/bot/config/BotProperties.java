package com.corebuilders.bot.config;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Immutable, security-sensitive Discord/integration configuration. */
public final class BotProperties {
    private final String token;
    private final String guildId;
    private final Set<String> trustedStaffRoleIds;
    private final Set<String> adminRoleIds;
    private final Set<String> leadershipRoleIds;
    private final String staffApprovalChannel;
    private final String contributionLogChannel;
    private final String economyLogChannel;
    private final String achievementChannel;
    private final String leaderboardChannel;
    private final boolean textCommandsEnabled;
    private final String textCommandPrefix;
    private final int linkCodeExpiryMinutes;
    private final String hyperglidingApiUrl;
    private final String hyperglidingApiKey;
    private final int hyperglidingTimeoutSeconds;
    private final int hyperglidingUserCooldownSeconds;
    private final int hyperglidingCacheSeconds;
    private final int hyperglidingGlobalRequestsPerMinute;

    public BotProperties(FileConfiguration config) {
        this.token = envOrConfig("DISCORD_BOT_TOKEN", config.getString("discord.token", ""));
        this.guildId = requireSnowflake(
                envOrConfig("DISCORD_GUILD_ID", config.getString("discord.guild-id", "")),
                "discord.guild-id / DISCORD_GUILD_ID"
        );
        this.trustedStaffRoleIds = roleIds(
                "COREBOT_TRUSTED_STAFF_ROLE_IDS",
                config.getStringList("discord.permissions.trusted-staff-role-ids"),
                "discord.permissions.trusted-staff-role-ids"
        );
        this.adminRoleIds = roleIds(
                "COREBOT_ADMIN_ROLE_IDS",
                config.getStringList("discord.permissions.admin-role-ids"),
                "discord.permissions.admin-role-ids"
        );
        this.leadershipRoleIds = roleIds(
                "COREBOT_LEADERSHIP_ROLE_IDS",
                config.getStringList("discord.permissions.leadership-role-ids"),
                "discord.permissions.leadership-role-ids"
        );
        this.staffApprovalChannel = envOrConfig("COREBOT_STAFF_APPROVAL_CHANNEL", config.getString("discord.channels.staff-approval", "staff-approvals"));
        this.contributionLogChannel = envOrConfig("COREBOT_CONTRIBUTION_LOG_CHANNEL", config.getString("discord.channels.contribution-log", "contribution-log"));
        this.economyLogChannel = envOrConfig("COREBOT_ECONOMY_LOG_CHANNEL", config.getString("discord.channels.economy-log", "economy-log"));
        this.achievementChannel = envOrConfig("COREBOT_ACHIEVEMENT_CHANNEL", config.getString("discord.channels.achievements", "achievements"));
        this.leaderboardChannel = envOrConfig("COREBOT_LEADERBOARD_CHANNEL", config.getString("discord.channels.leaderboard", "core-leaderboard"));
        this.textCommandsEnabled = config.getBoolean("discord.text-commands.enabled", false);
        this.textCommandPrefix = normalizePrefix(config.getString("discord.text-commands.prefix", "!core"));
        this.linkCodeExpiryMinutes = Math.max(1, config.getInt("minecraft.link-code-expiry-minutes", 10));
        this.hyperglidingApiUrl = config.getString("integrations.hypergliding.base-url", "https://hypergliding.com/api/");
        this.hyperglidingApiKey = envOrConfig(
                "COREBOT_HYPERGLIDING_API_KEY",
                config.getString("integrations.hypergliding.api-key", "")
        );
        this.hyperglidingTimeoutSeconds = clamp(config.getInt("integrations.hypergliding.timeout-seconds", 15), 3, 60);
        this.hyperglidingUserCooldownSeconds = clamp(config.getInt("integrations.hypergliding.user-cooldown-seconds", 10), 1, 300);
        this.hyperglidingCacheSeconds = clamp(config.getInt("integrations.hypergliding.cache-seconds", 30), 0, 600);
        this.hyperglidingGlobalRequestsPerMinute = clamp(config.getInt("integrations.hypergliding.global-requests-per-minute", 20), 1, 600);
    }

    public Set<String> trustedStaffRoleIds() { return trustedStaffRoleIds; }
    public Set<String> adminRoleIds() { return adminRoleIds; }
    public Set<String> leadershipRoleIds() { return leadershipRoleIds; }

    private static Set<String> roleIds(String envName, List<String> configured, String path) {
        String env = System.getenv(envName);
        Set<String> values;
        if (env != null && !env.isBlank()) {
            values = split(env);
        } else {
            values = configured == null ? Set.of() : configured.stream()
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }
        for (String value : values) {
            if (!isSnowflake(value)) {
                throw new IllegalStateException(path + " must contain Discord role IDs only; invalid value: " + value);
            }
        }
        return Set.copyOf(values);
    }

    private static String requireSnowflake(String value, String path) {
        String cleaned = value == null ? "" : value.trim();
        if (!isSnowflake(cleaned)) {
            throw new IllegalStateException(
                    "Missing or invalid " + path + ". CoreBot requires one explicit Discord guild ID for security."
            );
        }
        return cleaned;
    }

    private static boolean isSnowflake(String value) {
        return value != null && value.matches("\\d{15,22}");
    }

    private static String envOrConfig(String env, String configured) {
        String value = System.getenv(env);
        return value == null || value.isBlank() ? (configured == null ? "" : configured.trim()) : value.trim();
    }

    private static String normalizePrefix(String value) {
        if (value == null || value.isBlank()) return "!core";
        return value.trim();
    }

    private static Set<String> split(String value) {
        if (value == null || value.isBlank()) return Set.of();
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public String getToken() { return token; }
    public String getGuildId() { return guildId; }
    public String getStaffApprovalChannel() { return staffApprovalChannel; }
    public String getContributionLogChannel() { return contributionLogChannel; }
    public String getEconomyLogChannel() { return economyLogChannel; }
    public String getAchievementChannel() { return achievementChannel; }
    public String getLeaderboardChannel() { return leaderboardChannel; }
    public boolean isTextCommandsEnabled() { return textCommandsEnabled; }
    public String getTextCommandPrefix() { return textCommandPrefix; }
    public int getLinkCodeExpiryMinutes() { return linkCodeExpiryMinutes; }
    public String getHyperglidingApiUrl() { return hyperglidingApiUrl; }
    public String getHyperglidingApiKey() { return hyperglidingApiKey; }
    public int getHyperglidingTimeoutSeconds() { return hyperglidingTimeoutSeconds; }
    public int getHyperglidingUserCooldownSeconds() { return hyperglidingUserCooldownSeconds; }
    public int getHyperglidingCacheSeconds() { return hyperglidingCacheSeconds; }
    public int getHyperglidingGlobalRequestsPerMinute() { return hyperglidingGlobalRequestsPerMinute; }
}
