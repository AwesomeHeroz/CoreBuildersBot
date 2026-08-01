package com.corebuilders.bot.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketplaceWebContentTest {
    @Test
    void exposesRegistrationAndLoginGuidance() throws IOException {
        String html = resource("/web/index.html");
        String javascript = resource("/web/app.js");

        assertTrue(html.contains("id=\"register-modal\""));
        assertTrue(html.contains("https://discord.gg/corebuilders"));
        assertTrue(html.contains("<code>#apply</code>"));
        assertTrue(html.contains("play.corebuilders.gg"));

        assertTrue(javascript.contains("'Register'"));
        assertTrue(javascript.contains("guidanceType: 'discord'"));
        assertTrue(javascript.contains("guidanceType: 'minecraft'"));
        assertTrue(javascript.contains("Join play.corebuilders.gg"));
    }

    private static String resource(String path) throws IOException {
        try (InputStream input = MarketplaceWebContentTest.class.getResourceAsStream(path)) {
            if (input == null) throw new IOException("Missing test resource: " + path);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
