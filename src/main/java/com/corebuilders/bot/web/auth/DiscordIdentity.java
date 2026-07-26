package com.corebuilders.bot.web.auth;

public record DiscordIdentity(
        String id,
        String username,
        String displayName,
        String avatarUrl
) {}
