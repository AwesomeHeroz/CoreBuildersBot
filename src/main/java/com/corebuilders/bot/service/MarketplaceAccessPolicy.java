package com.corebuilders.bot.service;

import java.util.UUID;

/**
 * Authorization port for marketplace use cases.
 *
 * Keeping the policy behind an interface lets application workflows depend on
 * behavior instead of the QueryDSL implementation used at runtime.
 */
public interface MarketplaceAccessPolicy {
    void requireAuthorized(UUID memberId);

    /** Must be called inside a transaction before a security-sensitive mutation. */
    void requireAuthorizedForUpdate(UUID memberId);
}
