package com.corebuilders.bot.discord;

import java.util.Locale;

/** Shared presentation helpers for Discord responses. */
public final class DiscordFormatting {
    private DiscordFormatting() {}

    public static String truncate(String value, int max) {
        if (value == null) return "";
        if (max < 1) return "";
        return value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }

    public static String valueOrNull(String value) {
        if (value == null) return "null";
        String cleaned = value.replace("`", "'");
        return cleaned.isBlank() ? "" : cleaned;
    }

    public static String value(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }

    public static String formatNumber(long value) {
        return String.format(Locale.US, "%,d", value);
    }

    public static long number(Long value) {
        return value == null ? 0 : value;
    }

    public static String medal(int index) {
        return switch (index) {
            case 0 -> "🥇";
            case 1 -> "🥈";
            case 2 -> "🥉";
            default -> "▫️";
        };
    }
}
