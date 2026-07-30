package com.corebuilders.bot.service;

import com.corebuilders.bot.model.MarketplaceModels.CheckoutRequest;
import com.corebuilders.bot.model.MarketplaceModels.MarketplaceCart;
import com.corebuilders.bot.model.MarketplaceModels.MarketplaceOrder;

import java.util.UUID;

/** Buyer cart and checkout use cases. */
public interface MarketplaceCartOperations {
    MarketplaceCart cart(UUID memberId);
    MarketplaceCart setCartQuantity(UUID memberId, UUID itemId, int quantity);
    MarketplaceCart removeCartItem(UUID memberId, UUID itemId);
    MarketplaceOrder checkout(UUID buyerMemberId, String actorDiscordId, CheckoutRequest request);
}
