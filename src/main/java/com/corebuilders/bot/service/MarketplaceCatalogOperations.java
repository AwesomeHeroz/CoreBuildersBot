package com.corebuilders.bot.service;

import com.corebuilders.bot.model.MarketplaceModels.ItemPage;
import com.corebuilders.bot.model.MarketplaceModels.ItemSearch;
import com.corebuilders.bot.model.MarketplaceModels.MarketplaceItem;
import com.corebuilders.bot.model.MarketplaceModels.PlayerShop;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Read-only marketplace catalog use cases. */
public interface MarketplaceCatalogOperations {
    ItemPage searchItems(ItemSearch search);
    Optional<MarketplaceItem> findItem(UUID itemId);
    List<String> categories();
    Optional<PlayerShop> findShop(UUID shopId);
    List<MarketplaceItem> shopItems(UUID shopId);
}
