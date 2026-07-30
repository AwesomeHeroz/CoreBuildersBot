package com.corebuilders.bot.web.auth;

import java.util.UUID;

public interface WebsiteIdentity {
    /**
     * Legacy adapter method retained for source compatibility. Website login no
     * longer creates a session from Discord OAuth.
     */
    SessionPrincipal ensureProfile(DiscordIdentity identity);

    default SessionPrincipal requireProfile(UUID memberId) {
        throw new UnsupportedOperationException("Member based website profiles are not supported by this adapter.");
    }

    default SessionPrincipal linkDiscord(UUID memberId, DiscordIdentity identity) {
        throw new UnsupportedOperationException("Discord account linking is not supported by this adapter.");
    }

    long contributionPointBalance(UUID memberId);
}
