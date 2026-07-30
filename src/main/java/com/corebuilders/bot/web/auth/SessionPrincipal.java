package com.corebuilders.bot.web.auth;

import java.util.UUID;

/** Immutable session identity snapshot. securityVersion invalidates stale sessions after account changes. */
public record SessionPrincipal(
        UUID memberId,
        String discordUserId,
        String username,
        String avatarUrl,
        long securityVersion
) {}
