package com.corebuilders.bot.config;

import org.bukkit.configuration.file.FileConfiguration;

import java.net.URI;
import java.time.Duration;

/** Validated configuration for the embedded marketplace website and Discord OAuth login. */
public record WebsiteConfig(
        boolean enabled,
        String bindAddress,
        int port,
        URI publicBaseUrl,
        String oauthClientId,
        String oauthClientSecret,
        URI oauthRedirectUri,
        boolean requireGuildMembership,
        boolean secureCookies,
        Duration sessionLifetime,
        int maxRequestBytes,
        int workerThreads
) {
    public static WebsiteConfig from(FileConfiguration config) {
        boolean enabled = config.getBoolean("website.enabled", false);
        String bind = value("COREBOT_WEB_BIND_ADDRESS", config.getString("website.bind-address", "0.0.0.0"));
        int port = integer("COREBOT_WEB_PORT", config.getInt("website.port", 8080), 1, 65535, "website.port");
        String publicUrl = value("COREBOT_WEB_PUBLIC_BASE_URL", config.getString("website.public-base-url", ""));
        String clientId = value("COREBOT_DISCORD_OAUTH_CLIENT_ID", config.getString("website.discord-oauth.client-id", ""));
        String clientSecret = value("COREBOT_DISCORD_OAUTH_CLIENT_SECRET", config.getString("website.discord-oauth.client-secret", ""));
        String redirect = value("COREBOT_DISCORD_OAUTH_REDIRECT_URI", config.getString("website.discord-oauth.redirect-uri", ""));
        boolean requireGuild = config.getBoolean("website.discord-oauth.require-guild-membership", true);
        boolean secure = config.getBoolean("website.cookies.secure", true);
        int sessionHours = integer("COREBOT_WEB_SESSION_HOURS",
                config.getInt("website.cookies.session-hours", 24), 1, 24 * 30, "website.cookies.session-hours");
        int maxBytes = integer("COREBOT_WEB_MAX_REQUEST_BYTES",
                config.getInt("website.max-request-bytes", 1_048_576), 1024, 10_485_760, "website.max-request-bytes");
        int workers = integer("COREBOT_WEB_WORKER_THREADS",
                config.getInt("website.worker-threads", 16), 2, 256, "website.worker-threads");

        if (!enabled) {
            URI base = publicUrl.isBlank() ? URI.create("http://localhost:" + port) : absoluteHttpUri(publicUrl, "website.public-base-url");
            URI callback = redirect.isBlank() ? base.resolve("/api/auth/callback") : absoluteHttpUri(redirect, "website.discord-oauth.redirect-uri");
            return new WebsiteConfig(false, bind, port, base, clientId, clientSecret, callback,
                    requireGuild, secure, Duration.ofHours(sessionHours), maxBytes, workers);
        }

        if (bind.isBlank()) throw new IllegalStateException("website.bind-address cannot be blank.");
        URI base = absoluteHttpUri(required(publicUrl, "website.public-base-url / COREBOT_WEB_PUBLIC_BASE_URL"),
                "website.public-base-url");
        URI callback = redirect.isBlank() ? base.resolve("/api/auth/callback")
                : absoluteHttpUri(redirect, "website.discord-oauth.redirect-uri");
        String cleanClientId = required(clientId, "website.discord-oauth.client-id / COREBOT_DISCORD_OAUTH_CLIENT_ID");
        if (!cleanClientId.matches("\\d{15,22}")) {
            throw new IllegalStateException("Discord OAuth client ID must be a Discord snowflake.");
        }
        String cleanSecret = required(clientSecret,
                "website.discord-oauth.client-secret / COREBOT_DISCORD_OAUTH_CLIENT_SECRET");
        return new WebsiteConfig(true, bind, port, base, cleanClientId, cleanSecret, callback,
                requireGuild, secure, Duration.ofHours(sessionHours), maxBytes, workers);
    }

    private static URI absoluteHttpUri(String value, String path) {
        try {
            URI uri = URI.create(value.trim());
            String scheme = uri.getScheme();
            if (!("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) || uri.getHost() == null) {
                throw new IllegalStateException(path + " must be an absolute HTTP or HTTPS URL.");
            }
            if (uri.getRawQuery() != null || uri.getRawFragment() != null) {
                throw new IllegalStateException(path + " cannot contain a query or fragment.");
            }
            if (path.equals("website.public-base-url") && uri.getPath() != null
                    && !uri.getPath().isBlank() && !"/".equals(uri.getPath())) {
                throw new IllegalStateException(path + " must be an origin without an extra path.");
            }
            String text = uri.toString();
            if (text.endsWith("/") && uri.getPath() != null && "/".equals(uri.getPath())) {
                return URI.create(text.substring(0, text.length() - 1));
            }
            return uri;
        } catch (IllegalArgumentException error) {
            throw new IllegalStateException(path + " must be an absolute HTTP or HTTPS URL.", error);
        }
    }

    private static String value(String envName, String configured) {
        String env = System.getenv(envName);
        if (env != null && !env.isBlank()) return env.trim();
        return configured == null ? "" : configured.trim();
    }

    private static int integer(String envName, int configured, int min, int max, String path) {
        String env = System.getenv(envName);
        int value = configured;
        if (env != null && !env.isBlank()) {
            try {
                value = Integer.parseInt(env.trim());
            } catch (NumberFormatException error) {
                throw new IllegalStateException(envName + " must be an integer.", error);
            }
        }
        if (value < min || value > max) {
            throw new IllegalStateException(path + " must be between " + min + " and " + max + ".");
        }
        return value;
    }

    private static String required(String value, String path) {
        if (value == null || value.isBlank()) throw new IllegalStateException("Missing " + path + ".");
        return value.trim();
    }
}
