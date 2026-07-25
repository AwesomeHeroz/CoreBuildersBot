package com.corebuilders.bot.external;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class HyperglidingClientTest {
    @Test
    void readsResponseWithinLimit() throws Exception {
        byte[] input = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
        assertArrayEquals(input, HyperglidingClient.readLimited(new ByteArrayInputStream(input), input.length));
    }

    @Test
    void rejectsOversizedResponse() {
        byte[] input = new byte[11];
        assertThrows(IOException.class, () ->
                HyperglidingClient.readLimited(new ByteArrayInputStream(input), 10));
    }

    @Test
    void parsesCurrentPayloadAndIgnoresFutureFields() throws Exception {
        var client = client();
        String json = """
                {
                  "page": 1,
                  "size": 1,
                  "total": 726,
                  "pages": 726,
                  "future_top_level_field": "ignored",
                  "players": [
                    {
                      "player_uuid": "cbb696c3-be69-47d9-8d45-efc7f236d905",
                      "player_name": "000000000TreeOne",
                      "online": true,
                      "first_join": "2024-12-26 21:01:13.345491",
                      "last_join": "2026-07-22 15:12:21.130720",
                      "last_seen": "2026-07-22 15:12:21.130720",
                      "age_seconds": 49506037,
                      "prio_queue": false,
                      "future_player_field": 123
                    }
                  ]
                }
                """;

        NewPlayersResponse response = client.parseResponse(json.getBytes(StandardCharsets.UTF_8));

        assertEquals(1, response.page());
        assertEquals(726, response.total());
        assertEquals(1, response.players().size());
        assertEquals("000000000TreeOne", response.players().getFirst().playerName());
        assertTrue(response.players().getFirst().online());
    }

    @Test
    void mapsAuthenticationAndRateLimitFailuresToUsefulMessages() {
        assertTrue(HyperglidingClient.statusException(401, Optional.empty())
                .getMessage().contains("API key"));
        assertTrue(HyperglidingClient.statusException(403, Optional.empty())
                .getMessage().contains("rejected"));
        assertTrue(HyperglidingClient.statusException(429, Optional.of("30"))
                .getMessage().contains("30"));
        assertTrue(HyperglidingClient.statusException(308, Optional.empty())
                .getMessage().contains("redirects"));
    }

    @Test
    void logPreviewRedactsApiKeyAndTruncatesLargeBodies() {
        byte[] body = ("failure secret-key " + "x".repeat(1000)).getBytes(StandardCharsets.UTF_8);
        String preview = HyperglidingClient.safePreview(body, "secret-key");

        assertFalse(preview.contains("secret-key"));
        assertTrue(preview.contains("<redacted-api-key>"));
        assertTrue(preview.endsWith("…"));
    }

    private static HyperglidingClient client() {
        return new HyperglidingClient(new HyperglidingConfig(
                "https://hypergliding.com/api/",
                "test-key",
                Duration.ofSeconds(5)
        ));
    }
}
