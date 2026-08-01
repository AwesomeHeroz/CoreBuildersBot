package com.corebuilders.bot.model;

import java.util.Locale;

/** One validated progression rank loaded from configuration. */
public record RankDefinition(
        String code,
        String display,
        long minimumXp,
        String discordRoleId
) {
    public RankDefinition {
        code = requireText(code, "Rank code").toUpperCase(Locale.ROOT);
        display = requireText(display, "Rank display name");
        discordRoleId = normalizeOptional(discordRoleId);

        if (!code.matches("[A-Z0-9_]{2,40}")) {
            throw new IllegalArgumentException("Rank code must match [A-Z0-9_]{2,40}: " + code);
        }
        if (display.length() > 100) {
            throw new IllegalArgumentException("Rank display name cannot exceed 100 characters: " + display);
        }
        if (minimumXp < 0) {
            throw new IllegalArgumentException("Rank minimum points cannot be negative: " + code);
        }
        if (discordRoleId != null && !discordRoleId.matches("\\d{15,22}")) {
            throw new IllegalArgumentException("Rank discord-role-id must be a Discord role ID: " + code);
        }
    }

    public boolean hasDiscordRoleId() {
        return discordRoleId != null;
    }

    private static String requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " is required.");
        }
        return value.trim();
    }

    private static String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
