package com.corebuilders.bot.external;

import java.util.List;

/** Provider-neutral page of newly observed players. */
public record NewPlayersResponse(
        int page,
        int size,
        long total,
        int pages,
        List<NewPlayerData> players
) {
    public NewPlayersResponse {
        players = players == null ? List.of() : List.copyOf(players);
    }
}
