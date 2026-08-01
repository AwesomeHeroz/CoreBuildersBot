package com.corebuilders.bot.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class WebsiteConfigTest {
    @Test
    void disabledWebsiteDoesNotRequireOauthSecrets() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("website.enabled", false);
        yaml.set("website.port", 8080);

        WebsiteConfig config = WebsiteConfig.from(yaml);

        assertFalse(config.enabled());
        assertEquals(8080, config.port());
        assertEquals("127.0.0.1", config.bindAddress());
        assertEquals("http://localhost:8080/api/account/discord/callback", config.oauthRedirectUri().toString());
    }

    @Test
    void parsesEnabledWebsiteAndDerivesCanonicalCallback() {
        YamlConfiguration yaml = enabled("https://shop.example.com", true);

        WebsiteConfig config = WebsiteConfig.from(yaml);

        assertTrue(config.enabled());
        assertEquals("https://shop.example.com", config.publicBaseUrl().toString());
        assertEquals("https://shop.example.com/api/account/discord/callback", config.oauthRedirectUri().toString());
        assertTrue(config.secureCookies());
        assertEquals("/uploads/images", config.imagePublicPath());
        assertEquals("https://shop.example.com/uploads/images/", config.imagePublicBaseUrl().toString());
        assertEquals(5_242_880, config.maxImageUploadBytes());
    }

    @Test
    void stripsOnlyOriginTrailingSlashBeforeDerivingCallback() {
        YamlConfiguration yaml = enabled("https://shop.example.com/", true);

        WebsiteConfig config = WebsiteConfig.from(yaml);

        assertEquals("https://shop.example.com", config.publicBaseUrl().toString());
        assertEquals("https://shop.example.com/api/account/discord/callback", config.oauthRedirectUri().toString());
    }

    @Test
    void permitsLocalHttpOnlyWithInsecureCookies() {
        YamlConfiguration yaml = enabled("http://localhost:9000", false);

        WebsiteConfig config = WebsiteConfig.from(yaml);

        assertEquals("http://localhost:9000/api/account/discord/callback", config.oauthRedirectUri().toString());
        assertFalse(config.secureCookies());
    }


    @Test
    void permitsIpv6LoopbackForLocalDevelopment() {
        YamlConfiguration yaml = enabled("http://[::1]:9000", false);
        yaml.set("website.bind-address", "::1");

        WebsiteConfig config = WebsiteConfig.from(yaml);

        assertEquals("::1", config.bindAddress());
        assertEquals("http://[::1]:9000", config.publicBaseUrl().toString());
    }

    @Test
    void acceptsLegacyRedirectOnlyWhenItExactlyMatchesCanonicalCallback() {
        YamlConfiguration yaml = enabled("https://shop.example.com", true);
        yaml.set("website.discord-oauth.redirect-uri", "https://shop.example.com/api/account/discord/callback");

        WebsiteConfig config = WebsiteConfig.from(yaml);

        assertEquals("https://shop.example.com/api/account/discord/callback", config.oauthRedirectUri().toString());
    }

    @Test
    void rejectsMismatchedLegacyRedirect() {
        YamlConfiguration yaml = enabled("https://shop.example.com", true);
        yaml.set("website.discord-oauth.redirect-uri", "https://other.example.com/api/account/discord/callback");

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> WebsiteConfig.from(yaml));

        assertTrue(error.getMessage().contains("must exactly match https://shop.example.com/api/account/discord/callback"));
    }

    @Test
    void rejectsCallbackWithTrailingSlash() {
        YamlConfiguration yaml = enabled("https://shop.example.com", true);
        yaml.set("website.discord-oauth.redirect-uri", "https://shop.example.com/api/account/discord/callback/");

        assertThrows(IllegalStateException.class, () -> WebsiteConfig.from(yaml));
    }

    @Test
    void rejectsPublicBaseUrlWithExtraPath() {
        YamlConfiguration yaml = enabled("https://example.com/shop", true);

        assertThrows(IllegalStateException.class, () -> WebsiteConfig.from(yaml));
    }

    @Test
    void rejectsWildcardBindAddressAsPublicUrl() {
        YamlConfiguration yaml = enabled("http://0.0.0.0:8080", false);

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> WebsiteConfig.from(yaml));

        assertTrue(error.getMessage().contains("cannot use 0.0.0.0"));
    }

    @Test
    void rejectsPublicHttpEvenWhenSecureCookiesAreDisabled() {
        YamlConfiguration yaml = enabled("http://203.0.113.10:8080", false);

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> WebsiteConfig.from(yaml));

        assertTrue(error.getMessage().contains("HTTP is allowed only"));
    }

    @Test
    void rejectsSecureCookiesForLocalPlainHttp() {
        YamlConfiguration yaml = enabled("http://127.0.0.1:8080", true);

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> WebsiteConfig.from(yaml));

        assertTrue(error.getMessage().contains("website.cookies.secure must be false"));
    }


    @Test
    void rejectsMalformedImageHostnames() {
        YamlConfiguration yaml = enabled("https://shop.example.com", true);
        yaml.set("website.marketplace.allowed-image-hosts", java.util.List.of("cdn..example.com"));
        assertThrows(IllegalStateException.class, () -> WebsiteConfig.from(yaml));

        yaml.set("website.marketplace.allowed-image-hosts", java.util.List.of("-cdn.example.com"));
        assertThrows(IllegalStateException.class, () -> WebsiteConfig.from(yaml));
    }

    @Test
    void acceptsPublicImageHostsAndRejectsIpLiterals() {
        YamlConfiguration yaml = enabled("https://shop.example.com", true);
        yaml.set("website.marketplace.allowed-image-hosts", java.util.List.of("cdn.example.com"));

        WebsiteConfig config = WebsiteConfig.from(yaml);
        assertEquals(java.util.Set.of("cdn.example.com"), config.allowedImageHosts());

        yaml.set("website.marketplace.allowed-image-hosts", java.util.List.of("10.0.0.1"));
        assertThrows(IllegalStateException.class, () -> WebsiteConfig.from(yaml));
    }

    @Test
    void parsesImageUploadDirectoryPathAndLimit() {
        YamlConfiguration yaml = enabled("https://shop.example.com", true);
        yaml.set("website.marketplace.image-uploads.directory", "build/custom-marketplace-images");
        yaml.set("website.marketplace.image-uploads.public-path", "/media/listings");
        yaml.set("website.marketplace.image-uploads.max-file-bytes", 2_000_000);

        WebsiteConfig config = WebsiteConfig.from(yaml);

        assertEquals(Path.of("build/custom-marketplace-images").toAbsolutePath().normalize(),
                config.imageUploadDirectory());
        assertEquals("/media/listings", config.imagePublicPath());
        assertEquals("https://shop.example.com/media/listings/", config.imagePublicBaseUrl().toString());
        assertEquals(2_000_000, config.maxImageUploadBytes());
    }

    @Test
    void rejectsUnsafeImagePublicPaths() {
        YamlConfiguration yaml = enabled("https://shop.example.com", true);
        yaml.set("website.marketplace.image-uploads.public-path", "/api/images");
        assertThrows(IllegalStateException.class, () -> WebsiteConfig.from(yaml));

        yaml.set("website.marketplace.image-uploads.public-path", "/uploads/../images");
        assertThrows(IllegalStateException.class, () -> WebsiteConfig.from(yaml));

        yaml.set("website.marketplace.image-uploads.public-path", "uploads/images");
        assertThrows(IllegalStateException.class, () -> WebsiteConfig.from(yaml));
    }

    private static YamlConfiguration enabled(String publicBaseUrl, boolean secureCookies) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("website.enabled", true);
        yaml.set("website.bind-address", "0.0.0.0");
        yaml.set("website.port", 9000);
        yaml.set("website.public-base-url", publicBaseUrl);
        yaml.set("website.discord-oauth.client-id", "123456789012345678");
        yaml.set("website.discord-oauth.client-secret", "secret");
        yaml.set("website.cookies.secure", secureCookies);
        return yaml;
    }
}
