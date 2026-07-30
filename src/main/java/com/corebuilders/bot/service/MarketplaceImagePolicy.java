package com.corebuilders.bot.service;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import static com.corebuilders.bot.service.MarketplaceException.validation;

/** Allows listing images only from explicitly trusted HTTPS hosts. */
public final class MarketplaceImagePolicy {
    private final Set<String> allowedHosts;

    public MarketplaceImagePolicy(Set<String> allowedHosts) {
        this.allowedHosts = allowedHosts == null ? Set.of() : allowedHosts.stream()
                .filter(v -> v != null && !v.isBlank())
                .map(v -> v.trim().toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }

    public String validate(String value) {
        if (value == null || value.isBlank()) return null;
        if (value.length() > 1000) throw validation("Image URL is too long.");
        try {
            URI uri = new URI(value.trim());
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || host.isBlank()) {
                throw validation("Images must use an approved HTTPS host.");
            }
            if (uri.getRawUserInfo() != null || uri.getRawFragment() != null || uri.getRawQuery() != null) {
                throw validation("Image URLs cannot contain credentials, queries, or fragments.");
            }
            if (!isAllowed(host)) throw validation("That image host is not approved.");
            return uri.toASCIIString();
        } catch (URISyntaxException error) {
            throw validation("Image must be a valid approved HTTPS URL.");
        }
    }

    private boolean isAllowed(String host) {
        for (String allowed : allowedHosts) {
            if (host.equals(allowed) || host.endsWith("." + allowed)) return true;
        }
        return false;
    }
}
