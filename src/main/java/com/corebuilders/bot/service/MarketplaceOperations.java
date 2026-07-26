package com.corebuilders.bot.service;

import com.corebuilders.bot.model.MarketplaceModels.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Port used by the HTTP adapter and test fakes. */
public interface MarketplaceOperations {
    ItemPage searchItems(ItemSearch search);
    Optional<MarketplaceItem> findItem(UUID itemId);
    List<String> categories();
    Optional<PlayerShop> findShop(UUID shopId);
    List<MarketplaceItem> shopItems(UUID shopId);
    Optional<PlayerShop> findShopByOwner(UUID ownerMemberId);
    PlayerShop createShop(UUID ownerMemberId, ShopInput input);
    PlayerShop updateShop(UUID ownerMemberId, ShopInput input);
    List<MarketplaceItem> ownerItems(UUID ownerMemberId);
    MarketplaceItem createItem(UUID ownerMemberId, ItemInput input);
    MarketplaceItem updateItem(UUID ownerMemberId, UUID itemId, ItemInput input);
    void deactivateItem(UUID ownerMemberId, UUID itemId);
    MarketplaceCart cart(UUID memberId);
    MarketplaceCart setCartQuantity(UUID memberId, UUID itemId, int quantity);
    MarketplaceCart removeCartItem(UUID memberId, UUID itemId);
    MarketplaceOrder checkout(UUID buyerMemberId, String actorDiscordId);
    List<MarketplaceOrder> purchases(UUID buyerMemberId, int limit);
    List<MarketplaceOrderLine> sales(UUID sellerMemberId, int limit);
    MarketplaceOrderLine markDelivered(UUID sellerMemberId, UUID lineId);
}
