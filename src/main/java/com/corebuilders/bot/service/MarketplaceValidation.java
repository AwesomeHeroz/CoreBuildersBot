package com.corebuilders.bot.service;

import com.corebuilders.bot.model.MarketplaceModels.ItemInput;
import com.corebuilders.bot.model.MarketplaceModels.ShopInput;

import static com.corebuilders.bot.service.MarketplaceException.validation;

public final class MarketplaceValidation {
    private MarketplaceValidation() {}

    public static ShopInput shop(ShopInput input) {
        if (input == null) throw validation("Shop details are required.");
        return new ShopInput(required(input.name(), "Shop name", 3, 120),
                required(input.description(), "Shop description", 3, 1000));
    }

    public static ItemInput item(ItemInput input, MarketplaceListingImagePolicy imagePolicy) {
        if (input == null) throw validation("Item details are required.");
        if (input.stock() < 0 || input.stock() > 1_000_000) {
            throw validation("Stock must be between 0 and 1,000,000.");
        }
        if (input.price() <= 0 || input.price() > 1_000_000_000L) {
            throw validation("Price must be between 1 and 1,000,000,000 contribution points.");
        }
        String imageUrl = imagePolicy.validate(input.imageUrl());
        return new ItemInput(required(input.name(), "Item name", 2, 150),
                required(input.description(), "Item description", 2, 1000), imageUrl,
                input.stock(), input.price(), normalizeCategory(input.category()), input.active());
    }

    public static int quantity(int quantity) {
        if (quantity < 1 || quantity > 999) throw validation("Quantity must be between 1 and 999.");
        return quantity;
    }

    public static int limit(int limit) { return Math.max(1, Math.min(50, limit)); }

    public static String disputeReason(String value) {
        return required(value, "Dispute reason", 5, 500);
    }

    private static String normalizeCategory(String value) {
        return required(value, "Category", 2, 64).replaceAll("\\s+", " ").trim();
    }

    private static String required(String value, String label, int min, int max) {
        if (value == null) throw validation(label + " is required.");
        String cleaned = value.trim().replaceAll("\\s+", " ");
        if (cleaned.length() < min || cleaned.length() > max) {
            throw validation(label + " must be between " + min + " and " + max + " characters.");
        }
        return cleaned;
    }
}
