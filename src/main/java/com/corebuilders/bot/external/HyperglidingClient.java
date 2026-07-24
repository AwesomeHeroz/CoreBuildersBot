package com.corebuilders.bot.external;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HTTP client for external server-specific player discovery APIs.
 *
 * This client deliberately has no database dependency. QueryDSL remains the
 * persistence mechanism for Core Builders data, while external HTTP APIs are
 * accessed through Java's standard HttpClient.
 */
public final class HyperglidingClient implements NewPlayersProvider {
    private static final Logger log = LoggerFactory.getLogger(HyperglidingClient.class);
    private static final String SUPPORTED_SERVER = "2b2t";
    private static final int MAX_RESPONSE_BYTES = 2 * 1024 * 1024;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Duration requestTimeout;
    private final HyperglidingRequestFactory requestFactory;

    public HyperglidingClient(HyperglidingConfig config) {
        Objects.requireNonNull(config, "config");
        this.requestTimeout = config.timeout();
        this.requestFactory = new HyperglidingRequestFactory(config);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(requestTimeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        this.objectMapper = new ObjectMapper()
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
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

        final HttpResponse<InputStream> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("The Hypergliding API request was interrupted.", interrupted);
        } catch (IOException error) {
            throw new IllegalStateException("Could not reach the player API. Please try again later.", error);
        }

        try (InputStream body = response.body()) {
            if (response.statusCode() == 429) {
                throw new IllegalStateException("The Hypergliding API rate limit was reached. Try again later.");
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("Hypergliding API returned HTTP {}.", response.statusCode());
                throw new IllegalStateException("The player API returned an error. Please try again later.");
            }

            byte[] json = readLimited(body, MAX_RESPONSE_BYTES);
            return objectMapper.readValue(json, NewPlayersResponse.class);
        } catch (IOException error) {
            throw new IllegalStateException("Hypergliding API returned an invalid or oversized response.", error);
        }
    }

    static byte[] readLimited(InputStream input, int maxBytes) throws IOException {
        if (maxBytes < 1) throw new IllegalArgumentException("maxBytes must be positive.");
        ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(maxBytes, 64 * 1024));
        byte[] buffer = new byte[16 * 1024];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) >= 0) {
            if (read == 0) continue;
            total = Math.addExact(total, read);
            if (total > maxBytes) {
                throw new IOException("Response body exceeded the configured safety limit.");
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static String normalizeServer(String server) {
        return server == null ? "" : server.trim().toLowerCase(Locale.ROOT);
    }


}
