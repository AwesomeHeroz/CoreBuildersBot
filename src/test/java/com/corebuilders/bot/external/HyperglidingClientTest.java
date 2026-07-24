package com.corebuilders.bot.external;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class HyperglidingClientTest {
    @Test
    void readsResponseWithinLimit() throws Exception {
        byte[] input = "{\"ok\":true}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        assertArrayEquals(input, HyperglidingClient.readLimited(new ByteArrayInputStream(input), input.length));
    }

    @Test
    void rejectsOversizedResponse() {
        byte[] input = new byte[11];
        assertThrows(IOException.class, () ->
                HyperglidingClient.readLimited(new ByteArrayInputStream(input), 10));
    }
}
