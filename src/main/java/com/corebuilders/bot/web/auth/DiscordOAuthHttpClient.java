package com.corebuilders.bot.web.auth;

import com.corebuilders.bot.config.WebsiteConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;

import static com.corebuilders.bot.web.auth.OAuthException.Code.*;

/** Discord authorization-code OAuth implementation. Access tokens are never persisted. */
public final class DiscordOAuthHttpClient implements DiscordOAuth {
    private static final URI DEFAULT_AUTHORIZE = URI.create("https://discord.com/oauth2/authorize");
    private static final URI DEFAULT_TOKEN = URI.create("https://discord.com/api/v10/oauth2/token");
    private static final URI DEFAULT_USER = URI.create("https://discord.com/api/v10/users/@me");
    private static final URI DEFAULT_GUILDS = URI.create("https://discord.com/api/v10/users/@me/guilds");

    private final WebsiteConfig config;
    private final String guildId;
    private final ObjectMapper mapper;
    private final HttpClient client;
    private final URI authorizeEndpoint;
    private final URI tokenEndpoint;
    private final URI userEndpoint;
    private final URI guildsEndpoint;

    public DiscordOAuthHttpClient(WebsiteConfig config, String guildId, ObjectMapper mapper) {
        this(config, guildId, mapper,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build(),
                DEFAULT_AUTHORIZE, DEFAULT_TOKEN, DEFAULT_USER, DEFAULT_GUILDS);
    }

    DiscordOAuthHttpClient(
            WebsiteConfig config,
            String guildId,
            ObjectMapper mapper,
            HttpClient client,
            URI authorizeEndpoint,
            URI tokenEndpoint,
            URI userEndpoint,
            URI guildsEndpoint
    ) {
        this.config = Objects.requireNonNull(config, "config");
        this.guildId = Objects.requireNonNull(guildId, "guildId");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.client = Objects.requireNonNull(client, "client");
        this.authorizeEndpoint = Objects.requireNonNull(authorizeEndpoint, "authorizeEndpoint");
        this.tokenEndpoint = Objects.requireNonNull(tokenEndpoint, "tokenEndpoint");
        this.userEndpoint = Objects.requireNonNull(userEndpoint, "userEndpoint");
        this.guildsEndpoint = Objects.requireNonNull(guildsEndpoint, "guildsEndpoint");
    }

    @Override
    public URI authorizationUri(String state) {
        if (state == null || state.isBlank()) throw new IllegalArgumentException("OAuth state is required.");
        String scope = config.requireGuildMembership() ? "identify guilds" : "identify";
        String query = "client_id=" + queryEncode(config.oauthClientId())
                + "&response_type=code"
                + "&redirect_uri=" + queryEncode(config.oauthRedirectUri().toString())
                + "&scope=" + queryEncode(scope)
                + "&state=" + queryEncode(state);
        return URI.create(authorizeEndpoint + "?" + query);
    }

    @Override
    public DiscordIdentity exchange(String authorizationCode) {
        if (authorizationCode == null || authorizationCode.isBlank()) {
            throw new OAuthException(ACCESS_DENIED, "Discord did not provide an authorization code.");
        }
        String accessToken = exchangeToken(authorizationCode.trim());
        JsonNode user = getJson(userEndpoint, accessToken);
        String id = requiredText(user, "id");
        String username = requiredText(user, "username");
        String globalName = optionalText(user, "global_name");
        if (config.requireGuildMembership() && !belongsToGuild(accessToken)) {
            throw new OAuthException(NOT_IN_GUILD, "Join the configured Core Builders Discord server before using the marketplace.");
        }
        String avatar = avatarUrl(id, optionalText(user, "avatar"));
        return new DiscordIdentity(id, username, globalName == null ? username : globalName, avatar);
    }

    private String exchangeToken(String code) {
        String body = "client_id=" + formEncode(config.oauthClientId())
                + "&client_secret=" + formEncode(config.oauthClientSecret())
                + "&grant_type=authorization_code"
                + "&code=" + formEncode(code)
                + "&redirect_uri=" + formEncode(config.oauthRedirectUri().toString());
        HttpRequest request = HttpRequest.newBuilder(tokenEndpoint)
                .timeout(Duration.ofSeconds(15))
                .header("Accept", "application/json")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        JsonNode response = sendJson(request);
        String token = optionalText(response, "access_token");
        if (token == null) throw new OAuthException(INVALID_RESPONSE, "Discord token response was incomplete.");
        return token;
    }

    private boolean belongsToGuild(String accessToken) {
        String after = null;
        for (int page = 0; page < 20; page++) {
            String query = "?limit=200" + (after == null ? "" : "&after=" + queryEncode(after));
            JsonNode guilds = getJson(URI.create(guildsEndpoint + query), accessToken);
            if (!guilds.isArray()) throw new OAuthException(INVALID_RESPONSE, "Discord guild response was invalid.");
            String last = null;
            for (JsonNode guild : guilds) {
                String id = optionalText(guild, "id");
                if (guildId.equals(id)) return true;
                if (id != null) last = id;
            }
            if (guilds.size() < 200 || last == null) return false;
            after = last;
        }
        return false;
    }

    private JsonNode getJson(URI uri, String accessToken) {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(15))
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + accessToken)
                .GET()
                .build();
        return sendJson(request);
    }

    private JsonNode sendJson(HttpRequest request) {
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                OAuthException.Code code = response.statusCode() == 401 || response.statusCode() == 403
                        ? ACCESS_DENIED : PROVIDER_UNAVAILABLE;
                throw new OAuthException(code, "Discord OAuth request failed with status " + response.statusCode() + ".");
            }
            return mapper.readTree(response.body());
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new OAuthException(PROVIDER_UNAVAILABLE, "Discord OAuth request was interrupted.", error);
        } catch (IOException error) {
            throw new OAuthException(PROVIDER_UNAVAILABLE, "Could not communicate with Discord OAuth.", error);
        }
    }

    private static String requiredText(JsonNode node, String field) {
        String value = optionalText(node, field);
        if (value == null) throw new OAuthException(INVALID_RESPONSE, "Discord response omitted " + field + ".");
        return value;
    }

    private static String optionalText(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() || !value.isTextual() || value.asText().isBlank()
                ? null : value.asText();
    }

    private static String avatarUrl(String userId, String avatarHash) {
        if (avatarHash == null) return "https://cdn.discordapp.com/embed/avatars/0.png";
        String extension = avatarHash.startsWith("a_") ? "gif" : "png";
        return "https://cdn.discordapp.com/avatars/" + userId + "/" + avatarHash + "." + extension + "?size=128";
    }

    private static String queryEncode(String value) {
        // URI query spaces should be percent-encoded, not represented as form-style plus signs.
        return formEncode(value).replace("+", "%20");
    }

    private static String formEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
