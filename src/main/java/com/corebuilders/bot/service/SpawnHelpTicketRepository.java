package com.corebuilders.bot.service;

import com.corebuilders.bot.model.Models.SpawnHelpTicket;

import java.util.Optional;
import java.util.UUID;

/** Persistence boundary for spawn-help tickets. Implementations must make transitions atomic. */
public interface SpawnHelpTicketRepository {
    SpawnHelpTicket createOpen(SpawnHelpTicket ticket);
    Optional<SpawnHelpTicket> find(UUID id);
    Optional<SpawnHelpTicket> activeForUser(String discordUserId);
    SpawnHelpTicket setChannel(UUID id, String channelId, String controlMessageId);
    SpawnHelpTicket claim(UUID id, String helperDiscordId, String helperUsername);
    SpawnHelpTicket complete(UUID id, String helperDiscordId);
    SpawnHelpTicket deny(UUID id, String helperDiscordId, String helperUsername, String reason);
    void abortUninitialized(UUID id, String discordUserId);
}
