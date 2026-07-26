package com.corebuilders.bot.model;

import com.corebuilders.bot.model.Models.ShopItem;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/** Validated shop catalog plus startup synchronization policy. */
public final class ShopCatalog {
    private final List<ShopItem> items;
    private final boolean disableUnlistedItems;
    private final boolean syncStockOnStartup;

    public ShopCatalog(
            List<ShopItem> configuredItems,
            boolean disableUnlistedItems,
            boolean syncStockOnStartup
    ) {
        Objects.requireNonNull(configuredItems, "configuredItems");

        Set<String> codes = new HashSet<>();
        List<ShopItem> validated = new ArrayList<>(configuredItems.size());
        for (ShopItem configured : configuredItems) {
            ShopItem item = Objects.requireNonNull(configured, "Configured shop items cannot contain null.");
            String code = requireText(item.code(), "Shop item code").toUpperCase(Locale.ROOT);
            String name = requireText(item.name(), "Shop item name");
            String description = requireText(item.description(), "Shop item description");

            if (!code.matches("[A-Z0-9_]{2,64}")) {
                throw new IllegalArgumentException("Shop item code must match [A-Z0-9_]{2,64}: " + code);
            }
            if (!codes.add(code)) {
                throw new IllegalArgumentException("Duplicate shop item code: " + code);
            }
            if (name.length() > 150) {
                throw new IllegalArgumentException("Shop item name cannot exceed 150 characters: " + code);
            }
            if (description.length() > 500) {
                throw new IllegalArgumentException("Shop item description cannot exceed 500 characters: " + code);
            }
            if (item.price() < 0) {
                throw new IllegalArgumentException("Shop item price cannot be negative: " + code);
            }
            if (item.stock() != null && item.stock() < 0) {
                throw new IllegalArgumentException("Shop item stock cannot be negative: " + code);
            }

            validated.add(new ShopItem(code, name, description, item.price(), item.stock(), item.active()));
        }

        this.items = List.copyOf(validated);
        this.disableUnlistedItems = disableUnlistedItems;
        this.syncStockOnStartup = syncStockOnStartup;
    }

    public List<ShopItem> items() {
        return items;
    }

    public boolean disableUnlistedItems() {
        return disableUnlistedItems;
    }

    public boolean syncStockOnStartup() {
        return syncStockOnStartup;
    }

    private static String requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " is required.");
        }
        return value.trim();
    }
}
