package com.corebuilders.bot.web.auth;

import com.corebuilders.bot.config.WebsiteConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class DiscordOAuthHttpClientTest {
    private static final String CALLBACK = "http://127.0.0.1/api/account/discord/callback";

    private HttpServer server;
    private final AtomicBoolean includeGuild = new AtomicBoolean(true);
    private final AtomicReference<String> tokenRequestBody = new AtomicReference<>();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/token", exchange -> {
            tokenRequestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            json(exchange, 200, "{\"access_token\":\"access\"}");
        });
        server.createContext("/user", exchange -> json(exchange, 200,
                "{\"id\":\"123456789012345678\",\"username\":\"builder\",\"global_name\":\"Builder\",\"avatar\":\"abc\"}"));
        server.createContext("/guilds", exchange -> json(exchange, 200,
                includeGuild.get() ? "[{\"id\":\"987654321098765432\"}]" : "[{\"id\":\"111111111111111111\"}]"));
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void buildsAuthorizationUrlWithExactCanonicalRedirectAndExchangesCode() {
        DiscordOAuthHttpClient client = client();

        URI authorization = client.authorizationUri("state value");
        DiscordIdentity identity = client.exchange("code value");

        assertTrue(authorization.getRawQuery().contains("scope=identify%20guilds"));
        assertFalse(authorization.getRawQuery().contains("scope=identify+guilds"));
        Map<String, String> authorizationQuery = decodeParameters(authorization.getRawQuery());
        assertEquals("123456789012345678", authorizationQuery.get("client_id"));
        assertEquals(CALLBACK, authorizationQuery.get("redirect_uri"));
        assertEquals("identify guilds", authorizationQuery.get("scope"));
        assertEquals("state value", authorizationQuery.get("state"));

        Map<String, String> tokenForm = decodeParameters(tokenRequestBody.get());
        assertEquals(CALLBACK, tokenForm.get("redirect_uri"));
        assertEquals("code value", tokenForm.get("code"));
        assertEquals("authorization_code", tokenForm.get("grant_type"));

        assertEquals("123456789012345678", identity.id());
        assertEquals("Builder", identity.displayName());
        assertTrue(identity.avatarUrl().contains("/avatars/123456789012345678/abc.png"));
    }

    @Test
    void rejectsUsersOutsideConfiguredGuild() {
        includeGuild.set(false);
        OAuthException error = assertThrows(OAuthException.class, () -> client().exchange("code-value"));
        assertEquals(OAuthException.Code.NOT_IN_GUILD, error.code());
    }

    private DiscordOAuthHttpClient client() {
        int port = server.getAddress().getPort();
        WebsiteConfig config = new WebsiteConfig(
                true, "127.0.0.1", 0, URI.create("http://127.0.0.1"),
                "123456789012345678", "secret", URI.create(CALLBACK),
                true, false, Duration.ofHours(1), Duration.ofMinutes(30),
                1024 * 1024, 4, java.util.Set.of(), false, "", ""
        );
        return new DiscordOAuthHttpClient(
                config,
                "987654321098765432",
                new ObjectMapper(),
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build(),
                URI.create("http://127.0.0.1:" + port + "/authorize"),
                URI.create("http://127.0.0.1:" + port + "/token"),
                URI.create("http://127.0.0.1:" + port + "/user"),
                URI.create("http://127.0.0.1:" + port + "/guilds")
        );
    }

    private static Map<String, String> decodeParameters(String input) {
        if (input == null || input.isBlank()) return Map.of();
        return Arrays.stream(input.split("&"))
                .map(part -> part.split("=", 2))
                .collect(Collectors.toMap(
                        pair -> decode(pair[0]),
                        pair -> pair.length == 2 ? decode(pair[1]) : "",
                        (first, ignored) -> first,
                        ConcurrentHashMap::new
                ));
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static void json(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
