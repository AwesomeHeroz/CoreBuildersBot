package com.corebuilders.bot.runtime;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.flywaydb.core.Flyway;

import java.net.URI;
import java.util.Locale;

/** Owns database infrastructure setup so runtime wiring only composes application services. */
final class DatabaseBootstrap {
    private DatabaseBootstrap() {}

    static HikariDataSource start(JavaPlugin plugin) {
        HikariDataSource dataSource = createDataSource(plugin);
        try {
            migrate(dataSource, plugin.getConfig());
            return dataSource;
        } catch (RuntimeException | Error error) {
            dataSource.close();
            throw error;
        }
    }

    private static HikariDataSource createDataSource(JavaPlugin plugin) {
        FileConfiguration config = plugin.getConfig();
        String jdbcUrl = envOrConfig("COREBOT_DB_URL", config.getString("database.jdbc-url", ""));
        boolean allowInsecureRemote = config.getBoolean("database.allow-insecure-remote", false);

        if (jdbcUrl.isBlank()) {
            String host = envOrConfig("COREBOT_DB_HOST", config.getString("database.host", "localhost"));
            int port = envInt("COREBOT_DB_PORT", config.getInt("database.port", 3306));
            String database = require(envOrConfig("COREBOT_DB_NAME", config.getString("database.name", "corebuilders_bot")), "database.name");
            String configuredSslMode = envOrConfig("COREBOT_DB_SSL_MODE", config.getString("database.ssl-mode", "DISABLED"));
            String sslMode = configuredSslMode.isBlank() ? "VERIFY_IDENTITY" : configuredSslMode;
            validateTransport(host, sslMode, allowInsecureRemote);

            jdbcUrl = "jdbc:mysql://" + require(host, "database.host") + ":" + port + "/" + database
                    + "?useUnicode=true"
                    + "&characterEncoding=utf8"
                    + "&connectionTimeZone=UTC"
                    + "&forceConnectionTimeZoneToSession=true"
                    + "&sslMode=" + (sslMode.isBlank() ? "VERIFY_IDENTITY" : sslMode);
        } else {
            validateJdbcUrl(jdbcUrl, allowInsecureRemote);
        }

        HikariConfig hikari = new HikariConfig();
        hikari.setJdbcUrl(jdbcUrl);
        hikari.setDriverClassName("com.mysql.cj.jdbc.Driver");
        hikari.setUsername(require(envOrConfig("COREBOT_DB_USERNAME", config.getString("database.username", "root")), "database.username"));
        hikari.setPassword(envOrConfig("COREBOT_DB_PASSWORD", config.getString("database.password", "")));

        int maximumPoolSize = Math.max(2, config.getInt("database.maximum-pool-size", 10));
        int minimumIdle = Math.max(0, Math.min(maximumPoolSize, config.getInt("database.minimum-idle", 1)));
        hikari.setMaximumPoolSize(maximumPoolSize);
        hikari.setMinimumIdle(minimumIdle);
        hikari.setPoolName("CoreBuilders-DB");
        hikari.setConnectionTimeout(Math.max(1_000L, config.getLong("database.connection-timeout-ms", 10_000L)));
        hikari.setValidationTimeout(Math.max(1_000L, config.getLong("database.validation-timeout-ms", 5_000L)));
        hikari.setConnectionInitSql("SET time_zone = '+00:00'");

        plugin.getLogger().info("Connecting to MySQL: " + sanitizeJdbcUrl(jdbcUrl));
        return new HikariDataSource(hikari);
    }

    private static void validateTransport(String host, String sslMode, boolean allowInsecureRemote) {
        if (isLoopback(host) || allowInsecureRemote) return;
        if (!isStrictTlsMode(sslMode)) {
            throw new IllegalStateException(
                    "Refusing a remote MySQL connection to " + host
                            + " without strict TLS. Use ssl-mode=REQUIRED, VERIFY_CA, or VERIFY_IDENTITY, "
                            + "or explicitly set database.allow-insecure-remote=true."
            );
        }
    }

    private static void validateJdbcUrl(String jdbcUrl, boolean allowInsecureRemote) {
        try {
            String normalized = jdbcUrl.startsWith("jdbc:") ? jdbcUrl.substring(5) : jdbcUrl;
            URI uri = URI.create(normalized);
            if (uri.getUserInfo() != null) {
                throw new IllegalStateException(
                        "Do not embed database credentials in COREBOT_DB_URL. Use COREBOT_DB_USERNAME and COREBOT_DB_PASSWORD."
                );
            }
            if (hasCredentialQueryParameter(uri.getRawQuery())) {
                throw new IllegalStateException(
                        "Do not embed database credentials in COREBOT_DB_URL query parameters. "
                                + "Use COREBOT_DB_USERNAME and COREBOT_DB_PASSWORD."
                );
            }
            String host = uri.getHost();
            if (host != null && !isLoopback(host) && !allowInsecureRemote) {
                String sslMode = queryParameter(uri.getRawQuery(), "sslMode");
                if (!isStrictTlsMode(sslMode)) {
                    throw new IllegalStateException(
                            "Refusing a remote MySQL JDBC URL without strict TLS. Add sslMode=REQUIRED, VERIFY_CA, "
                                    + "or VERIFY_IDENTITY, or explicitly set database.allow-insecure-remote=true."
                    );
                }
            }
        } catch (IllegalStateException error) {
            throw error;
        } catch (IllegalArgumentException error) {
            throw new IllegalStateException("Invalid database.jdbc-url / COREBOT_DB_URL.", error);
        }
    }

    private static boolean hasCredentialQueryParameter(String rawQuery) {
        if (rawQuery == null || rawQuery.isBlank()) return false;
        for (String part : rawQuery.split("&")) {
            int separator = part.indexOf('=');
            String name = (separator < 0 ? part : part.substring(0, separator)).trim();
            if (name.equalsIgnoreCase("user")
                    || name.equalsIgnoreCase("username")
                    || name.equalsIgnoreCase("password")) {
                return true;
            }
        }
        return false;
    }

    private static boolean isLoopback(String host) {
        if (host == null) return false;
        String value = host.trim().toLowerCase(Locale.ROOT);
        return value.equals("localhost") || value.equals("127.0.0.1") || value.equals("::1") || value.equals("[::1]");
    }

    private static boolean isStrictTlsMode(String sslMode) {
        String mode = clean(sslMode).toUpperCase(Locale.ROOT);
        return mode.equals("REQUIRED") || mode.equals("VERIFY_CA") || mode.equals("VERIFY_IDENTITY");
    }

    private static String queryParameter(String rawQuery, String wantedName) {
        if (rawQuery == null || rawQuery.isBlank()) return "";
        for (String part : rawQuery.split("&")) {
            int separator = part.indexOf('=');
            String name = separator < 0 ? part : part.substring(0, separator);
            if (name.equalsIgnoreCase(wantedName)) {
                return separator < 0 ? "" : part.substring(separator + 1);
            }
        }
        return "";
    }

    private static void migrate(HikariDataSource dataSource, FileConfiguration config) {
        if (!config.getBoolean("database.migrations.enabled", true)) return;
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .baselineOnMigrate(config.getBoolean("database.migrations.baseline-on-migrate", false))
                .validateOnMigrate(config.getBoolean("database.migrations.validate-on-migrate", true))
                .load()
                .migrate();
    }

    private static String envOrConfig(String env, String configured) {
        String value = System.getenv(env);
        return value == null || value.isBlank() ? clean(configured) : value.trim();
    }

    private static int envInt(String env, int configured) {
        String value = System.getenv(env);
        if (value == null || value.isBlank()) return configured;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException error) {
            throw new IllegalStateException(env + " must be an integer.", error);
        }
    }

    private static String require(String value, String path) {
        String cleaned = clean(value);
        if (cleaned.isBlank()) throw new IllegalStateException("Missing required config value: " + path);
        return cleaned;
    }

    private static String clean(String value) { return value == null ? "" : value.trim(); }

    static String sanitizeJdbcUrl(String jdbcUrl) {
        String withoutQuery = jdbcUrl;
        int query = withoutQuery.indexOf('?');
        if (query >= 0) withoutQuery = withoutQuery.substring(0, query);
        return withoutQuery.replaceFirst("(?i)(jdbc:mysql://)[^/@]+@", "$1<redacted>@");
    }
}
