package com.corebuilders.bot.service;

import com.corebuilders.bot.model.MarketplaceModels.DisputeResolution;
import com.corebuilders.bot.model.MarketplaceModels.ItemInput;
import com.corebuilders.bot.model.MarketplaceModels.ShopInput;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class MarketplaceValidationTest {
    private static final MarketplaceImagePolicy IMAGES = new MarketplaceImagePolicy(Set.of("example.com"));

    @Test
    void normalizesValidShopAndItemInput() {
        ShopInput shop = MarketplaceValidation.shop(new ShopInput("  Redstone   Works ", "  Useful machines  "));
        ItemInput item = MarketplaceValidation.item(new ItemInput(
                "  XP   Shulker ", "  Bottles for repairs  ", "https://images.example.com/item.png",
                12, 450, "  Resources  ", true), IMAGES);
        assertEquals("Redstone Works", shop.name());
        assertEquals("Useful machines", shop.description());
        assertEquals("XP Shulker", item.name());
        assertEquals("Resources", item.category());
    }

    @Test
    void rejectsUnapprovedImagesAndInvalidNumbers() {
        assertThrows(MarketplaceException.class, () -> MarketplaceValidation.item(
                new ItemInput("Item", "Description", "javascript:alert(1)", 1, 10, "Kits", true), IMAGES));
        assertThrows(MarketplaceException.class, () -> MarketplaceValidation.item(
                new ItemInput("Item", "Description", "https://untrusted.test/a.png", 1, 10, "Kits", true), IMAGES));
        assertThrows(MarketplaceException.class, () -> MarketplaceValidation.item(
                new ItemInput("Item", "Description", null, -1, 10, "Kits", true), IMAGES));
        assertThrows(MarketplaceException.class, () -> MarketplaceValidation.quantity(0));
    }
    @Test
    void parsesOnlyExplicitDisputeResolutions() {
        assertEquals(DisputeResolution.REFUND_BUYER, DisputeResolution.parse("refund_buyer"));
        assertEquals(DisputeResolution.RELEASE_SELLER, DisputeResolution.parse(" RELEASE_SELLER "));
        assertThrows(IllegalArgumentException.class, () -> DisputeResolution.parse("split_payment"));
        assertThrows(IllegalArgumentException.class, () -> DisputeResolution.parse(""));
    }

}
