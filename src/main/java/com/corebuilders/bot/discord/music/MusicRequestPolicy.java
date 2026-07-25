package com.corebuilders.bot.discord.music;

import com.corebuilders.bot.config.MusicConfig;

import java.net.IDN;
import java.net.URI;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Converts user input into a Lavaplayer identifier while preventing local-file
 * loading and unrestricted server-side HTTP requests.
 */
public final class MusicRequestPolicy {
    private static final Set<String> BUILT_IN_HOSTS = Set.of(
            "youtube.com", "youtu.be", "soundcloud.com", "bandcamp.com",
            "vimeo.com", "twitch.tv"
    );

    private final boolean allowDirectUrls;
    private final Set<String> allowedDirectHosts;

    public MusicRequestPolicy(MusicConfig config) {
        this(config.allowDirectUrls(), config.allowedDirectUrlHosts());
    }

    MusicRequestPolicy(boolean allowDirectUrls, Set<String> allowedDirectHosts) {
        this.allowDirectUrls = allowDirectUrls;
        this.allowedDirectHosts = normalizeAllowedHosts(allowedDirectHosts);
    }

    public String resolve(String input) {
        String value = input == null ? "" : input.trim();
        if (value.isBlank()) {
            throw new IllegalArgumentException("Enter a YouTube search, supported-site URL, or allowed direct audio URL.");
        }
        if (value.length() > 1_000) {
            throw new IllegalArgumentException("Music request is too long.");
        }

        if (!value.matches("(?i)^[a-z][a-z0-9+.-]*://.*")) {
            return "ytsearch:" + value;
        }

        URI uri = parseUri(value);
        if (uri == null || uri.getScheme() == null) {
            throw new IllegalArgumentException("Invalid music URL.");
        }

        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("Only HTTPS music URLs are allowed.");
        }
        if (uri.getRawUserInfo() != null || uri.getPort() != -1) {
            throw new IllegalArgumentException("Music URLs cannot contain credentials or a custom port.");
        }

        String host = normalizeHost(uri.getHost());
        if (host.isBlank()) {
            throw new IllegalArgumentException("Music URL has no valid host.");
        }
        if (matchesAny(host, BUILT_IN_HOSTS)) {
            return uri.toASCIIString();
        }
        if (allowDirectUrls && matchesAny(host, allowedDirectHosts)) {
            return uri.toASCIIString();
        }

        throw new IllegalArgumentException(
                "That website is not allowed. Use YouTube, SoundCloud, Bandcamp, Vimeo, Twitch, "
                        + "or add the direct-audio host to music.allowed-direct-url-hosts."
        );
    }

    static boolean hostMatches(String host, String allowed) {
        return host.equals(allowed) || host.endsWith("." + allowed);
    }

    private static boolean matchesAny(String host, Set<String> allowedHosts) {
        return allowedHosts.stream().anyMatch(allowed -> hostMatches(host, allowed));
    }

    private static Set<String> normalizeAllowedHosts(Set<String> hosts) {
        if (hosts == null || hosts.isEmpty()) return Set.of();
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String raw : hosts) {
            String host = normalizeHost(raw);
            if (host.isBlank() || isLocalOrIpLiteral(host)) {
                throw new IllegalArgumentException("Unsafe direct-audio host: " + raw);
            }
            normalized.add(host);
        }
        return Set.copyOf(normalized);
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
            if (Integer.parseInt(part) > 255) return false;
        }
        return true;
    }

    private static URI parseUri(String value) {
        try {
            return URI.create(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static String normalizeHost(String host) {
        if (host == null || host.isBlank()) return "";
        try {
            return IDN.toASCII(host, IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException ignored) {
            return "";
        }
    }
}
