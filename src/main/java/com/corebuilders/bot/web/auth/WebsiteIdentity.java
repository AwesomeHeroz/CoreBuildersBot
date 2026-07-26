package com.corebuilders.bot.web.auth;

import java.util.UUID;

public interface WebsiteIdentity {
    SessionPrincipal ensureProfile(DiscordIdentity identity);
    long contributionPointBalance(UUID memberId);
}
