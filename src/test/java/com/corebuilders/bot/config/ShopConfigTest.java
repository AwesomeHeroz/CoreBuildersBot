package com.corebuilders.bot.config;

import com.corebuilders.bot.model.ShopCatalog;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ShopConfigTest {
    @Test
    void loadsFiniteAndUnlimitedStock() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString("""
                shop:
                  disable-unlisted-items: true
                  sync-stock-on-startup: true
                  items:
                    - code: finite_item
                      name: Finite Item
                      description: Limited supply
                      price: 50
                      stock: 7
                    - code: unlimited_item
                      name: Unlimited Item
                      description: Unlimited supply
                      price: 75
                      stock: unlimited
                      active: false
                """);

        ShopCatalog catalog = ShopConfig.from(config);

        assertEquals(2, catalog.items().size());
        assertEquals(7, catalog.items().get(0).stock());
        assertNull(catalog.items().get(1).stock());
        assertFalse(catalog.items().get(1).active());
        assertTrue(catalog.disableUnlistedItems());
        assertTrue(catalog.syncStockOnStartup());
    }

    @Test
    void explicitEmptyListKeepsCatalogEmpty() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString("""
                shop:
                  disable-unlisted-items: true
                  items: []
                """);

        ShopCatalog catalog = ShopConfig.from(config);

        assertTrue(catalog.items().isEmpty());
        assertTrue(catalog.disableUnlistedItems());
    }

    @Test
    void missingShopSectionUsesSeededDefaultsWithoutDisablingUnknownRows() {
        ShopCatalog catalog = ShopConfig.from(new YamlConfiguration());

        assertEquals(7, catalog.items().size());
        assertFalse(catalog.disableUnlistedItems());
        assertFalse(catalog.syncStockOnStartup());
    }

    @Test
    void rejectsInvalidStock() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString("""
                shop:
                  items:
                    - code: item
                      name: Item
                      description: Invalid stock
                      price: 50
                      stock: many
                """);

        assertThrows(IllegalArgumentException.class, () -> ShopConfig.from(config));
    }
}
