package com.corebuilders.bot.external;

import java.net.URI;
import java.net.http.HttpRequest;
import java.util.Objects;

/** Builds authenticated Hypergliding requests; separated from I/O for deterministic tests. */
public final class HyperglidingRequestFactory {
    static final String USER_AGENT = "CoreBuildersBot (+https://github.com/AwesomeHeroz/CoreBuildersBot)";

    private final HyperglidingConfig config;

    public HyperglidingRequestFactory(HyperglidingConfig config) {
        this.config = Objects.requireNonNull(config, "config");
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
                + "&size=" + size);

        return HttpRequest.newBuilder(uri)
                .timeout(config.timeout())
                // Match the known-working curl request closely. Some reverse proxies reject
                // Java's default request when it has no User-Agent or negotiates HTTP/2.
                .header("Accept", "application/json")
                .header("Accept-Encoding", "identity")
                .header("Cache-Control", "no-cache")
                .header("User-Agent", USER_AGENT)
                .header("X-Internal-Api-Key", config.apiKey())
                .GET()
                .build();
    }
}
