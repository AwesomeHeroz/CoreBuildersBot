package com.corebuilders.bot.model;

import com.corebuilders.bot.model.Models.ShopItem;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ShopCatalogTest {
    @Test
    void normalizesCodesAndRetainsStockPolicy() {
        ShopCatalog catalog = new ShopCatalog(List.of(
                new ShopItem(" basic_gear ", " Basic Gear ", " Gear package ", 250, 4, true)
        ), true, false);

        ShopItem item = catalog.items().getFirst();
        assertEquals("BASIC_GEAR", item.code());
        assertEquals("Basic Gear", item.name());
        assertEquals("Gear package", item.description());
        assertEquals(4, item.stock());
        assertTrue(catalog.disableUnlistedItems());
        assertFalse(catalog.syncStockOnStartup());
    }

    @Test
    void allowsExplicitlyEmptyCatalog() {
        ShopCatalog catalog = new ShopCatalog(List.of(), true, false);
        assertTrue(catalog.items().isEmpty());
    }

    @Test
    void rejectsDuplicateOrInvalidItems() {
        assertThrows(IllegalArgumentException.class, () -> new ShopCatalog(List.of(
                new ShopItem("ITEM", "One", "First", 1, null, true),
                new ShopItem("item", "Two", "Second", 2, null, true)
        ), true, false));
        assertThrows(IllegalArgumentException.class, () -> new ShopCatalog(List.of(
                new ShopItem("ITEM", "Item", "Description", -1, null, true)
        ), true, false));
        assertThrows(IllegalArgumentException.class, () -> new ShopCatalog(List.of(
                new ShopItem("ITEM", "Item", "Description", 1, -1, true)
        ), true, false));
    }
}
