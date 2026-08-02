package com.corebuilders.bot.discord;

import com.corebuilders.bot.model.MarketplaceModels.DisputeResolution;
import com.corebuilders.bot.model.MarketplaceModels.MarketplaceOrderLine;
import com.corebuilders.bot.service.MarketplaceDisputeOperations;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Keeps Discord tickets synchronized when leadership resolves a dispute. */
public final class TicketingMarketplaceDisputeOperations implements MarketplaceDisputeOperations {
    private final MarketplaceDisputeOperations delegate;
    private final MarketplaceTicketCoordinator tickets;

    public TicketingMarketplaceDisputeOperations(MarketplaceDisputeOperations delegate,
                                                 MarketplaceTicketCoordinator tickets) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.tickets = Objects.requireNonNull(tickets, "tickets");
    }

    @Override public List<MarketplaceOrderLine> disputes(int limit) { return delegate.disputes(limit); }

    @Override
    public MarketplaceOrderLine resolveDispute(UUID lineId, DisputeResolution resolution,
                                               String actorDiscordId, String reason) {
        MarketplaceOrderLine line = delegate.resolveDispute(lineId, resolution, actorDiscordId, reason);
        tickets.refresh(line.id());
        return line;
    }
}
