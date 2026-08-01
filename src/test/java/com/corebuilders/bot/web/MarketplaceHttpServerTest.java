package com.corebuilders.bot.web;

import com.corebuilders.bot.config.WebsiteConfig;
import com.corebuilders.bot.model.MarketplaceModels.*;
import com.corebuilders.bot.service.MarketplaceOperations;
import com.corebuilders.bot.service.DiscordWebLoginChallengeRepository;
import com.corebuilders.bot.service.DiscordWebLoginService;
import com.corebuilders.bot.service.WebLoginChallengeRepository;
import com.corebuilders.bot.service.WebLoginService;
import com.corebuilders.bot.web.auth.DiscordIdentity;
import com.corebuilders.bot.web.auth.DiscordOAuth;
import com.corebuilders.bot.web.auth.SessionPrincipal;
import com.corebuilders.bot.web.auth.WebsiteIdentity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class MarketplaceHttpServerTest {
    private static final UUID MEMBER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID SELLER_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID SHOP_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID ITEM_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID CART_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID ORDER_ID = UUID.fromString("40000000-0000-0000-0000-000000000001");
    private static final UUID LINE_ID = UUID.fromString("50000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-07-26T10:00:00Z");

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(2))
            .build();

    private FakeMarketplace marketplace;
    private FakeLoginRepository loginRepository;
    private FakeDiscordLoginRepository discordLoginRepository;
    private AtomicBoolean discordLinked;
    private MarketplaceHttpServer server;
    private URI baseUri;
    @TempDir
    Path uploadDirectory;

    @BeforeEach
    void startServer() throws Exception {
        marketplace = new FakeMarketplace();
        WebsiteConfig config = new WebsiteConfig(
                true,
                "127.0.0.1",
                0,
                URI.create("http://127.0.0.1"),
                "123456789012345678",
                "secret",
                URI.create("http://127.0.0.1/api/account/discord/callback"),
                false,
                false,
                Duration.ofHours(1),
                Duration.ofMinutes(30),
                1024 * 1024,
                4,
                Set.of("example.com"),
                uploadDirectory,
                "/uploads/images",
                5 * 1024 * 1024,
                false,
                "", ""
        );
        DiscordOAuth oauth = new FakeOAuth();
        discordLinked = new AtomicBoolean(false);
        AtomicLong securityVersion = new AtomicLong(0L);
        WebsiteIdentity identity = new WebsiteIdentity() {
            @Override
            public SessionPrincipal ensureProfile(DiscordIdentity discord) {
                return new SessionPrincipal(MEMBER_ID, discord.id(), discord.displayName(), discord.avatarUrl(), securityVersion.get());
            }

            @Override
            public SessionPrincipal requireProfile(UUID memberId) {
                assertEquals(MEMBER_ID, memberId);
                return new SessionPrincipal(MEMBER_ID,
                        discordLinked.get() ? "123456789012345678" : null,
                        "Builder", discordLinked.get() ? "https://cdn.example/avatar.png" : null,
                        securityVersion.get());
            }

            @Override
            public SessionPrincipal linkDiscord(UUID memberId, DiscordIdentity discord) {
                assertEquals(MEMBER_ID, memberId);
                discordLinked.set(true);
                long version = securityVersion.incrementAndGet();
                return new SessionPrincipal(MEMBER_ID, discord.id(), discord.displayName(), discord.avatarUrl(), version);
            }

            @Override
            public long contributionPointBalance(UUID memberId) {
                assertEquals(MEMBER_ID, memberId);
                return 1_000L;
            }
        };
        loginRepository = new FakeLoginRepository();
        discordLoginRepository = new FakeDiscordLoginRepository(discordLinked);
        WebLoginService webLogin = new WebLoginService(loginRepository, Duration.ofMinutes(10));
        DiscordWebLoginService discordWebLogin = new DiscordWebLoginService(
                discordLoginRepository, Duration.ofMinutes(10));
        server = new MarketplaceHttpServer(
                config, mapper, oauth, identity, webLogin, discordWebLogin, marketplace,
                Logger.getAnonymousLogger());
        server.start();
        baseUri = URI.create("http://127.0.0.1:" + server.port());
    }

    @AfterEach
    void stopServer() {
        if (server != null) server.close();
    }

    @Test
    void coversPublicAuthenticationAndEveryAuthenticatedApi() throws Exception {
        Response health = request("GET", "/api/health", null, null, null);
        assertEquals(200, health.status());
        assertEquals("ok", health.json().path("status").asText());

        Response anonymous = request("GET", "/api/auth/me", null, null, null);
        assertEquals(200, anonymous.status());
        assertFalse(anonymous.json().path("authenticated").asBoolean());

        Response categories = request("GET", "/api/categories", null, null, null);
        assertEquals(200, categories.status());
        assertEquals("Kits", categories.json().path("categories").get(0).asText());

        Response search = request("GET", "/api/items?q=kit&category=Kits&sort=price&direction=asc&page=2&pageSize=10", null, null, null);
        assertEquals(200, search.status());
        assertTrue(search.json().path("items").get(0).path("sellerMemberId").isMissingNode());
        assertTrue(search.json().path("items").get(0).path("sellerDiscordId").isMissingNode());
        assertEquals("kit", marketplace.lastSearch.text());
        assertEquals("Kits", marketplace.lastSearch.category());
        assertEquals(ItemSort.PRICE, marketplace.lastSearch.sort());
        assertEquals(SortDirection.ASC, marketplace.lastSearch.direction());
        assertEquals(2, marketplace.lastSearch.page());
        assertEquals(10, marketplace.lastSearch.pageSize());

        assertEquals(200, request("GET", "/api/items/" + ITEM_ID, null, null, null).status());
        Response publicShop = request("GET", "/api/shops/" + SHOP_ID, null, null, null);
        assertEquals(200, publicShop.status());
        assertEquals("Seller Shop", publicShop.json().path("shop").path("name").asText());
        assertTrue(publicShop.json().path("shop").path("ownerMemberId").isMissingNode());
        assertTrue(publicShop.json().path("shop").path("ownerDiscordId").isMissingNode());

        Login login = login();
        assertNotNull(login.sessionCookie());
        assertFalse(login.csrfToken().isBlank());

        Response me = request("GET", "/api/auth/me", login.sessionCookie(), null, null);
        assertTrue(me.json().path("authenticated").asBoolean());
        assertEquals(1_000L, me.json().path("balance").asLong());

        Response blockedUntilDiscordLink = request("GET", "/api/cart", login.sessionCookie(), null, null);
        assertEquals(409, blockedUntilDiscordLink.status());
        assertEquals("discord_link_required",
                blockedUntilDiscordLink.json().path("error").path("code").asText());

        login = linkDiscord(login);
        Response linkedMe = request("GET", "/api/auth/me", login.sessionCookie(), null, null);
        assertEquals("123456789012345678",
                linkedMe.json().path("user").path("discordUserId").asText());

        Response missingCsrf = request("POST", "/api/me/shop", login.sessionCookie(), null,
                "{\"name\":\"Builder Shop\",\"description\":\"Tools\"}");
        assertEquals(403, missingCsrf.status());
        assertEquals("csrf_failed", missingCsrf.json().path("error").path("code").asText());

        Response noShop = request("GET", "/api/me/shop", login.sessionCookie(), null, null);
        assertEquals(200, noShop.status());
        assertTrue(noShop.json().path("shop").isNull());

        Response createdShop = request("POST", "/api/me/shop", login.sessionCookie(), login.csrfToken(),
                "{\"name\":\"Builder Shop\",\"description\":\"Tools and kits\"}");
        assertEquals(201, createdShop.status());
        assertEquals("Builder Shop", createdShop.json().path("name").asText());

        Response updatedShop = request("PUT", "/api/me/shop", login.sessionCookie(), login.csrfToken(),
                "{\"name\":\"Updated Shop\",\"description\":\"Updated description\"}");
        assertEquals(200, updatedShop.status());
        assertEquals("Updated Shop", updatedShop.json().path("name").asText());

        String itemBody = """
                {"name":"PvP Kit","description":"Ready to fight","imageUrl":"https://example.com/kit.png",\
                "stock":5,"price":150,"category":"Kits","active":true}
                """;
        Response createdItem = request("POST", "/api/me/shop/items", login.sessionCookie(), login.csrfToken(), itemBody);
        assertEquals(201, createdItem.status());
        assertEquals("PvP Kit", createdItem.json().path("name").asText());

        Response updatedItem = request("PUT", "/api/me/shop/items/" + ITEM_ID, login.sessionCookie(), login.csrfToken(),
                itemBody.replace("PvP Kit", "PvP Kit Plus"));
        assertEquals(200, updatedItem.status());
        assertEquals("PvP Kit Plus", updatedItem.json().path("name").asText());

        Response deletedItem = request("DELETE", "/api/me/shop/items/" + ITEM_ID, login.sessionCookie(), login.csrfToken(), null);
        assertEquals(204, deletedItem.status());
        assertTrue(marketplace.itemDeactivated);

        Response cart = request("GET", "/api/cart", login.sessionCookie(), null, null);
        assertEquals(200, cart.status());
        assertEquals(0, cart.json().path("itemCount").asInt());

        Response cartUpdated = request("PUT", "/api/cart/items/" + ITEM_ID, login.sessionCookie(), login.csrfToken(),
                "{\"quantity\":2}");
        assertEquals(200, cartUpdated.status());
        assertEquals(2, cartUpdated.json().path("itemCount").asInt());

        Response cartRemoved = request("DELETE", "/api/cart/items/" + ITEM_ID, login.sessionCookie(), login.csrfToken(), null);
        assertEquals(200, cartRemoved.status());
        assertEquals(0, cartRemoved.json().path("itemCount").asInt());

        Response checkout = request("POST", "/api/cart/checkout", login.sessionCookie(), login.csrfToken(),
                "{\"expectedTotal\":300,\"items\":[{\"itemId\":\"" + ITEM_ID
                        + "\",\"quantity\":2,\"unitPrice\":150,\"version\":1}]}");
        assertEquals(201, checkout.status());
        assertEquals(ORDER_ID.toString(), checkout.json().path("id").asText());

        Response orders = request("GET", "/api/orders?limit=12", login.sessionCookie(), null, null);
        assertEquals(200, orders.status());
        assertEquals(12, marketplace.lastPurchaseLimit);
        assertEquals(1, orders.json().path("orders").size());

        Response sales = request("GET", "/api/sales?limit=18", login.sessionCookie(), null, null);
        assertEquals(200, sales.status());
        assertEquals(18, marketplace.lastSalesLimit);
        assertEquals(1, sales.json().path("sales").size());

        Response delivered = request("POST", "/api/sales/" + LINE_ID + "/delivered", login.sessionCookie(), login.csrfToken(), null);
        assertEquals(200, delivered.status());
        assertEquals("DELIVERED", delivered.json().path("status").asText());
        assertEquals(200, request("POST", "/api/orders/" + LINE_ID + "/confirm",
                login.sessionCookie(), login.csrfToken(), null).status());
        assertEquals(200, request("POST", "/api/orders/" + LINE_ID + "/cancel",
                login.sessionCookie(), login.csrfToken(), null).status());
        assertEquals(200, request("POST", "/api/orders/" + LINE_ID + "/dispute",
                login.sessionCookie(), login.csrfToken(), "{\"reason\":\"Delivery problem\"}").status());

        Response logout = request("POST", "/api/auth/logout", login.sessionCookie(), login.csrfToken(), null);
        assertEquals(200, logout.status());
        assertTrue(logout.json().path("loggedOut").asBoolean());
        assertTrue(cookieValue(logout.headers(), "core_session").isEmpty());

        Response afterLogout = request("GET", "/api/cart", login.sessionCookie(), null, null);
        assertEquals(401, afterLogout.status());
    }

    @Test
    void validatesMethodsContentTypeAndMissingResources() throws Exception {
        Response index = request("GET", "/", null, null, null);
        assertEquals(200, index.status());
        assertTrue(index.headers().firstValue("Content-Type").orElseThrow().startsWith("text/html"));
        assertTrue(index.headers().firstValue("Content-Security-Policy").isPresent());
        assertEquals(200, request("GET", "/app.js", null, null, null).status());
        assertEquals(405, request("POST", "/styles.css", null, null, null).status());
        assertEquals(401, request("GET", "/api/cart", null, null, null).status());
        assertEquals(400, request("GET", "/api/items?sort=unknown", null, null, null).status());
        assertEquals(404, request("GET", "/api/items/00000000-0000-0000-0000-000000000099", null, null, null).status());
        assertEquals(400, request("GET", "/api/items/not-a-uuid", null, null, null).status());

        assertEquals(404, request("GET", "/api/auth/login", null, null, null).status());
        Login login = login();
        Response invalidState = request(
                "GET", "/api/account/discord/callback?code=valid-code&state=wrong",
                login.sessionCookie() + "; core_oauth_state=wrong", null, null);
        assertEquals(302, invalidState.status());
        assertTrue(invalidState.headers().firstValue("Location").orElseThrow().contains("invalid_state"));

        login = linkDiscord(login);
        HttpRequest badContentType = HttpRequest.newBuilder(baseUri.resolve("/api/me/shop"))
                .header("Cookie", login.sessionCookie())
                .header("X-CSRF-Token", login.csrfToken())
                .header("Content-Type", "text/plain")
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .build();
        HttpResponse<String> response = client.send(badContentType, HttpResponse.BodyHandlers.ofString());
        assertEquals(415, response.statusCode());
    }

    @Test
    void supportsOneTimeDiscordBotWebsiteLogin() throws Exception {
        Response challenge = request("POST", "/api/auth/discord-bot/challenge", null, null, null);
        assertEquals(201, challenge.status());
        String challengeToken = challenge.json().path("challengeToken").asText();
        assertFalse(challengeToken.isBlank());
        assertTrue(challenge.json().path("command").asText().startsWith("/core web-login code:"));

        String completionBody = "{\"challengeToken\":\"" + challengeToken + "\",\"confirm\":false}";
        Response pending = request("POST", "/api/auth/discord-bot/complete", null, null, completionBody);
        assertEquals(202, pending.status());
        assertEquals("pending", pending.json().path("status").asText());

        discordLoginRepository.verifyLatest();
        Response ready = request("POST", "/api/auth/discord-bot/complete", null, null, completionBody);
        assertEquals(200, ready.status());
        assertEquals("ready", ready.json().path("status").asText());
        assertEquals("BuilderDiscord", ready.json().path("discordName").asText());

        Response completed = request("POST", "/api/auth/discord-bot/complete", null, null,
                "{\"challengeToken\":\"" + challengeToken + "\",\"confirm\":true}");
        assertEquals(200, completed.status());
        assertEquals("completed", completed.json().path("status").asText());
        String session = cookieValue(completed.headers(), "core_session");
        assertFalse(session.isBlank());

        Response me = request("GET", "/api/auth/me", "core_session=" + session, null, null);
        assertTrue(me.json().path("authenticated").asBoolean());
        assertEquals("123456789012345678", me.json().path("user").path("discordUserId").asText());
    }

    @Test
    void uploadsImagesWithMultipartAndServesThemFromThePublicPath() throws Exception {
        Login login = linkDiscord(login());
        Response shop = request("POST", "/api/me/shop", login.sessionCookie(), login.csrfToken(),
                "{\"name\":\"Builder Shop\",\"description\":\"Uploaded images\"}");
        assertEquals(201, shop.status());

        byte[] png = Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAusB9Y9ZP4sAAAAASUVORK5CYII=");
        String boundary = "CoreBuildersUploadBoundary";
        ByteArrayOutputStream multipart = new ByteArrayOutputStream();
        multipart.write(("--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"image\"; filename=\"listing.png\"\r\n"
                + "Content-Type: image/png\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
        multipart.write(png);
        multipart.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.US_ASCII));

        HttpRequest upload = HttpRequest.newBuilder(baseUri.resolve("/api/me/shop/images"))
                .header("Cookie", login.sessionCookie())
                .header("X-CSRF-Token", login.csrfToken())
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(multipart.toByteArray()))
                .build();
        HttpResponse<String> uploadResponse = client.send(upload, HttpResponse.BodyHandlers.ofString());
        assertEquals(201, uploadResponse.statusCode(), uploadResponse.body());
        JsonNode payload = mapper.readTree(uploadResponse.body());
        assertEquals("image/png", payload.path("contentType").asText());
        URI publicUrl = URI.create(payload.path("url").asText());
        assertTrue(publicUrl.getPath().startsWith("/uploads/images/" + MEMBER_ID + "/"));

        HttpResponse<byte[]> imageResponse = client.send(
                HttpRequest.newBuilder(baseUri.resolve(publicUrl.getPath())).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(200, imageResponse.statusCode());
        assertEquals("image/png", imageResponse.headers().firstValue("Content-Type").orElseThrow());
        assertArrayEquals(png, imageResponse.body());
    }

    private Login login() throws Exception {
        Response challenge = request("POST", "/api/auth/challenge", null, null, null);
        assertEquals(201, challenge.status());
        String challengeToken = challenge.json().path("challengeToken").asText();
        assertFalse(challengeToken.isBlank());
        assertTrue(challenge.json().path("command").asText().startsWith("/core login "));

        String completionBody = "{\"challengeToken\":\"" + challengeToken + "\",\"confirm\":false}";
        Response pending = request("POST", "/api/auth/complete", null, null, completionBody);
        assertEquals(202, pending.status());
        assertEquals("pending", pending.json().path("status").asText());

        loginRepository.verifyLatest();
        Response ready = request("POST", "/api/auth/complete", null, null, completionBody);
        assertEquals(200, ready.status());
        assertEquals("ready", ready.json().path("status").asText());
        assertEquals("Builder", ready.json().path("minecraftName").asText());
        Response completed = request("POST", "/api/auth/complete", null, null,
                "{\"challengeToken\":\"" + challengeToken + "\",\"confirm\":true}");
        assertEquals(200, completed.status());
        assertEquals("completed", completed.json().path("status").asText());
        String session = cookieValue(completed.headers(), "core_session");
        assertFalse(session.isBlank());

        Response me = request("GET", "/api/auth/me", "core_session=" + session, null, null);
        return new Login("core_session=" + session, me.json().path("csrfToken").asText());
    }

    private Login linkDiscord(Login login) throws Exception {
        Response start = request("GET", "/api/account/discord/link", login.sessionCookie(), null, null);
        assertEquals(302, start.status());
        String state = cookieValue(start.headers(), "core_oauth_state");
        assertFalse(state.isBlank());
        assertTrue(start.headers().firstValue("Location").orElseThrow().contains("state=" + state));

        Response callback = request(
                "GET", "/api/account/discord/callback?code=valid-code&state=" + state,
                login.sessionCookie() + "; core_oauth_state=" + state, null, null);
        assertEquals(302, callback.status());
        assertTrue(callback.headers().firstValue("Location").orElseThrow().contains("discord=linked"));
        String rotated = cookieValue(callback.headers(), "core_session");
        assertFalse(rotated.isBlank());
        assertNotEquals(login.sessionCookie(), "core_session=" + rotated);
        Response me = request("GET", "/api/auth/me", "core_session=" + rotated, null, null);
        return new Login("core_session=" + rotated, me.json().path("csrfToken").asText());
    }

    private Response request(String method, String path, String cookie, String csrf, String json) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(baseUri.resolve(path)).timeout(Duration.ofSeconds(5));
        if (cookie != null) builder.header("Cookie", cookie);
        if (csrf != null) builder.header("X-CSRF-Token", csrf);
        HttpRequest.BodyPublisher body = json == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(json);
        if (json != null) builder.header("Content-Type", "application/json");
        builder.method(method, body);
        HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        boolean jsonResponse = response.headers().firstValue("Content-Type")
                .map(value -> value.toLowerCase().contains("application/json"))
                .orElse(false);
        JsonNode parsed = response.body().isBlank() || !jsonResponse
                ? mapper.nullNode()
                : mapper.readTree(response.body());
        return new Response(response.statusCode(), response.headers(), parsed);
    }

    private static String cookieValue(java.net.http.HttpHeaders headers, String name) {
        for (String header : headers.allValues("Set-Cookie")) {
            String prefix = name + "=";
            if (header.startsWith(prefix)) return header.substring(prefix.length(), header.indexOf(';'));
        }
        return "";
    }

    private record Login(String sessionCookie, String csrfToken) {}
    private record Response(int status, java.net.http.HttpHeaders headers, JsonNode json) {}

    private static final class FakeLoginRepository implements WebLoginChallengeRepository {
        private NewChallenge challenge;
        private boolean verified;
        private boolean consumed;

        @Override
        public void create(NewChallenge challenge) {
            this.challenge = challenge;
            this.verified = false;
            this.consumed = false;
        }

        @Override
        public VerificationResult verify(
                String verificationCodeHash,
                UUID minecraftUuid,
                String minecraftName,
                Instant now
        ) {
            if (challenge == null || !challenge.verificationCodeHash().equals(verificationCodeHash)) {
                return new VerificationResult(VerificationStatus.INVALID, null);
            }
            verified = true;
            return new VerificationResult(VerificationStatus.VERIFIED, MEMBER_ID);
        }

        @Override
        public CompletionResult complete(String browserTokenHash, Instant now, boolean consume) {
            if (challenge == null || !challenge.browserTokenHash().equals(browserTokenHash)) {
                return new CompletionResult(CompletionStatus.INVALID, null, null);
            }
            if (consumed) {
                return new CompletionResult(CompletionStatus.USED, MEMBER_ID, "Builder");
            }
            if (!verified) {
                return new CompletionResult(CompletionStatus.PENDING, null, null);
            }
            if (!consume) return new CompletionResult(CompletionStatus.READY, MEMBER_ID, "Builder");
            consumed = true;
            return new CompletionResult(CompletionStatus.COMPLETED, MEMBER_ID, "Builder");
        }

        void verifyLatest() {
            assertNotNull(challenge);
            verified = true;
        }
    }

    private static final class FakeDiscordLoginRepository implements DiscordWebLoginChallengeRepository {
        private final AtomicBoolean discordLinked;
        private NewChallenge challenge;
        private boolean verified;
        private boolean consumed;

        private FakeDiscordLoginRepository(AtomicBoolean discordLinked) {
            this.discordLinked = discordLinked;
        }

        @Override
        public void create(NewChallenge challenge) {
            this.challenge = challenge;
            this.verified = false;
            this.consumed = false;
        }

        @Override
        public VerificationResult verify(
                String verificationCodeHash,
                String discordUserId,
                String discordUsername,
                String discordAvatarUrl,
                Instant now
        ) {
            if (challenge == null || !challenge.verificationCodeHash().equals(verificationCodeHash)) {
                return new VerificationResult(VerificationStatus.INVALID, null);
            }
            verified = true;
            discordLinked.set(true);
            return new VerificationResult(VerificationStatus.VERIFIED, MEMBER_ID);
        }

        @Override
        public CompletionResult complete(String browserTokenHash, Instant now, boolean consume) {
            if (challenge == null || !challenge.browserTokenHash().equals(browserTokenHash)) {
                return new CompletionResult(CompletionStatus.INVALID, null, null, null);
            }
            if (consumed) {
                return new CompletionResult(CompletionStatus.USED, MEMBER_ID,
                        "BuilderDiscord", "https://cdn.example/avatar.png");
            }
            if (!verified) {
                return new CompletionResult(CompletionStatus.PENDING, null, null, null);
            }
            if (!consume) {
                return new CompletionResult(CompletionStatus.READY, MEMBER_ID,
                        "BuilderDiscord", "https://cdn.example/avatar.png");
            }
            consumed = true;
            return new CompletionResult(CompletionStatus.COMPLETED, MEMBER_ID,
                    "BuilderDiscord", "https://cdn.example/avatar.png");
        }

        void verifyLatest() {
            assertNotNull(challenge);
            verified = true;
            discordLinked.set(true);
        }
    }

    private static final class FakeOAuth implements DiscordOAuth {
        @Override
        public URI authorizationUri(String state) {
            return URI.create("https://discord.test/oauth?state=" + state);
        }

        @Override
        public DiscordIdentity exchange(String authorizationCode) {
            assertEquals("valid-code", authorizationCode);
            return new DiscordIdentity("123456789012345678", "builder", "Builder", "https://cdn.example/avatar.png");
        }
    }

    private static final class FakeMarketplace implements MarketplaceOperations {
        private final PlayerShop sellerShop = shop(SHOP_ID, SELLER_ID, "Seller Shop");
        private PlayerShop ownerShop;
        private MarketplaceItem item = item("PvP Kit", true);
        private ItemSearch lastSearch;
        private boolean itemDeactivated;
        private int cartQuantity;
        private int lastPurchaseLimit;
        private int lastSalesLimit;

        @Override
        public ItemPage searchItems(ItemSearch search) {
            lastSearch = search;
            return new ItemPage(List.of(item), search.page(), search.pageSize(), 1);
        }

        @Override
        public Optional<MarketplaceItem> findItem(UUID itemId) {
            return ITEM_ID.equals(itemId) ? Optional.of(item) : Optional.empty();
        }

        @Override
        public List<String> categories() {
            return List.of("Kits", "Blocks");
        }

        @Override
        public Optional<PlayerShop> findShop(UUID shopId) {
            if (SHOP_ID.equals(shopId)) return Optional.of(sellerShop);
            if (ownerShop != null && ownerShop.id().equals(shopId)) return Optional.of(ownerShop);
            return Optional.empty();
        }

        @Override
        public List<MarketplaceItem> shopItems(UUID shopId) {
            return SHOP_ID.equals(shopId) ? List.of(item) : List.of();
        }

        @Override
        public Optional<PlayerShop> findShopByOwner(UUID ownerMemberId) {
            return MEMBER_ID.equals(ownerMemberId) ? Optional.ofNullable(ownerShop) : Optional.empty();
        }

        @Override
        public PlayerShop createShop(UUID ownerMemberId, ShopInput input) {
            assertEquals(MEMBER_ID, ownerMemberId);
            ownerShop = shop(UUID.fromString("10000000-0000-0000-0000-000000000002"), MEMBER_ID, input.name(), input.description());
            return ownerShop;
        }

        @Override
        public PlayerShop updateShop(UUID ownerMemberId, ShopInput input) {
            assertNotNull(ownerShop);
            ownerShop = shop(ownerShop.id(), ownerMemberId, input.name(), input.description());
            return ownerShop;
        }

        @Override
        public List<MarketplaceItem> ownerItems(UUID ownerMemberId) {
            return MEMBER_ID.equals(ownerMemberId) && ownerShop != null ? List.of(item) : List.of();
        }

        @Override
        public MarketplaceItem createItem(UUID ownerMemberId, ItemInput input) {
            item = fromInput(input);
            return item;
        }

        @Override
        public MarketplaceItem updateItem(UUID ownerMemberId, UUID itemId, ItemInput input) {
            assertEquals(ITEM_ID, itemId);
            item = fromInput(input);
            return item;
        }

        @Override
        public void deactivateItem(UUID ownerMemberId, UUID itemId) {
            itemDeactivated = true;
        }

        @Override
        public MarketplaceCart cart(UUID memberId) {
            return cartValue();
        }

        @Override
        public MarketplaceCart setCartQuantity(UUID memberId, UUID itemId, int quantity) {
            cartQuantity = quantity;
            return cartValue();
        }

        @Override
        public MarketplaceCart removeCartItem(UUID memberId, UUID itemId) {
            cartQuantity = 0;
            return cartValue();
        }

        @Override
        public MarketplaceOrder checkout(UUID buyerMemberId, String actorDiscordId, CheckoutRequest request) {
            assertEquals(MEMBER_ID, buyerMemberId);
            assertEquals("123456789012345678", actorDiscordId);
            assertEquals(300L, request.expectedTotal());
            return order();
        }

        @Override
        public List<MarketplaceOrder> purchases(UUID buyerMemberId, int limit) {
            lastPurchaseLimit = limit;
            return List.of(order());
        }

        @Override
        public List<MarketplaceOrderLine> sales(UUID sellerMemberId, int limit) {
            lastSalesLimit = limit;
            return List.of(line("PENDING_DELIVERY", null));
        }

        @Override
        public MarketplaceOrderLine markDelivered(UUID sellerMemberId, UUID lineId) {
            assertEquals(LINE_ID, lineId);
            return line("DELIVERED", NOW.plusSeconds(60));
        }

        @Override public MarketplaceOrderLine confirmDelivery(UUID buyerMemberId, UUID lineId) {
            return line("SETTLED", NOW.plusSeconds(60));
        }
        @Override public MarketplaceOrderLine cancelLine(UUID buyerMemberId, UUID lineId) {
            return line("CANCELLED", null);
        }
        @Override public MarketplaceOrderLine disputeLine(UUID buyerMemberId, UUID lineId, String reason) {
            return line("DISPUTED", NOW.plusSeconds(60));
        }

        private MarketplaceCart cartValue() {
            List<CartLine> lines = cartQuantity == 0 ? List.of() : List.of(new CartLine(item, cartQuantity, item.price() * cartQuantity));
            return new MarketplaceCart(CART_ID, MEMBER_ID, lines, item.price() * cartQuantity, cartQuantity, NOW);
        }

        private MarketplaceOrder order() {
            return new MarketplaceOrder(ORDER_ID, MEMBER_ID, 300L, "HELD", List.of(line("PENDING_DELIVERY", null)), NOW, NOW);
        }

        private MarketplaceOrderLine line(String status, Instant deliveredAt) {
            return new MarketplaceOrderLine(LINE_ID, ORDER_ID, ITEM_ID, SHOP_ID, SELLER_ID, MEMBER_ID,
                    "Builder", "Seller Shop", item.name(), item.imageUrl(), item.category(), 2, 150L, 300L,
                    status, "SETTLED".equals(status), NOW, deliveredAt,
                    "SETTLED".equals(status) ? NOW.plusSeconds(120) : null,
                    "DISPUTED".equals(status) ? NOW.plusSeconds(120) : null,
                    "DISPUTED".equals(status) ? "Delivery problem" : null,
                    null, null, null);
        }

        private MarketplaceItem fromInput(ItemInput input) {
            return new MarketplaceItem(ITEM_ID, SHOP_ID, "Seller Shop", SELLER_ID,
                    "987654321098765432", "Seller", input.name(), input.description(), input.imageUrl(),
                    input.stock(), input.price(), input.category(), input.active(), 1L, NOW, NOW);
        }

        private static PlayerShop shop(UUID id, UUID ownerId, String name) {
            return shop(id, ownerId, name, "Marketplace shop");
        }

        private static PlayerShop shop(UUID id, UUID ownerId, String name, String description) {
            String discordId = MEMBER_ID.equals(ownerId) ? "123456789012345678" : "987654321098765432";
            String username = MEMBER_ID.equals(ownerId) ? "Builder" : "Seller";
            return new PlayerShop(id, ownerId, discordId, username, name, description, true, NOW, NOW);
        }

        private static MarketplaceItem item(String name, boolean active) {
            return new MarketplaceItem(ITEM_ID, SHOP_ID, "Seller Shop", SELLER_ID,
                    "987654321098765432", "Seller", name, "Ready to fight",
                    "https://example.com/kit.png", 5, 150L, "Kits", active, 1L, NOW, NOW);
        }
    }
}
