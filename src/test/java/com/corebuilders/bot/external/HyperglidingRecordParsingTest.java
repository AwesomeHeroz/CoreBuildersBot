package com.corebuilders.bot.external;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HyperglidingRecordParsingTest {
    @Test
    void parsesBooleanRecordFieldsWithoutReflectiveMutation() throws Exception {
        String json = """
                {
                  "page": 1,
                  "size": 2,
                  "total": 2,
                  "pages": 1,
                  "players": [
                    {
                      "player_uuid": "cbb696c3-be69-47d9-8d45-efc7f236d905",
                      "player_name": "000000000TreeOne",
                      "online": true,
                      "first_join": "2024-12-26 21:01:13.345491",
                      "last_join": "2026-07-22 15:12:21.130720",
                      "last_seen": "2026-07-22 15:12:21.130720",
                      "age_seconds": 49506037,
                      "prio_queue": false
                    },
                    {
                      "player_uuid": "babea648-cc6f-40ac-8a08-b9da1c4e79c4",
                      "player_name": "0000002_Armorbar",
                      "online": false,
                      "first_join": "2026-01-07 15:02:35.237461",
                      "last_join": null,
                      "last_seen": "2026-07-22 15:47:27.954601",
                      "age_seconds": 16954755,
                      "prio_queue": true,
                      "future_field": "ignored"
                    }
                  ]
                }
                """;

        NewPlayersResponse response = HyperglidingClient.parseResponse(
                new ObjectMapper(),
                json.getBytes(StandardCharsets.UTF_8)
        );

        assertEquals(2, response.players().size());
        assertTrue(response.players().get(0).online());
        assertFalse(response.players().get(0).prioQueue());
        assertFalse(response.players().get(1).online());
        assertTrue(response.players().get(1).prioQueue());
        assertNull(response.players().get(1).lastJoin());
    }
}
