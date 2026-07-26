package com.corebuilders.bot.web;

import com.corebuilders.bot.config.WebsiteConfig;
import com.corebuilders.bot.model.MarketplaceModels.*;
import com.corebuilders.bot.service.MarketplaceException;
import com.corebuilders.bot.service.MarketplaceOperations;
import com.corebuilders.bot.web.auth.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Embedded same-origin HTTP API and static JavaScript frontend for the player marketplace. */
public final class MarketplaceHttpServer implements AutoCloseable {
    private static final String SESSION_COOKIE = "core_session";
    private static final String STATE_COOKIE = "core_oauth_state";

    private final WebsiteConfig config;
    private final ObjectMapper mapper;
    private final DiscordOAuth oauth;
    private final WebsiteIdentity identity;
    private final MarketplaceOperations marketplace;
    private final Logger logger;
    private final WebSessionStore sessions;
    private final OAuthStateStore states;
    private final HttpServer server;
    private final ExecutorService executor;

    public MarketplaceHttpServer(
            WebsiteConfig config,
            ObjectMapper mapper,
            DiscordOAuth oauth,
            WebsiteIdentity identity,
            MarketplaceOperations marketplace,
            Logger logger
    ) throws IOException {
        this.config = config;
        this.mapper = mapper;
        this.oauth = oauth;
        this.identity = identity;
        this.marketplace = marketplace;
        this.logger = logger;
        this.sessions = new WebSessionStore(config.sessionLifetime());
        this.states = new OAuthStateStore();
        this.server = HttpServer.create(new InetSocketAddress(config.bindAddress(), config.port()), 0);
        this.executor = Executors.newFixedThreadPool(config.workerThreads(), daemonThreads("core-marketplace-http"));
        this.server.setExecutor(executor);
        this.server.createContext("/", this::handle);
    }

    public void start() {
        server.start();
        logger.info("Marketplace website listening on " + config.bindAddress() + ":" + port()
                + "; public URL " + config.publicBaseUrl());
    }

    public int port() {
        return server.getAddress().getPort();
    }

    private void handle(HttpExchange exchange) {
        try {
            applySecurityHeaders(exchange.getResponseHeaders());
            String path = normalizePath(exchange.getRequestURI().getPath());
            if (path.startsWith("/api/")) {
                routeApi(exchange, path);
            } else {
                serveStatic(exchange, path);
            }
        } catch (HttpStatusException error) {
            sendProblem(exchange, error.status, error.code, error.getMessage());
        } catch (MarketplaceException error) {
            sendProblem(exchange, marketplaceStatus(error.code()), error.code().name().toLowerCase(Locale.ROOT), error.getMessage());
        } catch (OAuthException error) {
            sendProblem(exchange, error.code() == OAuthException.Code.NOT_IN_GUILD ? 403 : 502,
                    error.code().name().toLowerCase(Locale.ROOT), error.getMessage());
        } catch (IllegalArgumentException error) {
            sendProblem(exchange, 400, "invalid_request", safeMessage(error, "Invalid request."));
        } catch (IllegalStateException error) {
            sendProblem(exchange, 409, "conflict", safeMessage(error, "Request could not be completed."));
        } catch (RequestTooLargeException error) {
            sendProblem(exchange, 413, "request_too_large", error.getMessage());
        } catch (Exception error) {
            logger.log(Level.SEVERE, "Marketplace HTTP request failed", error);
            sendProblem(exchange, 500, "internal_error", "The server could not complete this request.");
        } finally {
            exchange.close();
        }
    }

    private void routeApi(HttpExchange exchange, String path) throws IOException {
        String method = exchange.getRequestMethod().toUpperCase(Locale.ROOT);
        List<String> parts = pathParts(path);

        if (path.equals("/api/health") && method.equals("GET")) {
            sendJson(exchange, 200, Map.of("status", "ok", "service", "core-builders-marketplace"));
            return;
        }
        if (path.equals("/api/auth/login") && method.equals("GET")) {
            startLogin(exchange);
            return;
        }
        if (path.equals("/api/auth/callback") && method.equals("GET")) {
            finishLogin(exchange);
            return;
        }
        if (path.equals("/api/auth/me") && method.equals("GET")) {
            authMe(exchange);
            return;
        }
        if (path.equals("/api/auth/logout") && method.equals("POST")) {
            WebSessionStore.Session session = requireSession(exchange);
            requireCsrf(exchange, session);
            sessions.destroy(session.id());
            clearCookie(exchange, SESSION_COOKIE);
            sendJson(exchange, 200, Map.of("loggedOut", true));
            return;
        }
        if (path.equals("/api/categories") && method.equals("GET")) {
            sendJson(exchange, 200, Map.of("categories", marketplace.categories()));
            return;
        }
        if (path.equals("/api/items") && method.equals("GET")) {
            Map<String, String> query = query(exchange.getRequestURI());
            ItemSearch search = new ItemSearch(
                    query.get("q"), query.get("category"), ItemSort.parse(query.get("sort")),
                    SortDirection.parse(query.get("direction")), integer(query.get("page"), 1),
                    integer(query.get("pageSize"), 20));
            sendJson(exchange, 200, marketplace.searchItems(search));
            return;
        }
        if (parts.size() == 3 && parts.get(0).equals("api") && parts.get(1).equals("items") && method.equals("GET")) {
            MarketplaceItem item = marketplace.findItem(uuid(parts.get(2)))
                    .orElseThrow(() -> MarketplaceException.notFound("Marketplace item not found."));
            sendJson(exchange, 200, item);
            return;
        }
        if (parts.size() == 3 && parts.get(0).equals("api") && parts.get(1).equals("shops") && method.equals("GET")) {
            UUID shopId = uuid(parts.get(2));
            PlayerShop shop = marketplace.findShop(shopId)
                    .orElseThrow(() -> MarketplaceException.notFound("Shop not found."));
            sendJson(exchange, 200, new ShopPayload(shop, marketplace.shopItems(shopId)));
            return;
        }

        WebSessionStore.Session session = requireSession(exchange);
        SessionPrincipal principal = session.principal();

        if (path.equals("/api/me/shop") && method.equals("GET")) {
            Optional<PlayerShop> shop = marketplace.findShopByOwner(principal.memberId());
            List<MarketplaceItem> items = shop.isPresent() ? marketplace.ownerItems(principal.memberId()) : List.of();
            sendJson(exchange, 200, new ShopPayload(shop.orElse(null), items));
            return;
        }
        if (path.equals("/api/me/shop") && method.equals("POST")) {
            requireCsrf(exchange, session);
            PlayerShop created = marketplace.createShop(principal.memberId(), readJson(exchange, ShopInput.class));
            sendJson(exchange, 201, created);
            return;
        }
        if (path.equals("/api/me/shop") && method.equals("PUT")) {
            requireCsrf(exchange, session);
            sendJson(exchange, 200, marketplace.updateShop(principal.memberId(), readJson(exchange, ShopInput.class)));
            return;
        }
        if (path.equals("/api/me/shop/items") && method.equals("POST")) {
            requireCsrf(exchange, session);
            MarketplaceItem created = marketplace.createItem(principal.memberId(), readJson(exchange, ItemInput.class));
            sendJson(exchange, 201, created);
            return;
        }
        if (parts.size() == 5 && parts.get(0).equals("api") && parts.get(1).equals("me")
                && parts.get(2).equals("shop") && parts.get(3).equals("items")) {
            UUID itemId = uuid(parts.get(4));
            requireCsrf(exchange, session);
            if (method.equals("PUT")) {
                sendJson(exchange, 200, marketplace.updateItem(
                        principal.memberId(), itemId, readJson(exchange, ItemInput.class)));
                return;
            }
            if (method.equals("DELETE")) {
                marketplace.deactivateItem(principal.memberId(), itemId);
                sendEmpty(exchange, 204);
                return;
            }
        }
        if (path.equals("/api/cart") && method.equals("GET")) {
            sendJson(exchange, 200, marketplace.cart(principal.memberId()));
            return;
        }
        if (parts.size() == 4 && parts.get(0).equals("api") && parts.get(1).equals("cart")
                && parts.get(2).equals("items")) {
            UUID itemId = uuid(parts.get(3));
            requireCsrf(exchange, session);
            if (method.equals("PUT")) {
                QuantityRequest request = readJson(exchange, QuantityRequest.class);
                sendJson(exchange, 200, marketplace.setCartQuantity(principal.memberId(), itemId, request.quantity()));
                return;
            }
            if (method.equals("DELETE")) {
                sendJson(exchange, 200, marketplace.removeCartItem(principal.memberId(), itemId));
                return;
            }
        }
        if (path.equals("/api/cart/checkout") && method.equals("POST")) {
            requireCsrf(exchange, session);
            sendJson(exchange, 201, marketplace.checkout(principal.memberId(), principal.discordUserId()));
            return;
        }
        if (path.equals("/api/orders") && method.equals("GET")) {
            int limit = integer(query(exchange.getRequestURI()).get("limit"), 25);
            sendJson(exchange, 200, Map.of("orders", marketplace.purchases(principal.memberId(), limit)));
            return;
        }
        if (path.equals("/api/sales") && method.equals("GET")) {
            int limit = integer(query(exchange.getRequestURI()).get("limit"), 50);
            sendJson(exchange, 200, Map.of("sales", marketplace.sales(principal.memberId(), limit)));
            return;
        }
        if (parts.size() == 4 && parts.get(0).equals("api") && parts.get(1).equals("sales")
                && parts.get(3).equals("delivered") && method.equals("POST")) {
            requireCsrf(exchange, session);
            sendJson(exchange, 200, marketplace.markDelivered(principal.memberId(), uuid(parts.get(2))));
            return;
        }

        sendProblem(exchange, 404, "not_found", "API endpoint not found.");
    }

    private void startLogin(HttpExchange exchange) throws IOException {
        String state = states.create();
        setCookie(exchange, STATE_COOKIE, state, Duration.ofMinutes(10), true);
        redirect(exchange, oauth.authorizationUri(state));
    }

    private void finishLogin(HttpExchange exchange) throws IOException {
        Map<String, String> query = query(exchange.getRequestURI());
        String providerError = query.get("error");
        if (providerError != null) {
            clearCookie(exchange, STATE_COOKIE);
            redirect(exchange, siteUri("/?login=error&code=" + encode(providerError)));
            return;
        }
        String state = query.get("state");
        String stateCookie = cookie(exchange, STATE_COOKIE).orElse(null);
        if (state == null || stateCookie == null || !constantTimeEquals(state, stateCookie) || !states.consume(state)) {
            clearCookie(exchange, STATE_COOKIE);
            redirect(exchange, siteUri("/?login=error&code=invalid_state"));
            return;
        }
        clearCookie(exchange, STATE_COOKIE);
        try {
            DiscordIdentity discord = oauth.exchange(query.get("code"));
            SessionPrincipal principal = identity.ensureProfile(discord);
            WebSessionStore.Session session = sessions.create(principal);
            setCookie(exchange, SESSION_COOKIE, session.id(), config.sessionLifetime(), true);
            redirect(exchange, siteUri("/?login=success"));
        } catch (OAuthException error) {
            redirect(exchange, siteUri("/?login=error&code=" + encode(error.code().name().toLowerCase(Locale.ROOT))));
        } catch (IllegalStateException error) {
            logger.log(Level.WARNING, "Discord login profile could not be activated", error);
            redirect(exchange, siteUri("/?login=error&code=profile_unavailable"));
        }
    }

    private void authMe(HttpExchange exchange) throws IOException {
        Optional<WebSessionStore.Session> session = optionalSession(exchange);
        if (session.isEmpty()) {
            sendJson(exchange, 200, new MePayload(false, null, 0L, null));
            return;
        }
        WebSessionStore.Session current = session.get();
        sendJson(exchange, 200, new MePayload(true, current.principal(),
                identity.contributionPointBalance(current.principal().memberId()), current.csrfToken()));
    }

    private WebSessionStore.Session requireSession(HttpExchange exchange) {
        return optionalSession(exchange).orElseThrow(() -> new HttpStatusException(401, "unauthorized", "Log in with Discord first."));
    }

    private Optional<WebSessionStore.Session> optionalSession(HttpExchange exchange) {
        return cookie(exchange, SESSION_COOKIE).flatMap(sessions::find);
    }

    private void requireCsrf(HttpExchange exchange, WebSessionStore.Session session) {
        String supplied = exchange.getRequestHeaders().getFirst("X-CSRF-Token");
        if (!constantTimeEquals(session.csrfToken(), supplied)) {
            throw new HttpStatusException(403, "csrf_failed", "The request CSRF token is missing or invalid.");
        }
    }

    private <T> T readJson(HttpExchange exchange, Class<T> type) throws IOException {
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("application/json")) {
            throw new HttpStatusException(415, "unsupported_media_type", "Use application/json.");
        }
        byte[] body = readLimited(exchange.getRequestBody(), config.maxRequestBytes());
        if (body.length == 0) throw new IllegalArgumentException("JSON request body is required.");
        try {
            return mapper.readValue(body, type);
        } catch (com.fasterxml.jackson.core.JsonProcessingException error) {
            throw new IllegalArgumentException("Invalid JSON request body.", error);
        }
    }

    private void serveStatic(HttpExchange exchange, String path) throws IOException {
        String method = exchange.getRequestMethod().toUpperCase(Locale.ROOT);
        if (!(method.equals("GET") || method.equals("HEAD"))) {
            sendProblem(exchange, 405, "method_not_allowed", "Static files support GET and HEAD only.");
            return;
        }
        String resource = switch (path) {
            case "/", "/index.html" -> "/web/index.html";
            case "/app.js" -> "/web/app.js";
            case "/styles.css" -> "/web/styles.css";
            default -> "/web/index.html";
        };
        byte[] bytes;
        try (InputStream input = MarketplaceHttpServer.class.getResourceAsStream(resource)) {
            if (input == null) {
                sendProblem(exchange, 404, "not_found", "Frontend resource not found.");
                return;
            }
            bytes = input.readAllBytes();
        }
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", contentType(resource));
        headers.set("Cache-Control", resource.endsWith("index.html") ? "no-cache" : "public, max-age=3600");
        if (method.equals("HEAD")) {
            exchange.sendResponseHeaders(200, -1);
            return;
        }
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    private void sendJson(HttpExchange exchange, int status, Object value) throws IOException {
        byte[] body = mapper.writeValueAsBytes(value);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
    }

    private void sendProblem(HttpExchange exchange, int status, String code, String message) {
        try {
            sendJson(exchange, status, new ProblemPayload(new Problem(code, message)));
        } catch (IOException ignored) {
            // The client may have disconnected while the error was being written.
        }
    }

    private void sendEmpty(HttpExchange exchange, int status) throws IOException {
        exchange.sendResponseHeaders(status, -1);
    }

    private void redirect(HttpExchange exchange, URI location) throws IOException {
        exchange.getResponseHeaders().set("Location", location.toString());
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(302, -1);
    }

    private void setCookie(HttpExchange exchange, String name, String value, Duration maxAge, boolean httpOnly) {
        StringBuilder cookie = new StringBuilder(name).append('=').append(value)
                .append("; Path=/; SameSite=Lax; Max-Age=").append(Math.max(0L, maxAge.toSeconds()));
        if (httpOnly) cookie.append("; HttpOnly");
        if (config.secureCookies()) cookie.append("; Secure");
        exchange.getResponseHeaders().add("Set-Cookie", cookie.toString());
    }

    private void clearCookie(HttpExchange exchange, String name) {
        String cookie = name + "=; Path=/; SameSite=Lax; Max-Age=0; HttpOnly"
                + (config.secureCookies() ? "; Secure" : "");
        exchange.getResponseHeaders().add("Set-Cookie", cookie);
    }

    private static Optional<String> cookie(HttpExchange exchange, String name) {
        List<String> headers = exchange.getRequestHeaders().getOrDefault("Cookie", List.of());
        for (String header : headers) {
            for (String part : header.split(";")) {
                String[] pair = part.trim().split("=", 2);
                if (pair.length == 2 && pair[0].equals(name)) return Optional.of(pair[1]);
            }
        }
        return Optional.empty();
    }

    private static byte[] readLimited(InputStream input, int maximum) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(maximum, 8192));
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > maximum) throw new RequestTooLargeException("Request body exceeds " + maximum + " bytes.");
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private URI siteUri(String suffix) {
        return URI.create(config.publicBaseUrl().toString() + suffix);
    }

    private static Map<String, String> query(URI uri) {
        Map<String, String> values = new LinkedHashMap<>();
        String raw = uri.getRawQuery();
        if (raw == null || raw.isBlank()) return values;
        for (String part : raw.split("&")) {
            String[] pair = part.split("=", 2);
            String key = decode(pair[0]);
            String value = pair.length == 2 ? decode(pair[1]) : "";
            values.putIfAbsent(key, value);
        }
        return values;
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static String encode(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static int integer(String value, int fallback) {
        if (value == null || value.isBlank()) return fallback;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("Expected an integer but received: " + value);
        }
    }

    private static UUID uuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (RuntimeException error) {
            throw new IllegalArgumentException("Invalid resource ID.");
        }
    }

    private static List<String> pathParts(String path) {
        if (path.equals("/")) return List.of();
        return Arrays.stream(path.substring(1).split("/"))
                .filter(part -> !part.isBlank())
                .toList();
    }

    private static String normalizePath(String value) {
        if (value == null || value.isBlank()) return "/";
        if (value.length() > 1 && value.endsWith("/")) return value.substring(0, value.length() - 1);
        return value;
    }

    private static boolean constantTimeEquals(String expected, String supplied) {
        if (expected == null || supplied == null) return false;
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8));
    }

    private static int marketplaceStatus(MarketplaceException.Code code) {
        return switch (code) {
            case VALIDATION -> 400;
            case NOT_FOUND -> 404;
            case CONFLICT, OUT_OF_STOCK, INSUFFICIENT_FUNDS -> 409;
            case FORBIDDEN -> 403;
        };
    }

    private static void applySecurityHeaders(Headers headers) {
        headers.set("X-Content-Type-Options", "nosniff");
        headers.set("Referrer-Policy", "no-referrer");
        headers.set("X-Frame-Options", "DENY");
        headers.set("Permissions-Policy", "camera=(), microphone=(), geolocation=()");
        headers.set("Content-Security-Policy",
                "default-src 'self'; img-src 'self' https: data:; style-src 'self'; script-src 'self'; "
                        + "connect-src 'self'; frame-ancestors 'none'; base-uri 'self'; form-action 'self' https://discord.com");
    }

    private static String contentType(String resource) {
        if (resource.endsWith(".js")) return "text/javascript; charset=utf-8";
        if (resource.endsWith(".css")) return "text/css; charset=utf-8";
        return "text/html; charset=utf-8";
    }

    private static String safeMessage(Throwable error, String fallback) {
        return error.getMessage() == null || error.getMessage().isBlank() ? fallback : error.getMessage();
    }

    private static ThreadFactory daemonThreads(String prefix) {
        AtomicInteger counter = new AtomicInteger();
        return task -> {
            Thread thread = new Thread(task, prefix + '-' + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    @Override
    public void close() {
        sessions.clear();
        server.stop(1);
        executor.shutdownNow();
    }

    private record QuantityRequest(int quantity) {}
    private record ShopPayload(PlayerShop shop, List<MarketplaceItem> items) {}
    private record MePayload(boolean authenticated, SessionPrincipal user, long balance, String csrfToken) {}
    private record Problem(String code, String message) {}
    private record ProblemPayload(Problem error) {}

    private static final class RequestTooLargeException extends RuntimeException {
        private RequestTooLargeException(String message) { super(message); }
    }

    private static final class HttpStatusException extends RuntimeException {
        private final int status;
        private final String code;

        private HttpStatusException(int status, String code, String message) {
            super(message);
            this.status = status;
            this.code = code;
        }
    }
}
