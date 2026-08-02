package com.corebuilders.bot.service;

import com.corebuilders.bot.model.MarketplaceModels.MarketplaceOrder;
import com.corebuilders.bot.model.MarketplaceModels.MarketplaceOrderLine;

import java.util.List;
import java.util.UUID;

/** Purchase, sale, delivery, cancellation, and dispute-opening use cases. */
public interface MarketplaceOrderOperations {
    List<MarketplaceOrder> purchases(UUID buyerMemberId, int limit);
    List<MarketplaceOrderLine> sales(UUID sellerMemberId, int limit);

    /** Buyer reports that the item has been received/delivered. */
    MarketplaceOrderLine markDelivered(UUID buyerMemberId, UUID lineId);

    /** Seller confirms completion; escrow is then released to the seller. */
    MarketplaceOrderLine confirmDelivery(UUID sellerMemberId, UUID lineId);

    MarketplaceOrderLine cancelLine(UUID buyerMemberId, UUID lineId);
    MarketplaceOrderLine cancelSale(UUID sellerMemberId, UUID lineId);
    MarketplaceOrderLine disputeLine(UUID buyerMemberId, UUID lineId, String reason);
}
