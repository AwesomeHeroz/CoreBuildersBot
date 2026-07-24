package com.corebuilders.bot.external;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class HyperglidingRequestFactoryTest {
    @Test
    void buildsExpectedAuthenticatedRequest() {
        var factory = new HyperglidingRequestFactory(new HyperglidingConfig(
                "https://hypergliding.com/api",
                "secret",
                Duration.ofSeconds(7)
        ));

        var request = factory.newPlayers(2, 10);

        assertEquals(
                "https://hypergliding.com/api/?page=2&size=10",
                request.uri().toString()
        );
        assertEquals("application/json", request.headers().firstValue("Accept").orElseThrow());
        assertEquals("secret", request.headers().firstValue("X-Internal-Api-Key").orElseThrow());
        assertEquals(Duration.ofSeconds(7), request.timeout().orElseThrow());
    }

    @Test
    void validatesPageSizeAndApiKey() {
        var missingKey = new HyperglidingRequestFactory(new HyperglidingConfig(
                "https://hypergliding.com/api/", "", Duration.ofSeconds(5)
        ));
        assertThrows(IllegalStateException.class, () -> missingKey.newPlayers(1, 5));

        var valid = new HyperglidingRequestFactory(new HyperglidingConfig(
                "https://hypergliding.com/api/", "key", Duration.ofSeconds(5)
        ));
        assertThrows(IllegalArgumentException.class, () -> valid.newPlayers(0, 5));
        assertThrows(IllegalArgumentException.class, () -> valid.newPlayers(1, 51));
    }

    @Test
    void rejectsUntrustedOrInsecureEndpoints() {
        assertThrows(IllegalArgumentException.class, () -> new HyperglidingConfig(
                "http://hypergliding.com/api/", "key", Duration.ofSeconds(5)
        ));
        assertThrows(IllegalArgumentException.class, () -> new HyperglidingConfig(
                "https://evil.example/api/", "key", Duration.ofSeconds(5)
        ));
        assertThrows(IllegalArgumentException.class, () -> new HyperglidingConfig(
                "https://hypergliding.com/api/?redirect=https://evil.example", "key", Duration.ofSeconds(5)
        ));
    }
}
