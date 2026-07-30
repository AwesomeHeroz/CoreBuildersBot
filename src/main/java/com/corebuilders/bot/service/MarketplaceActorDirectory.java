package com.corebuilders.bot.service;

import java.util.UUID;

/** Resolves the audit identity associated with a marketplace member. */
@FunctionalInterface
public interface MarketplaceActorDirectory {
    String discordIdFor(UUID memberId);
}
