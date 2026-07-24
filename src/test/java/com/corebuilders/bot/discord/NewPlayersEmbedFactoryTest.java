package com.corebuilders.bot.discord;

import com.corebuilders.bot.external.NewPlayersResponse;
import com.corebuilders.bot.external.NewPlayerData;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NewPlayersEmbedFactoryTest {
    @Test
    void formatsPlayerAndPaginationData() {
        NewPlayerData player = new NewPlayerData(
                "uuid-1", "PlayerOne", true,
                "2026-01-01", null, "2026-07-23", 90061, true
        );
        NewPlayersResponse response = new NewPlayersResponse(2, 1, 10, 10, List.of(player));

        var embeds = new NewPlayersEmbedFactory().create("2b2t", 2, 1, response);

        assertEquals(1, embeds.size());
        assertTrue(embeds.get(0).getDescription().contains("Total: `10`"));
        assertTrue(embeds.get(0).getFields().get(0).getValue().contains("1 days, 1 hours, 1 minutes, 1 seconds"));
    }

    @Test
    void splitsLargeResultsIntoMultipleEmbeds() {
        NewPlayerData player = new NewPlayerData("u", "p", false, null, null, null, 0, false);
        NewPlayersResponse response = new NewPlayersResponse(1, 11, 11, 1,
                java.util.Collections.nCopies(11, player));

        assertEquals(2, new NewPlayersEmbedFactory().create("2b2t", 1, 11, response).size());
    }
}
