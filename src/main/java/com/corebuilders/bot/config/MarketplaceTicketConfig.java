package com.corebuilders.bot.config;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.Objects;

/** Discord channel configuration for marketplace fulfilment tickets. */
public record MarketplaceTicketConfig(
        boolean enabled,
        String category,
        String namePattern,
        boolean lockOnTerminalState,
        int reconciliationLimit
) {
    public MarketplaceTicketConfig {
        category = clean(category);
        namePattern = clean(namePattern);
        if (enabled && category.isBlank()) {
            throw new IllegalStateException("discord.marketplace-tickets.category is required when tickets are enabled.");
        }
        if (namePattern.isBlank()) namePattern = "order-{item}-{id}";
        reconciliationLimit = Math.max(1, Math.min(500, reconciliationLimit));
    }

    public static MarketplaceTicketConfig from(FileConfiguration config) {
        Objects.requireNonNull(config, "config");
        return new MarketplaceTicketConfig(
                config.getBoolean("discord.marketplace-tickets.enabled", true),
                config.getString("discord.marketplace-tickets.category", "marketplace-orders"),
                config.getString("discord.marketplace-tickets.name-pattern", "order-{item}-{id}"),
                config.getBoolean("discord.marketplace-tickets.lock-on-terminal-state", true),
                config.getInt("discord.marketplace-tickets.reconciliation-limit", 100)
        );
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
