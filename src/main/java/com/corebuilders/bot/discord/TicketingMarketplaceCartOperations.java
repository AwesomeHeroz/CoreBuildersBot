package com.corebuilders.bot.discord;

import com.corebuilders.bot.model.MarketplaceModels.CheckoutRequest;
import com.corebuilders.bot.model.MarketplaceModels.MarketplaceCart;
import com.corebuilders.bot.model.MarketplaceModels.MarketplaceOrder;
import com.corebuilders.bot.service.MarketplaceCartOperations;

import java.util.Objects;
import java.util.UUID;

/** Adds Discord ticket creation after a successful marketplace checkout. */
public final class TicketingMarketplaceCartOperations implements MarketplaceCartOperations {
    private final MarketplaceCartOperations delegate;
    private final MarketplaceTicketCoordinator tickets;

    public TicketingMarketplaceCartOperations(MarketplaceCartOperations delegate, MarketplaceTicketCoordinator tickets) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.tickets = Objects.requireNonNull(tickets, "tickets");
    }

    @Override public MarketplaceCart cart(UUID memberId) { return delegate.cart(memberId); }
    @Override public MarketplaceCart setCartQuantity(UUID memberId, UUID itemId, int quantity) {
        return delegate.setCartQuantity(memberId, itemId, quantity);
    }
    @Override public MarketplaceCart removeCartItem(UUID memberId, UUID itemId) {
        return delegate.removeCartItem(memberId, itemId);
    }

    @Override
    public MarketplaceOrder checkout(UUID buyerMemberId, String actorDiscordId, CheckoutRequest request) {
        MarketplaceOrder order = delegate.checkout(buyerMemberId, actorDiscordId, request);
        tickets.ensureTickets(order);
        return order;
    }
}
