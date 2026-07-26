package com.corebuilders.bot.config;

import com.corebuilders.bot.model.RankCatalog;
import com.corebuilders.bot.model.RankDefinition;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Loads progression ranks from config.yml. */
public final class ProgressionConfig {
    private ProgressionConfig() {}

    public static RankCatalog from(FileConfiguration config) {
        Objects.requireNonNull(config, "config");
        long xpPerLevel = config.getLong("progression.xp-per-level", RankCatalog.DEFAULT_XP_PER_LEVEL);
        boolean ranksConfigured = config.isSet("progression.ranks");
        List<Map<?, ?>> rawRanks = config.getMapList("progression.ranks");
        if (!ranksConfigured || rawRanks.isEmpty()) {
            RankCatalog defaults = RankCatalog.defaults();
            return xpPerLevel == defaults.xpPerLevel()
                    ? defaults
                    : new RankCatalog(defaults.ranks(), xpPerLevel);
        }

        List<RankDefinition> ranks = new ArrayList<>(rawRanks.size());
        for (Map<?, ?> raw : rawRanks) {
            ranks.add(new RankDefinition(
                    requiredText(raw.get("code"), "code"),
                    requiredText(raw.get("name"), "name"),
                    number(raw.get("minimum-xp"), "minimum-xp"),
                    optionalText(raw.get("discord-role-id"))
            ));
        }
        return new RankCatalog(ranks, xpPerLevel);
    }

    private static String requiredText(Object value, String field) {
        String text = value == null ? "" : String.valueOf(value).trim();
        if (text.isBlank()) {
            throw new IllegalArgumentException("Missing progression rank field: " + field);
        }
        return text;
    }

    private static String optionalText(Object value) {
        String text = value == null ? "" : String.valueOf(value).trim();
        return text.isBlank() ? null : text;
    }

    private static long number(Object value, String field) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            throw new IllegalArgumentException("Missing progression rank field: " + field);
        }
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("Invalid progression rank " + field + ": " + value, error);
        }
    }
}
