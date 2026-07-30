package com.corebuilders.bot.service;

import com.corebuilders.bot.model.MarketplaceModels.DisputeResolution;
import com.corebuilders.bot.model.MarketplaceModels.MarketplaceOrderLine;

import java.util.List;
import java.util.UUID;

/** Staff-only marketplace dispute workflow exposed to trusted adapters such as Discord. */
public interface MarketplaceDisputeOperations {
    List<MarketplaceOrderLine> disputes(int limit);

    MarketplaceOrderLine resolveDispute(
            UUID lineId,
            DisputeResolution resolution,
            String actorDiscordId,
            String reason
    );
}
