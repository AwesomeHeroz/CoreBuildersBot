package com.corebuilders.bot.web.auth;

import java.util.UUID;

public record SessionPrincipal(
        UUID memberId,
        String discordUserId,
        String username,
        String avatarUrl
) {}
