package com.corebuilders.bot.service;

import com.corebuilders.bot.model.MarketplaceModels.MarketplaceOrder;
import com.corebuilders.bot.model.MarketplaceModels.MarketplaceOrderLine;

import java.util.List;
import java.util.UUID;

/** Purchase, sale, delivery, cancellation, and dispute-opening use cases. */
public interface MarketplaceOrderOperations {
    List<MarketplaceOrder> purchases(UUID buyerMemberId, int limit);
    List<MarketplaceOrderLine> sales(UUID sellerMemberId, int limit);
    MarketplaceOrderLine markDelivered(UUID sellerMemberId, UUID lineId);
    MarketplaceOrderLine confirmDelivery(UUID buyerMemberId, UUID lineId);
    MarketplaceOrderLine cancelLine(UUID buyerMemberId, UUID lineId);
    MarketplaceOrderLine disputeLine(UUID buyerMemberId, UUID lineId, String reason);
}
