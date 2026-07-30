package com.corebuilders.bot.service;

/**
 * Backward-compatible aggregate marketplace port.
 *
 * New adapters should depend on the narrowest use-case interface they need.
 */
public interface MarketplaceOperations extends
        MarketplaceCatalogOperations,
        MarketplaceShopManagementOperations,
        MarketplaceCartOperations,
        MarketplaceOrderOperations {
}
