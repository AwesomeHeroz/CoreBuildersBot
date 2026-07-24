package com.corebuilders.bot.external;

/** Port used by command handlers to retrieve server-specific new-player data. */
@FunctionalInterface
public interface NewPlayersProvider {
    NewPlayersResponse fetchNewPlayers(String server, int page, int size);
}
