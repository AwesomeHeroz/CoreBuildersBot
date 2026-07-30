package com.corebuilders.bot.service;

import com.corebuilders.bot.model.MarketplaceModels.ItemInput;
import com.corebuilders.bot.model.MarketplaceModels.MarketplaceItem;
import com.corebuilders.bot.model.MarketplaceModels.PlayerShop;
import com.corebuilders.bot.model.MarketplaceModels.ShopInput;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Seller-facing shop and listing management use cases. */
public interface MarketplaceShopManagementOperations {
    Optional<PlayerShop> findShopByOwner(UUID ownerMemberId);
    PlayerShop createShop(UUID ownerMemberId, ShopInput input);
    PlayerShop updateShop(UUID ownerMemberId, ShopInput input);
    List<MarketplaceItem> ownerItems(UUID ownerMemberId);
    MarketplaceItem createItem(UUID ownerMemberId, ItemInput input);
    MarketplaceItem updateItem(UUID ownerMemberId, UUID itemId, ItemInput input);
    void deactivateItem(UUID ownerMemberId, UUID itemId);
}
