package com.corebuilders.bot.config;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Immutable configuration for Discord voice playback. */
public record MusicConfig(
        boolean enabled,
        int defaultVolume,
        int maxQueueSize,
        int maxPlaylistTracks,
        long maxTrackDurationMillis,
        boolean allowStreams,
        int idleDisconnectSeconds,
        int loadTimeoutSeconds,
        Set<String> controllerRoleIds,
        boolean allowDirectUrls,
        Set<String> allowedDirectUrlHosts
) {
    public MusicConfig {
        defaultVolume = clamp(defaultVolume, 0, 150);
        maxQueueSize = clamp(maxQueueSize, 1, 500);
        maxPlaylistTracks = clamp(maxPlaylistTracks, 1, 100);
        maxTrackDurationMillis = Math.max(60_000L, maxTrackDurationMillis);
        idleDisconnectSeconds = clamp(idleDisconnectSeconds, 10, 3_600);
        loadTimeoutSeconds = clamp(loadTimeoutSeconds, 5, 60);
        controllerRoleIds = Set.copyOf(controllerRoleIds == null ? Set.of() : controllerRoleIds);
        allowedDirectUrlHosts = Set.copyOf(
                allowedDirectUrlHosts == null ? Set.of() : allowedDirectUrlHosts
        );
    }

    public static MusicConfig from(FileConfiguration config) {
        int maxMinutes = clamp(config.getInt("music.max-track-duration-minutes", 180), 1, 1_440);
        return new MusicConfig(
                config.getBoolean("music.enabled", true),
                config.getInt("music.default-volume", 80),
                config.getInt("music.max-queue-size", 100),
                config.getInt("music.max-playlist-tracks", 25),
                maxMinutes * 60_000L,
                config.getBoolean("music.allow-streams", true),
                config.getInt("music.idle-disconnect-seconds", 300),
                config.getInt("music.load-timeout-seconds", 20),
                snowflakes(config.getStringList("music.controller-role-ids"), "music.controller-role-ids"),
                config.getBoolean("music.allow-direct-urls", false),
                hosts(config.getStringList("music.allowed-direct-url-hosts"))
        );
    }

    private static Set<String> snowflakes(List<String> values, String path) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (values == null) return result;
        for (String raw : values) {
            String value = raw == null ? "" : raw.trim();
            if (value.isBlank()) continue;
            if (!value.matches("\\d{15,22}")) {
                throw new IllegalStateException(path + " must contain Discord role IDs only: " + value);
            }
            result.add(value);
        }
        return result;
    }

    private static Set<String> hosts(List<String> values) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (values == null) return result;
        for (String raw : values) {
            String host = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
            if (host.startsWith("*.")) host = host.substring(2);
            if (host.isBlank()
                    || !host.matches("[a-z0-9.-]+")
                    || host.startsWith(".")
                    || host.endsWith(".")
                    || isLocalOrIpLiteral(host)) {
                throw new IllegalStateException("Invalid or unsafe music.allowed-direct-url-hosts entry: " + raw);
            }
            result.add(host);
        }
        return result;
    }

    private static boolean isLocalOrIpLiteral(String host) {
        if (host.equals("localhost") || host.endsWith(".localhost")
                || host.endsWith(".local") || host.endsWith(".internal")) {
            return true;
        }
        String[] parts = host.split("\\.", -1);
        if (parts.length != 4) return false;
        for (String part : parts) {
            if (!part.matches("\\d{1,3}")) return false;
            int value = Integer.parseInt(part);
            if (value > 255) return false;
        }
        return true;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
