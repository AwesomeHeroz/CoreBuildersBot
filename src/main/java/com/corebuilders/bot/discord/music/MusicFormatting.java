package com.corebuilders.bot.discord.music;

import java.util.Locale;

public final class MusicFormatting {
    private MusicFormatting() {}

    public static String duration(long millis, boolean stream) {
        if (stream) return "LIVE";
        long total = Math.max(0, millis) / 1_000;
        long hours = total / 3_600;
        long minutes = (total % 3_600) / 60;
        long seconds = total % 60;
        return hours > 0
                ? String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
                : String.format(Locale.ROOT, "%d:%02d", minutes, seconds);
    }

    public static String safe(String value, int maxLength) {
        String clean = value == null ? "" : value
                .replace("@", "@\u200B")
                .replace("`", "ˋ")
                .replace("\n", " ")
                .replace("\r", " ")
                .trim();
        if (clean.length() <= maxLength) return clean;
        return clean.substring(0, Math.max(0, maxLength - 1)) + "…";
    }
}
