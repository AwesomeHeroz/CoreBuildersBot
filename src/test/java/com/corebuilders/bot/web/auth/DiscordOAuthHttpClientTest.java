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
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class DiscordOAuthHttpClientTest {
    private HttpServer server;
    private final AtomicBoolean includeGuild = new AtomicBoolean(true);

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/token", exchange -> json(exchange, 200, "{\"access_token\":\"access\"}"));
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
    void buildsAuthorizationUrlAndExchangesCode() {
        DiscordOAuthHttpClient client = client();

        URI authorization = client.authorizationUri("state-value");
        DiscordIdentity identity = client.exchange("code-value");

        assertTrue(authorization.toString().contains("scope=identify+guilds"));
        assertTrue(authorization.toString().contains("state=state-value"));
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
                "123456789012345678", "secret", URI.create("http://127.0.0.1/api/auth/callback"),
                true, false, Duration.ofHours(1), 1024 * 1024, 4
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

    private static void json(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
