package com.corebuilders.bot.external;

/** Provider-neutral new-player data returned to command handlers. */
public record NewPlayerData(
        String playerUuid,
        String playerName,
        boolean online,
        String firstJoin,
        String lastJoin,
        String lastSeen,
        long ageSeconds,
        boolean prioQueue
) {}
