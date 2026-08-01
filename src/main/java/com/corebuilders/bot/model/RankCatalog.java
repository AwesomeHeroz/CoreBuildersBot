package com.corebuilders.bot.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/** Validated, immutable progression rules. */
public final class RankCatalog {
    public static final long DEFAULT_XP_PER_LEVEL = 500L;

    private final List<RankDefinition> ranks;
    private final long xpPerLevel;

    public RankCatalog(List<RankDefinition> configuredRanks, long xpPerLevel) {
        if (xpPerLevel < 1) {
            throw new IllegalArgumentException("progression.xp-per-level must be at least 1.");
        }
        if (configuredRanks == null || configuredRanks.isEmpty()) {
            throw new IllegalArgumentException("At least one progression rank must be configured.");
        }

        List<RankDefinition> sorted = new ArrayList<>(configuredRanks.size());
        for (RankDefinition rank : configuredRanks) {
            sorted.add(Objects.requireNonNull(rank, "Configured ranks cannot contain null."));
        }
        sorted.sort(Comparator.comparingLong(RankDefinition::minimumXp));

        Set<String> codes = new HashSet<>();
        Set<String> names = new HashSet<>();
        Set<Long> thresholds = new HashSet<>();
        Set<String> roleIds = new HashSet<>();
        for (RankDefinition rank : sorted) {
            if (!codes.add(rank.code())) {
                throw new IllegalArgumentException("Duplicate progression rank code: " + rank.code());
            }
            if (!names.add(rank.display().toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("Duplicate progression rank display name: " + rank.display());
            }
            if (!thresholds.add(rank.minimumXp())) {
                throw new IllegalArgumentException("Duplicate progression minimum-xp: " + rank.minimumXp());
            }
            if (rank.discordRoleId() != null && !roleIds.add(rank.discordRoleId())) {
                throw new IllegalArgumentException(
                        "Discord rank role ID is configured more than once: " + rank.discordRoleId()
                );
            }
        }
        if (sorted.getFirst().minimumXp() != 0) {
            throw new IllegalArgumentException("The first progression rank must start at 0 points.");
        }

        this.ranks = List.copyOf(sorted);
        this.xpPerLevel = xpPerLevel;
    }

    public static RankCatalog defaults() {
        return new RankCatalog(List.of(
                new RankDefinition("RECRUIT", "Recruit", 0, null),
                new RankDefinition("MEMBER", "Member", 500, null),
                new RankDefinition("CONTRIBUTOR", "Contributor", 1_500, null),
                new RankDefinition("VETERAN", "Veteran", 4_000, null),
                new RankDefinition("ELITE", "Elite", 8_000, null),
                new RankDefinition("NOBLE", "Noble", 15_000, null),
                new RankDefinition("CORE_LEGEND", "Core Legend", 30_000, null)
        ), DEFAULT_XP_PER_LEVEL);
    }

    public List<RankDefinition> ranks() {
        return ranks;
    }

    public long xpPerLevel() {
        return xpPerLevel;
    }

    public RankDefinition rankForXp(long xp) {
        long safeXp = Math.max(0, xp);
        RankDefinition current = ranks.getFirst();
        for (RankDefinition rank : ranks) {
            if (safeXp < rank.minimumXp()) {
                break;
            }
            current = rank;
        }
        return current;
    }

    public RankDefinition next(RankDefinition current) {
        Objects.requireNonNull(current, "current");
        int index = ranks.indexOf(current);
        if (index < 0) {
            throw new IllegalArgumentException(
                    "Rank is not part of this progression catalog: " + current.code()
            );
        }
        return index + 1 < ranks.size() ? ranks.get(index + 1) : null;
    }

    public int levelForXp(long xp) {
        long level = Math.max(0, xp) / xpPerLevel;
        return (int) Math.min(Integer.MAX_VALUE, level);
    }
}
