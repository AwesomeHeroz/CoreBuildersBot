package com.corebuilders.bot.external;

import java.net.URI;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;

/** Narrow configuration contract for the Hypergliding integration. */
public record HyperglidingConfig(String endpoint, String apiKey, Duration timeout) {
    private static final String ALLOWED_HOST = "hypergliding.com";

    public HyperglidingConfig {
        endpoint = Objects.requireNonNull(endpoint, "endpoint").trim();
        apiKey = apiKey == null ? "" : apiKey.trim();
        timeout = Objects.requireNonNull(timeout, "timeout");
        if (timeout.isZero() || timeout.isNegative()) throw new IllegalArgumentException("timeout must be positive.");

        URI uri;
        try {
            uri = URI.create(endpoint);
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("Invalid Hypergliding endpoint.", error);
        }
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if (!"https".equalsIgnoreCase(uri.getScheme())
                || !ALLOWED_HOST.equals(host)
                || uri.getUserInfo() != null
                || uri.getQuery() != null
                || uri.getFragment() != null) {
            throw new IllegalArgumentException("Hypergliding endpoint must be a plain HTTPS URL on " + ALLOWED_HOST + ".");
        }
        if (!endpoint.endsWith("/")) endpoint = endpoint + "/";
    }
}
