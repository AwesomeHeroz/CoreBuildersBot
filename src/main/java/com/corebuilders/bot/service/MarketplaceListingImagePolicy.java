package com.corebuilders.bot.service;

/** Validation port for marketplace listing images. */
@FunctionalInterface
public interface MarketplaceListingImagePolicy {
    String validate(String value);
}
