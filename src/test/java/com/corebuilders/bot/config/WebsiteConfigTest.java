package com.corebuilders.bot.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

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
    }

    @Test
    void parsesEnabledWebsiteAndDerivesCallback() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("website.enabled", true);
        yaml.set("website.bind-address", "127.0.0.1");
        yaml.set("website.port", 9000);
        yaml.set("website.public-base-url", "https://shop.example.com");
        yaml.set("website.discord-oauth.client-id", "123456789012345678");
        yaml.set("website.discord-oauth.client-secret", "secret");
        yaml.set("website.cookies.secure", true);

        WebsiteConfig config = WebsiteConfig.from(yaml);

        assertTrue(config.enabled());
        assertEquals("https://shop.example.com/api/auth/callback", config.oauthRedirectUri().toString());
        assertTrue(config.secureCookies());
    }

    @Test
    void rejectsPublicBaseUrlWithExtraPath() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("website.enabled", true);
        yaml.set("website.public-base-url", "https://example.com/shop");
        yaml.set("website.discord-oauth.client-id", "123456789012345678");
        yaml.set("website.discord-oauth.client-secret", "secret");

        assertThrows(IllegalStateException.class, () -> WebsiteConfig.from(yaml));
    }
}
