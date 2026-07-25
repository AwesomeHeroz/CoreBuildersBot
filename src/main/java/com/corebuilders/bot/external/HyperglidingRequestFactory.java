package com.corebuilders.bot.external;

import java.net.URI;
import java.net.http.HttpRequest;

/** Builds authenticated Hypergliding requests; separated from I/O for deterministic tests. */
public final class HyperglidingRequestFactory {
    private final HyperglidingConfig config;

    public HyperglidingRequestFactory(HyperglidingConfig config) {
        this.config = config;
    }

    public HttpRequest newPlayers(int page, int size) {
        if (page < 1) {
            throw new IllegalArgumentException("page must be at least 1.");
        }
        if (size < 1 || size > 50) {
            throw new IllegalArgumentException("size must be between 1 and 50.");
        }
        if (config.apiKey().isBlank()) {
            throw new IllegalStateException(
                    "Hypergliding API key is not configured. Set integrations.hypergliding.api-key "
                            + "in config.yml or COREBOT_HYPERGLIDING_API_KEY."
            );
        }

        URI uri = URI.create(config.endpoint()
                + "?page=" + page
                + "&size=" + size
                + "&sort=first_join&direction=desc&prio=1");
        return HttpRequest.newBuilder(uri)
                .timeout(config.timeout())
                .header("Accept", "application/json")
                .header("X-Internal-Api-Key", config.apiKey())
                .GET()
                .build();
    }
}
