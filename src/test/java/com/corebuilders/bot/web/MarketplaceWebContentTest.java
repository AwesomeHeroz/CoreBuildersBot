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

    @Test
    void usesCoinsForMarketplacePrices() throws IOException {
        String html = resource("/web/index.html");
        String javascript = resource("/web/app.js");

        assertTrue(html.contains("Purchases use coins"));
        assertTrue(html.contains("id=\"cart-total\">0 coins"));
        assertTrue(javascript.contains("const coins ="));
        assertTrue(javascript.contains("const shortCoins ="));
        assertTrue(!html.contains("contribution points"));
        assertTrue(!javascript.contains(" contribution points"));
        assertTrue(!javascript.contains(" CP`"));
    }

    @Test
    void exposesBuyerSellerDeliveryAndCancellationActions() throws IOException {
        String javascript = resource("/web/app.js");
        assertTrue(javascript.contains("/api/orders/${line.id}/delivered"));
        assertTrue(javascript.contains("/api/orders/${line.id}/cancel"));
        assertTrue(javascript.contains("/api/sales/${sale.id}/confirm"));
        assertTrue(javascript.contains("/api/sales/${sale.id}/cancel"));
        assertTrue(javascript.contains("The seller must now confirm it"));
    }


    @Test
    void refreshesAllAuthenticationDependentViews() throws IOException {
        String javascript = resource("/web/app.js");

        assertTrue(javascript.contains("async function refreshAuthenticationUi"));
        assertTrue(javascript.contains("async function synchronizeAuthentication"));
        assertTrue(javascript.contains("window.addEventListener('pageshow'"));
        assertTrue(javascript.contains("document.addEventListener('visibilitychange'"));
        assertTrue(javascript.contains("if (response.status === 401"));
        assertTrue(javascript.contains("if (xhr.status === 401) invalidateAuthentication()"));
        assertTrue(javascript.contains("tasks.push(loadItems())"));
        assertTrue(javascript.contains("tasks.push(loadCart())"));
        assertTrue(javascript.contains("preserveShopEditor: !identityChanged"));
        assertTrue(javascript.contains("if (preserveEditor && (shopProfileDirty || itemEditorDirty)) return"));
        assertTrue(javascript.contains("await Promise.all([loadShop(), loadItems()])"));
    }

    private static String resource(String path) throws IOException {
        try (InputStream input = MarketplaceWebContentTest.class.getResourceAsStream(path)) {
            if (input == null) throw new IOException("Missing test resource: " + path);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
