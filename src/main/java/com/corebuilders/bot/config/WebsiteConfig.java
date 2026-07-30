package com.corebuilders.bot.config;

import org.bukkit.configuration.file.FileConfiguration;

import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/** Validated configuration for the embedded marketplace website and optional Discord account linking. */
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
        Duration idleSessionLifetime,
        int maxRequestBytes,
        int workerThreads,
        Set<String> allowedImageHosts,
        boolean proxyCustomAuth,
        String originHeaderName,
        String originHeaderSecret
) {
    public static final String OAUTH_CALLBACK_PATH = "/api/account/discord/callback";

    public static final String DEFAULT_BIND = "0.0.0.0";

    public WebsiteConfig {
        allowedImageHosts = allowedImageHosts == null ? Set.of() : Set.copyOf(allowedImageHosts);
    }

    public static WebsiteConfig from(FileConfiguration config) {
        boolean enabled = config.getBoolean("website.enabled", false);
        String bind = value("COREBOT_WEB_BIND_ADDRESS", config.getString("website.bind-address", "127.0.0.1"));
        int port = integer("COREBOT_WEB_PORT", config.getInt("website.port", 8080), 1, 65535, "website.port");
        String publicUrl = value("COREBOT_WEB_PUBLIC_BASE_URL", config.getString("website.public-base-url", ""));
        String clientId = value("COREBOT_DISCORD_OAUTH_CLIENT_ID", config.getString("website.discord-oauth.client-id", ""));
        String clientSecret = value("COREBOT_DISCORD_OAUTH_CLIENT_SECRET", config.getString("website.discord-oauth.client-secret", ""));
        String configuredRedirect = value("COREBOT_DISCORD_OAUTH_REDIRECT_URI",
                config.getString("website.discord-oauth.redirect-uri", ""));
        boolean requireGuild = config.getBoolean("website.discord-oauth.require-guild-membership", true);
        boolean secure = config.getBoolean("website.cookies.secure", true);
        int sessionHours = integer("COREBOT_WEB_SESSION_HOURS",
                config.getInt("website.cookies.session-hours", 24), 1, 24 * 30, "website.cookies.session-hours");
        int idleMinutes = integer("COREBOT_WEB_IDLE_SESSION_MINUTES",
                config.getInt("website.cookies.idle-minutes", 30), 5, 24 * 60, "website.cookies.idle-minutes");
        int maxBytes = integer("COREBOT_WEB_MAX_REQUEST_BYTES",
                config.getInt("website.max-request-bytes", 1_048_576), 1024, 10_485_760, "website.max-request-bytes");
        int workers = integer("COREBOT_WEB_WORKER_THREADS",
                config.getInt("website.worker-threads", 16), 2, 256, "website.worker-threads");
        Set<String> imageHosts = allowedHosts(config.getStringList("website.marketplace.allowed-image-hosts"));

        boolean proxyCustomAuth = config.getBoolean("website.origin-auth.enabled", false);

        String originHeaderName = config.getString(
                "website.origin-auth.header-name",
                "X-CoreBuilders-Origin-Secret"
        );

        if (proxyCustomAuth && !originHeaderName.matches("[A-Za-z0-9_-]{1,64}")) {
            throw new IllegalStateException(
                    "website.origin-auth.header-name contains invalid characters."
            );
        }

        String originHeaderSecret = value(
                "COREBOT_ORIGIN_HEADER_SECRET",
                config.getString("website.origin-auth.secret", "")
        );

        URI base = publicUrl.isBlank()
                ? URI.create("http://localhost:" + port)
                : absoluteHttpUri(publicUrl, "website.public-base-url");
        URI callback = canonicalCallback(base);
        if (!enabled) {
            return new WebsiteConfig(false, bind, port, base, clientId, clientSecret, callback,
                    requireGuild, secure, Duration.ofHours(sessionHours), Duration.ofMinutes(idleMinutes),
                    maxBytes, workers, imageHosts, proxyCustomAuth, originHeaderName, originHeaderSecret);
        }

        if (bind.isBlank()) bind = DEFAULT_BIND;
        bind = validatedBindAddress(bind);
        base = absoluteHttpUri(required(publicUrl, "website.public-base-url / COREBOT_WEB_PUBLIC_BASE_URL"),
                "website.public-base-url");
        validatePublicBaseUrl(base, secure);

        callback = canonicalCallback(base);
        validateConfiguredRedirect(configuredRedirect, callback);

        String cleanClientId = required(clientId, "website.discord-oauth.client-id / COREBOT_DISCORD_OAUTH_CLIENT_ID");
        if (!cleanClientId.matches("\\d{15,22}")) {
            throw new IllegalStateException("Discord OAuth client ID must be a Discord snowflake.");
        }
        String cleanSecret = required(clientSecret,
                "website.discord-oauth.client-secret / COREBOT_DISCORD_OAUTH_CLIENT_SECRET");
        return new WebsiteConfig(true, bind, port, base, cleanClientId, cleanSecret, callback,
                requireGuild, secure, Duration.ofHours(sessionHours), Duration.ofMinutes(idleMinutes),
                maxBytes, workers, imageHosts, proxyCustomAuth, originHeaderName, originHeaderSecret);
    }

    private static URI canonicalCallback(URI publicBaseUrl) {
        return publicBaseUrl.resolve(OAUTH_CALLBACK_PATH);
    }

    private static void validateConfiguredRedirect(String configuredRedirect, URI canonicalCallback) {
        if (configuredRedirect == null || configuredRedirect.isBlank()) return;
        URI configured = absoluteHttpUri(configuredRedirect, "website.discord-oauth.redirect-uri");
        if (!configured.equals(canonicalCallback)) {
            throw new IllegalStateException(
                    "website.discord-oauth.redirect-uri / COREBOT_DISCORD_OAUTH_REDIRECT_URI must exactly match "
                            + canonicalCallback + ". Prefer leaving it blank; the callback is derived from website.public-base-url."
            );
        }
    }

    private static String validatedBindAddress(String bindAddress) {
        String host = bindAddress.trim().toLowerCase(Locale.ROOT);
        if (!isLoopbackHost(host)) {
            return DEFAULT_BIND;
        }
        return bindAddress;
    }

    private static void validatePublicBaseUrl(URI base, boolean secureCookies) {
        String host = base.getHost().toLowerCase(Locale.ROOT);
        if (isAnyLocalAddress(host)) {
            throw new IllegalStateException(
                    "website.public-base-url cannot use " + host + ". Use the real public hostname, or localhost for local testing."
            );
        }
        boolean https = "https".equalsIgnoreCase(base.getScheme());
        if (https && !secureCookies) {
            throw new IllegalStateException("website.cookies.secure must be true for an HTTPS public URL.");
        }
        if (!https) {
            if (!isLoopbackHost(host)) {
                throw new IllegalStateException("HTTP is allowed only for localhost/loopback development. Use HTTPS in production.");
            }
            if (secureCookies) {
                throw new IllegalStateException("website.cookies.secure must be false for local HTTP development.");
            }
        }
    }

    private static boolean isAnyLocalAddress(String host) {
        return "0.0.0.0".equals(host) || "::".equals(host) || "[::]".equals(host);
    }

    private static boolean isLoopbackHost(String host) {
        return "localhost".equals(host) || "::1".equals(host) || "[::1]".equals(host)
                || isIpv4Loopback(host);
    }

    private static boolean isIpv4Loopback(String host) {
        String[] parts = host.split("\\.", -1);
        if (parts.length != 4 || !"127".equals(parts[0])) return false;
        for (String part : parts) {
            if (part.isEmpty() || part.length() > 3) return false;
            for (int i = 0; i < part.length(); i++) {
                if (!Character.isDigit(part.charAt(i))) return false;
            }
            int value;
            try {
                value = Integer.parseInt(part);
            } catch (NumberFormatException ignored) {
                return false;
            }
            if (value < 0 || value > 255 || !Integer.toString(value).equals(part)) return false;
        }
        return true;
    }

    private static Set<String> allowedHosts(java.util.List<String> configured) {
        LinkedHashSet<String> hosts = new LinkedHashSet<>();
        if (configured == null) return Set.of();
        for (String raw : configured) {
            if (raw == null || raw.isBlank()) continue;
            String host = raw.trim().toLowerCase(Locale.ROOT);
            if (!isValidDnsName(host)) {
                throw new IllegalStateException("Invalid website.marketplace.allowed-image-hosts entry: " + raw);
            }
            if (host.equals("localhost") || host.endsWith(".local") || host.endsWith(".lan")
                    || host.endsWith(".internal") || host.indexOf('.') < 1 || isIpLiteral(host)) {
                throw new IllegalStateException("Marketplace image hosts must be public DNS hostnames: " + raw);
            }
            hosts.add(host);
        }
        return Set.copyOf(hosts);
    }

    private static boolean isValidDnsName(String host) {
        if (host.length() > 253 || host.startsWith(".") || host.endsWith(".")) return false;
        String[] labels = host.split("\\.", -1);
        if (labels.length < 2) return false;
        for (String label : labels) {
            if (label.isEmpty() || label.length() > 63 || label.startsWith("-") || label.endsWith("-")) {
                return false;
            }
            for (int i = 0; i < label.length(); i++) {
                char c = label.charAt(i);
                if (!(c >= 'a' && c <= 'z') && !(c >= '0' && c <= '9') && c != '-') return false;
            }
        }
        return true;
    }

    private static boolean isIpLiteral(String host) {
        if (host.indexOf(':') >= 0) return true;
        String[] parts = host.split("\\.", -1);
        if (parts.length != 4) return false;
        for (String part : parts) {
            try {
                int value = Integer.parseInt(part);
                if (value < 0 || value > 255 || !Integer.toString(value).equals(part)) return false;
            } catch (NumberFormatException ignored) {
                return false;
            }
        }
        return true;
    }

    private static URI absoluteHttpUri(String value, String path) {
        try {
            URI uri = URI.create(value.trim());
            String scheme = uri.getScheme();
            if (!("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) || uri.getHost() == null) {
                throw new IllegalStateException(path + " must be an absolute HTTP or HTTPS URL.");
            }
            if (uri.getRawUserInfo() != null) {
                throw new IllegalStateException(path + " cannot contain user information.");
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
        int result = configured;
        if (env != null && !env.isBlank()) {
            try {
                result = Integer.parseInt(env.trim());
            } catch (NumberFormatException error) {
                throw new IllegalStateException(envName + " must be an integer.", error);
            }
        }
        if (result < min || result > max) {
            throw new IllegalStateException(path + " must be between " + min + " and " + max + ".");
        }
        return result;
    }

    private static String required(String value, String path) {
        if (value == null || value.isBlank()) throw new IllegalStateException("Missing " + path + ".");
        return value.trim();
    }
}
