package com.corebuilders.bot.discord;

import com.corebuilders.bot.model.MarketplaceModels.MarketplaceOrder;
import com.corebuilders.bot.model.MarketplaceModels.MarketplaceOrderLine;
import com.corebuilders.bot.service.MarketplaceOrderOperations;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Synchronizes Discord tickets after every marketplace order-state transition. */
public final class TicketingMarketplaceOrderOperations implements MarketplaceOrderOperations {
    private final MarketplaceOrderOperations delegate;
    private final MarketplaceTicketCoordinator tickets;

    public TicketingMarketplaceOrderOperations(MarketplaceOrderOperations delegate, MarketplaceTicketCoordinator tickets) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.tickets = Objects.requireNonNull(tickets, "tickets");
    }

    @Override public List<MarketplaceOrder> purchases(UUID buyerMemberId, int limit) {
        return delegate.purchases(buyerMemberId, limit);
    }
    @Override public List<MarketplaceOrderLine> sales(UUID sellerMemberId, int limit) {
        return delegate.sales(sellerMemberId, limit);
    }
    @Override public MarketplaceOrderLine markDelivered(UUID buyerMemberId, UUID lineId) {
        return refresh(delegate.markDelivered(buyerMemberId, lineId));
    }
    @Override public MarketplaceOrderLine confirmDelivery(UUID sellerMemberId, UUID lineId) {
        return refresh(delegate.confirmDelivery(sellerMemberId, lineId));
    }
    @Override public MarketplaceOrderLine cancelLine(UUID buyerMemberId, UUID lineId) {
        return refresh(delegate.cancelLine(buyerMemberId, lineId));
    }
    @Override public MarketplaceOrderLine cancelSale(UUID sellerMemberId, UUID lineId) {
        return refresh(delegate.cancelSale(sellerMemberId, lineId));
    }
    @Override public MarketplaceOrderLine disputeLine(UUID buyerMemberId, UUID lineId, String reason) {
        return refresh(delegate.disputeLine(buyerMemberId, lineId, reason));
    }

    private MarketplaceOrderLine refresh(MarketplaceOrderLine line) {
        tickets.refresh(line.id());
        return line;
    }
}
