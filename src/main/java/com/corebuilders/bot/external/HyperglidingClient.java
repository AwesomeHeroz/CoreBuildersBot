package com.corebuilders.bot.external;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * HTTP client for external server-specific player discovery APIs.
 *
 * <p>This client deliberately has no database dependency. QueryDSL remains the
 * persistence mechanism for Core Builders data, while external HTTP APIs are
 * accessed through Java's standard {@link HttpClient}.</p>
 */
public final class HyperglidingClient implements NewPlayersProvider {
    private static final Logger log = LoggerFactory.getLogger(HyperglidingClient.class);
    private static final String SUPPORTED_SERVER = "2b2t";
    private static final int MAX_RESPONSE_BYTES = 2 * 1024 * 1024;
    private static final int MAX_LOG_PREVIEW_CHARS = 500;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final HyperglidingRequestFactory requestFactory;
    private final String apiKey;

    public HyperglidingClient(HyperglidingConfig config) {
        Objects.requireNonNull(config, "config");
        this.requestFactory = new HyperglidingRequestFactory(config);
        this.apiKey = config.apiKey();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(config.timeout())
                // The supplied curl example uses HTTP/1.1. Forcing the same protocol also
                // avoids intermittent HTTP/2/proxy compatibility failures seen with some CDNs.
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        this.objectMapper = new ObjectMapper()
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                // The API may add fields over time. New optional fields must not break the bot.
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Override
    public NewPlayersResponse fetchNewPlayers(String server, int page, int size) {
        String normalizedServer = normalizeServer(server);
        if (!SUPPORTED_SERVER.equals(normalizedServer)) {
            throw new IllegalArgumentException(
                    "Unsupported server '" + server + "'. Currently supported: " + SUPPORTED_SERVER + "."
            );
        }

        HttpRequest request = requestFactory.newPlayers(page, size);
        HttpResponse<InputStream> response = send(request);

        byte[] body;
        try (InputStream responseBody = response.body()) {
            body = readLimited(responseBody, MAX_RESPONSE_BYTES);
        } catch (IOException error) {
            log.warn("Could not read Hypergliding response body for {}: {}", request.uri(), error.getMessage());
            throw new IllegalStateException("The player API returned an invalid or oversized response.", error);
        }

        int status = response.statusCode();
        if (status < 200 || status >= 300) {
            logHttpFailure(request, response, body);
            throw statusException(status, response.headers().firstValue("Retry-After"));
        }

        if (body.length == 0) {
            log.warn("Hypergliding API returned an empty HTTP {} response for {}.", status, request.uri());
            throw new IllegalStateException("The player API returned an empty response. Please try again later.");
        }

        String contentType = response.headers().firstValue("Content-Type").orElse("");
        if (!contentType.isBlank() && !isJsonContentType(contentType)) {
            log.warn(
                    "Hypergliding API returned unexpected Content-Type '{}' for {}. Body preview: {}",
                    contentType,
                    request.uri(),
                    safePreview(body, apiKey)
            );
        }

        try {
            return parseResponse(body);
        } catch (IOException error) {
            log.warn(
                    "Could not parse Hypergliding JSON response from {}. Content-Type='{}'. Body preview: {}",
                    request.uri(),
                    contentType,
                    safePreview(body, apiKey),
                    error
            );
            throw new IllegalStateException("The player API returned an invalid JSON response.", error);
        }
    }


    NewPlayersResponse parseResponse(byte[] body) throws IOException {
        NewPlayersResponse parsed = objectMapper.readValue(body, NewPlayersResponse.class);
        validate(parsed);
        return parsed;
    }

    private HttpResponse<InputStream> send(HttpRequest request) {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("The Hypergliding API request was interrupted.", interrupted);
        } catch (IOException error) {
            log.warn("Could not reach Hypergliding API at {}: {}", request.uri(), error.toString());
            throw new IllegalStateException(
                    "Could not reach the player API. Check the server network, DNS and TLS configuration.",
                    error
            );
        }
    }

    private void logHttpFailure(
            HttpRequest request,
            HttpResponse<?> response,
            byte[] body
    ) {
        String contentType = response.headers().firstValue("Content-Type").orElse("unknown");
        String requestId = firstHeader(response, "CF-Ray", "X-Request-Id", "X-Correlation-Id")
                .orElse("none");
        log.warn(
                "Hypergliding API request failed: HTTP {}, uri={}, contentType={}, requestId={}, body={}",
                response.statusCode(),
                request.uri(),
                contentType,
                requestId,
                safePreview(body, apiKey)
        );
    }

    static IllegalStateException statusException(int status, Optional<String> retryAfter) {
        return switch (status) {
            case 401 -> new IllegalStateException(
                    "Hypergliding authentication failed. Check COREBOT_HYPERGLIDING_API_KEY or integrations.hypergliding.api-key."
            );
            case 403 -> new IllegalStateException(
                    "Hypergliding rejected this API key or server address. Check that the key is active and allowed."
            );
            case 301, 302, 307, 308 -> new IllegalStateException(
                    "The configured Hypergliding API endpoint redirects elsewhere. Configure the final HTTPS API URL directly."
            );
            case 400, 422 -> new IllegalStateException(
                    "Hypergliding rejected the page or size parameters. Check the command arguments."
            );
            case 404 -> new IllegalStateException(
                    "The configured Hypergliding API endpoint was not found. Check integrations.hypergliding.base-url."
            );
            case 408, 504 -> new IllegalStateException(
                    "The Hypergliding API timed out. Please try again later."
            );
            case 429 -> new IllegalStateException(
                    "The Hypergliding API rate limit was reached. Try again"
                            + retryAfter.filter(value -> !value.isBlank())
                            .map(value -> " after " + value)
                            .orElse(" later")
                            + "."
            );
            default -> {
                if (status >= 500) {
                    yield new IllegalStateException(
                            "The Hypergliding API is temporarily unavailable (HTTP " + status + ")."
                    );
                }
                yield new IllegalStateException(
                        "The Hypergliding API request failed (HTTP " + status + ")."
                );
            }
        };
    }

    static String safePreview(byte[] body, String secret) {
        if (body == null || body.length == 0) {
            return "<empty>";
        }
        String value = new String(body, StandardCharsets.UTF_8)
                .replace('\r', ' ')
                .replace('\n', ' ')
                .replace('\t', ' ')
                .replaceAll("[\\p{Cntrl}&&[^ ]]+", "?")
                .trim();
        if (secret != null && !secret.isBlank()) {
            value = value.replace(secret, "<redacted-api-key>");
        }
        if (value.length() > MAX_LOG_PREVIEW_CHARS) {
            return value.substring(0, MAX_LOG_PREVIEW_CHARS) + "…";
        }
        return value;
    }

    static byte[] readLimited(InputStream input, int maxBytes) throws IOException {
        if (maxBytes < 1) {
            throw new IllegalArgumentException("maxBytes must be positive.");
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(maxBytes, 64 * 1024));
        byte[] buffer = new byte[16 * 1024];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) >= 0) {
            if (read == 0) {
                continue;
            }
            total = Math.addExact(total, read);
            if (total > maxBytes) {
                throw new IOException("Response body exceeded the configured safety limit.");
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static void validate(NewPlayersResponse response) {
        if (response == null) {
            throw new IllegalStateException("The player API returned no response object.");
        }
        if (response.page() < 0 || response.size() < 0 || response.total() < 0 || response.pages() < 0) {
            throw new IllegalStateException("The player API returned invalid pagination values.");
        }
    }

    private static Optional<String> firstHeader(HttpResponse<?> response, String... names) {
        for (String name : names) {
            Optional<String> value = response.headers().firstValue(name);
            if (value.isPresent()) {
                return value;
            }
        }
        return Optional.empty();
    }

    private static boolean isJsonContentType(String contentType) {
        String normalized = contentType.toLowerCase(Locale.ROOT);
        return normalized.contains("application/json") || normalized.contains("+json");
    }

    private static String normalizeServer(String server) {
        return server == null ? "" : server.trim().toLowerCase(Locale.ROOT);
    }
}
