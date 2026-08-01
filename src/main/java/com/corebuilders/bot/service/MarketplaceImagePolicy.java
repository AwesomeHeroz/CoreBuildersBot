package com.corebuilders.bot.service;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import static com.corebuilders.bot.service.MarketplaceException.validation;

/** Allows generated same-origin upload URLs and explicitly trusted external HTTPS hosts. */
public final class MarketplaceImagePolicy implements MarketplaceListingImagePolicy {
    private static final java.util.regex.Pattern UPLOADED_PATH = java.util.regex.Pattern.compile(
            "/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/[0-9a-f]{32}\\.(png|jpg|gif)"
    );
    private final Set<String> allowedHosts;
    private final URI uploadedImageBase;

    public MarketplaceImagePolicy(Set<String> allowedHosts) {
        this(allowedHosts, null);
    }

    public MarketplaceImagePolicy(Set<String> allowedHosts, URI uploadedImageBase) {
        this.allowedHosts = allowedHosts == null ? Set.of() : allowedHosts.stream()
                .filter(v -> v != null && !v.isBlank())
                .map(v -> v.trim().toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
        this.uploadedImageBase = uploadedImageBase;
    }

    @Override
    public String validate(String value) {
        if (value == null || value.isBlank()) return null;
        if (value.length() > 1000) throw validation("Image URL is too long.");
        try {
            URI uri = new URI(value.trim());
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            if (uri.getRawUserInfo() != null || uri.getRawFragment() != null || uri.getRawQuery() != null) {
                throw validation("Image URLs cannot contain credentials, queries, or fragments.");
            }
            if (isUploadedImage(uri)) return uri.toASCIIString();
            if (!"https".equalsIgnoreCase(uri.getScheme()) || host.isBlank()) {
                throw validation("Images must use an approved HTTPS host.");
            }
            if (!isAllowed(host)) throw validation("That image host is not approved.");
            return uri.toASCIIString();
        } catch (URISyntaxException error) {
            throw validation("Image must be a valid approved HTTPS URL.");
        }
    }

    private boolean isUploadedImage(URI uri) {
        if (uploadedImageBase == null) return false;
        if (!equalsIgnoreCase(uri.getScheme(), uploadedImageBase.getScheme())) return false;
        if (!equalsIgnoreCase(uri.getHost(), uploadedImageBase.getHost())) return false;
        if (effectivePort(uri) != effectivePort(uploadedImageBase)) return false;
        String basePath = uploadedImageBase.getPath();
        if (uri.getPath() == null || basePath == null || !uri.getPath().startsWith(basePath)) return false;
        String relativePath = uri.getPath().substring(basePath.length() - 1);
        return UPLOADED_PATH.matcher(relativePath).matches();
    }

    private static int effectivePort(URI uri) {
        if (uri.getPort() >= 0) return uri.getPort();
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    private static boolean equalsIgnoreCase(String first, String second) {
        return first != null && second != null && first.equalsIgnoreCase(second);
    }

    private boolean isAllowed(String host) {
        for (String allowed : allowedHosts) {
            if (host.equals(allowed) || host.endsWith("." + allowed)) return true;
        }
        return false;
    }
}
