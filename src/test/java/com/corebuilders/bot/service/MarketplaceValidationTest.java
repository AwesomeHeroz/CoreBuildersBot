package com.corebuilders.bot.service;

import com.corebuilders.bot.model.MarketplaceModels.ItemInput;
import com.corebuilders.bot.model.MarketplaceModels.ShopInput;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MarketplaceValidationTest {
    @Test
    void normalizesValidShopAndItemInput() {
        ShopInput shop = MarketplaceValidation.shop(new ShopInput("  Redstone   Works ", "  Useful machines  "));
        ItemInput item = MarketplaceValidation.item(new ItemInput(
                "  XP   Shulker ", "  Bottles for repairs  ", "https://example.com/item.png",
                12, 450, "  Resources  ", true));

        assertEquals("Redstone Works", shop.name());
        assertEquals("Useful machines", shop.description());
        assertEquals("XP Shulker", item.name());
        assertEquals("Resources", item.category());
        assertEquals(450, item.price());
    }

    @Test
    void rejectsUnsafeImageSchemesAndInvalidNumbers() {
        assertThrows(MarketplaceException.class, () -> MarketplaceValidation.item(
                new ItemInput("Item", "Description", "javascript:alert(1)", 1, 10, "Kits", true)));
        assertThrows(MarketplaceException.class, () -> MarketplaceValidation.item(
                new ItemInput("Item", "Description", null, -1, 10, "Kits", true)));
        assertThrows(MarketplaceException.class, () -> MarketplaceValidation.item(
                new ItemInput("Item", "Description", null, 1, 0, "Kits", true)));
        assertThrows(MarketplaceException.class, () -> MarketplaceValidation.quantity(0));
        assertThrows(MarketplaceException.class, () -> MarketplaceValidation.quantity(1000));
    }
}
