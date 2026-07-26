package com.corebuilders.bot.config;

import com.corebuilders.bot.model.Models.ShopItem;
import com.corebuilders.bot.model.ShopCatalog;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Loads the Core Credit shop catalog from config.yml. */
public final class ShopConfig {
    private ShopConfig() {}

    public static ShopCatalog from(FileConfiguration config) {
        Objects.requireNonNull(config, "config");

        boolean hasShopSection = config.isSet("shop");
        boolean disableUnlisted = hasShopSection
                && config.getBoolean("shop.disable-unlisted-items", true);
        boolean syncStock = hasShopSection
                && config.getBoolean("shop.sync-stock-on-startup", false);
        boolean itemsConfigured = config.isSet("shop.items");
        List<Map<?, ?>> rawItems = config.getMapList("shop.items");

        if (!itemsConfigured) {
            return new ShopCatalog(defaultItems(), disableUnlisted, syncStock);
        }

        List<ShopItem> items = new ArrayList<>(rawItems.size());
        for (Map<?, ?> raw : rawItems) {
            items.add(new ShopItem(
                    requiredText(raw.get("code"), "code"),
                    requiredText(raw.get("name"), "name"),
                    requiredText(raw.get("description"), "description"),
                    number(raw.get("price"), "price"),
                    stock(raw.get("stock")),
                    booleanValue(raw.get("active"), true)
            ));
        }
        return new ShopCatalog(items, disableUnlisted, syncStock);
    }

    static List<ShopItem> defaultItems() {
        return List.of(
                new ShopItem("BASIC_GEAR", "Basic Gear Shulker", "A basic gear package for group members.", 250, null, true),
                new ShopItem("SURVIVAL_KIT", "Survival Kit", "A complete survival resupply kit.", 400, null, true),
                new ShopItem("XP_SHULKER", "XP Bottle Shulker", "A shulker of XP bottles.", 500, null, true),
                new ShopItem("BUILDING_PACKAGE", "Building Material Package", "A standard package of building materials.", 750, null, true),
                new ShopItem("LARGE_MATERIAL_REQUEST", "Large Material Request", "A larger custom material allocation.", 2_000, null, true),
                new ShopItem("CUSTOM_MAPART", "Custom Mapart Request", "Request a custom group mapart project.", 3_000, null, true),
                new ShopItem("PROJECT_ASSISTANCE", "Group Project Assistance", "Request organized group assistance for a project.", 5_000, null, true)
        );
    }

    private static String requiredText(Object value, String field) {
        String text = value == null ? "" : String.valueOf(value).trim();
        if (text.isBlank()) {
            throw new IllegalArgumentException("Missing shop item field: " + field);
        }
        return text;
    }

    private static long number(Object value, String field) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            throw new IllegalArgumentException("Missing shop item field: " + field);
        }
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("Invalid shop item " + field + ": " + value, error);
        }
    }

    private static Integer stock(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        if (text.isBlank() || "unlimited".equalsIgnoreCase(text) || "null".equalsIgnoreCase(text)) {
            return null;
        }
        if (value instanceof Number number) {
            long stock = number.longValue();
            if (stock > Integer.MAX_VALUE || stock < Integer.MIN_VALUE) {
                throw new IllegalArgumentException("Shop item stock is outside the supported integer range: " + value);
            }
            return (int) stock;
        }
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("Invalid shop item stock: " + value, error);
        }
    }

    private static boolean booleanValue(Object value, boolean fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        String text = String.valueOf(value).trim();
        if ("true".equalsIgnoreCase(text)) {
            return true;
        }
        if ("false".equalsIgnoreCase(text)) {
            return false;
        }
        throw new IllegalArgumentException("Shop item active must be true or false: " + value);
    }
}
