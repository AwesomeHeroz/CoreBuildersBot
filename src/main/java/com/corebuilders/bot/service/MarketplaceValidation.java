package com.corebuilders.bot.service;

import com.corebuilders.bot.model.MarketplaceModels.ItemInput;
import com.corebuilders.bot.model.MarketplaceModels.ShopInput;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

import static com.corebuilders.bot.service.MarketplaceException.validation;

public final class MarketplaceValidation {
    private MarketplaceValidation() {}

    public static ShopInput shop(ShopInput input) {
        if (input == null) throw validation("Shop details are required.");
        return new ShopInput(
                required(input.name(), "Shop name", 3, 120),
                required(input.description(), "Shop description", 3, 1000)
        );
    }

    public static ItemInput item(ItemInput input) {
        if (input == null) throw validation("Item details are required.");
        if (input.stock() < 0 || input.stock() > 1_000_000) {
            throw validation("Stock must be between 0 and 1,000,000.");
        }
        if (input.price() <= 0 || input.price() > 1_000_000_000L) {
            throw validation("Price must be between 1 and 1,000,000,000 contribution points.");
        }
        String imageUrl = optional(input.imageUrl(), 1000);
        if (imageUrl != null) validateImageUrl(imageUrl);
        return new ItemInput(
                required(input.name(), "Item name", 2, 150),
                required(input.description(), "Item description", 2, 1000),
                imageUrl,
                input.stock(),
                input.price(),
                normalizeCategory(input.category()),
                input.active()
        );
    }

    public static int quantity(int quantity) {
        if (quantity < 1 || quantity > 999) {
            throw validation("Quantity must be between 1 and 999.");
        }
        return quantity;
    }

    public static int limit(int limit) {
        return Math.max(1, Math.min(50, limit));
    }

    private static String normalizeCategory(String value) {
        String category = required(value, "Category", 2, 64);
        return category.replaceAll("\\s+", " ").trim();
    }

    private static void validateImageUrl(String value) {
        try {
            URI uri = new URI(value);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!("http".equals(scheme) || "https".equals(scheme)) || uri.getHost() == null) {
                throw validation("Image must be a valid HTTP or HTTPS URL.");
            }
        } catch (URISyntaxException error) {
            throw validation("Image must be a valid HTTP or HTTPS URL.");
        }
    }

    private static String required(String value, String label, int min, int max) {
        if (value == null) throw validation(label + " is required.");
        String cleaned = value.trim().replaceAll("\\s+", " ");
        if (cleaned.length() < min || cleaned.length() > max) {
            throw validation(label + " must be between " + min + " and " + max + " characters.");
        }
        return cleaned;
    }

    private static String optional(String value, int max) {
        if (value == null || value.isBlank()) return null;
        String cleaned = value.trim();
        if (cleaned.length() > max) throw validation("Image URL is too long.");
        return cleaned;
    }
}
