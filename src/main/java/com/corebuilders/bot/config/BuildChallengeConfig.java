package com.corebuilders.bot.config;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Configuration for the independent Discord build-challenge workflow. */
public final class BuildChallengeConfig {
    private final boolean enabled;
    private final boolean panelEnabled;
    private final String panelChannelId;
    private final String panelMessageId;
    private final String panelTitle;
    private final String panelDescription;
    private final String panelButtonLabel;
    private final String submissionsChannelId;
    private final String announcementChannelId;
    private final Set<String> judgeRoleIds;
    private final String prizeCategoryId;
    private final String prizeChannelNamePattern;
    private final String firstPlacePrize;
    private final String secondPlacePrize;
    private final String thirdPlacePrize;
    private final int maxScreenshots;
    private final long maxFileSizeBytes;
    private final long maxTotalUploadSizeBytes;
    private final boolean preventDuplicateSubmission;
    private final String submittedMessage;

    public BuildChallengeConfig(FileConfiguration config) {
        this.enabled = config.getBoolean("build-challenge.enabled", false);
        this.panelEnabled = enabled && config.getBoolean("build-challenge.entry-panel.enabled", true);
        this.panelChannelId = clean(config.getString("build-challenge.entry-panel.channel-id", ""));
        this.panelMessageId = clean(config.getString("build-challenge.entry-panel.message-id", ""));
        this.panelTitle = defaultIfBlank(config.getString("build-challenge.entry-panel.title", "Core Builders Build Challenge"), "Core Builders Build Challenge");
        this.panelDescription = defaultIfBlank(config.getString("build-challenge.entry-panel.description", "Submit your IGN, build coordinates, and screenshots for judging."), "Submit your IGN, build coordinates, and screenshots for judging.");
        this.panelButtonLabel = defaultIfBlank(config.getString("build-challenge.entry-panel.button-label", "Submit Build"), "Submit Build");
        this.submissionsChannelId = clean(config.getString("build-challenge.submissions-channel-id", ""));
        this.announcementChannelId = clean(config.getString("build-challenge.announcement-channel-id", ""));
        this.judgeRoleIds = parseSnowflakes(config.getStringList("build-challenge.judge-role-ids"), "build-challenge.judge-role-ids");
        this.prizeCategoryId = clean(config.getString("build-challenge.prize.category-id", ""));
        this.prizeChannelNamePattern = defaultIfBlank(config.getString("build-challenge.prize.channel-name-pattern", "prize-{place}-{username}"), "prize-{place}-{username}");
        this.firstPlacePrize = defaultIfBlank(config.getString("build-challenge.prize.place-1", "First place prize"), "First place prize");
        this.secondPlacePrize = defaultIfBlank(config.getString("build-challenge.prize.place-2", "Second place prize"), "Second place prize");
        this.thirdPlacePrize = defaultIfBlank(config.getString("build-challenge.prize.place-3", "Third place prize"), "Third place prize");
        this.maxScreenshots = clamp(config.getInt("build-challenge.uploads.max-screenshots", 5), 1, 10);
        int maxFileMb = clamp(config.getInt("build-challenge.uploads.max-file-size-mb", 15), 1, 100);
        int maxTotalMb = clamp(config.getInt("build-challenge.uploads.max-total-size-mb", 50), maxFileMb, 500);
        this.maxFileSizeBytes = maxFileMb * 1024L * 1024L;
        this.maxTotalUploadSizeBytes = maxTotalMb * 1024L * 1024L;
        this.preventDuplicateSubmission = config.getBoolean("build-challenge.prevent-duplicate-submission", true);
        this.submittedMessage = defaultIfBlank(config.getString("build-challenge.messages.submitted",
                "Your build challenge submission was successful. A judge will reach out soon to arrange a meeting at the build site."),
                "Your build challenge submission was successful. A judge will reach out soon to arrange a meeting at the build site.");
        validate();
    }

    private void validate() {
        if (!enabled) return;
        requireSnowflake(submissionsChannelId, "build-challenge.submissions-channel-id");
        if (!announcementChannelId.isBlank()) requireSnowflake(announcementChannelId, "build-challenge.announcement-channel-id");
        if (panelEnabled) requireSnowflake(panelChannelId, "build-challenge.entry-panel.channel-id");
        if (!panelMessageId.isBlank()) requireSnowflake(panelMessageId, "build-challenge.entry-panel.message-id");
        if (judgeRoleIds.isEmpty()) throw new IllegalStateException("build-challenge.judge-role-ids must contain at least one Discord role ID.");
        requireSnowflake(prizeCategoryId, "build-challenge.prize.category-id");
    }

    private static Set<String> parseSnowflakes(List<String> values, String path) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (values != null) for (String raw : values) {
            String value = clean(raw);
            if (value.isBlank()) continue;
            requireSnowflake(value, path);
            result.add(value);
        }
        return Set.copyOf(result);
    }

    private static void requireSnowflake(String value, String path) {
        if (!clean(value).matches("\\d{15,22}")) throw new IllegalStateException(path + " must be a Discord snowflake ID.");
    }

    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
    private static String clean(String value) { return value == null ? "" : value.trim(); }
    private static String defaultIfBlank(String value, String fallback) { String v = clean(value); return v.isBlank() ? fallback : v; }

    public boolean enabled() { return enabled; }
    public boolean panelEnabled() { return panelEnabled; }
    public String panelChannelId() { return panelChannelId; }
    public String panelMessageId() { return panelMessageId; }
    public String panelTitle() { return panelTitle; }
    public String panelDescription() { return panelDescription; }
    public String panelButtonLabel() { return panelButtonLabel; }
    public String submissionsChannelId() { return submissionsChannelId; }
    public String announcementChannelId() { return announcementChannelId; }
    public Set<String> judgeRoleIds() { return judgeRoleIds; }
    public String prizeCategoryId() { return prizeCategoryId; }
    public String prizeChannelNamePattern() { return prizeChannelNamePattern; }
    public String prizeForPlace(int place) {
        return switch (place) {
            case 1 -> firstPlacePrize;
            case 2 -> secondPlacePrize;
            case 3 -> thirdPlacePrize;
            default -> throw new IllegalArgumentException("Winner place must be 1, 2, or 3.");
        };
    }
    public int maxScreenshots() { return maxScreenshots; }
    public long maxFileSizeBytes() { return maxFileSizeBytes; }
    public long maxTotalUploadSizeBytes() { return maxTotalUploadSizeBytes; }
    public boolean preventDuplicateSubmission() { return preventDuplicateSubmission; }
    public String submittedMessage() { return submittedMessage; }
}
