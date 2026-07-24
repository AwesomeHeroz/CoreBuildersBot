package com.corebuilders.bot.config;

import org.bukkit.configuration.file.FileConfiguration;

/** Immutable configuration used only by the Discord application entry panel. */
public record ApplicationPanelConfig(
        boolean enabled,
        String channel,
        String messageId,
        String title,
        String description,
        String buttonLabel
) {
    public ApplicationPanelConfig(FileConfiguration config) {
        this(
                config.getBoolean("applications.enabled", false)
                        && config.getBoolean("applications.entry-panel.enabled", true),
                firstNonBlank(
                        config.getString("applications.entry-panel.channel", ""),
                        config.getString("applications.entry-panel.channel-id", ""),
                        "apply"
                ),
                clean(config.getString("applications.entry-panel.message-id", "")),
                defaultIfBlank(
                        config.getString("applications.entry-panel.title", "Core Builders Applications"),
                        "Core Builders Applications"
                ),
                defaultIfBlank(
                        config.getString("applications.entry-panel.description", "Click the button below to apply."),
                        "Click the button below to apply."
                ),
                defaultIfBlank(
                        config.getString("applications.entry-panel.button.label", "Apply Now"),
                        "Apply Now"
                )
        );
    }

    private static String firstNonBlank(String first, String second, String fallback) {
        String firstValue = clean(first);
        if (!firstValue.isBlank()) return firstValue;
        String secondValue = clean(second);
        return secondValue.isBlank() ? fallback : secondValue;
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private static String defaultIfBlank(String value, String fallback) {
        String cleaned = clean(value);
        return cleaned.isBlank() ? fallback : cleaned;
    }
}
