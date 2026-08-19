package com.corebuilders.bot.service;

import com.corebuilders.bot.model.Domain.SpawnHelpStatus;
import com.corebuilders.bot.model.Models.SpawnHelpTicket;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Application service for spawn-help requests. It contains no Discord/JDA or database code. */
public final class SpawnHelpService {
    private final SpawnHelpTicketRepository repository;
    private final SpawnHelpTicketPolicy policy;
    private final Map<String, String> serverNames;

    public SpawnHelpService(SpawnHelpTicketRepository repository, Map<String, String> serverNames) {
        this(repository, serverNames, new SpawnHelpTicketPolicy());
    }

    SpawnHelpService(SpawnHelpTicketRepository repository, Map<String, String> serverNames, SpawnHelpTicketPolicy policy) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.policy = Objects.requireNonNull(policy, "policy");
        this.serverNames = Map.copyOf(new LinkedHashMap<>(Objects.requireNonNull(serverNames, "serverNames")));
    }

    public SpawnHelpTicket create(String discordUserId, String discordUsername, String serverId, String inGameName) {
        String serverName = serverNames.get(serverId);
        if (serverName == null) throw new IllegalArgumentException("Unknown or disabled help server: " + serverId);
        String ign = policy.validateInGameName(inGameName);
        String username = clean(discordUsername, 100, "Discord username");
        String userId = clean(discordUserId, 32, "Discord user ID");
        Instant now = Instant.now();
        SpawnHelpTicket ticket = new SpawnHelpTicket(
                UUID.randomUUID(), userId, username, serverId, serverName, ign,
                SpawnHelpStatus.OPEN, null, null, null, null, null,
                now, now, null
        );
        return repository.createOpen(ticket);
    }

    public Optional<SpawnHelpTicket> activeForUser(String discordUserId) {
        return repository.activeForUser(discordUserId);
    }

    public SpawnHelpTicket attachChannel(UUID id, String channelId, String controlMessageId) {
        return repository.setChannel(id, clean(channelId, 32, "channel ID"), clean(controlMessageId, 32, "message ID"));
    }

    public SpawnHelpTicket claim(UUID id, String helperDiscordId, String helperUsername) {
        SpawnHelpTicket current = require(id);
        policy.requireOpenForClaim(current);
        return repository.claim(id, clean(helperDiscordId, 32, "helper Discord ID"), clean(helperUsername, 100, "helper username"));
    }

    public SpawnHelpTicket complete(UUID id, String helperDiscordId) {
        SpawnHelpTicket current = require(id);
        policy.requireCompletableBy(current, helperDiscordId);
        return repository.complete(id, helperDiscordId);
    }

    public SpawnHelpTicket deny(UUID id, String helperDiscordId, String helperUsername, String reason) {
        SpawnHelpTicket current = require(id);
        policy.requireDeniableBy(current, helperDiscordId);
        return repository.deny(
                id,
                clean(helperDiscordId, 32, "helper Discord ID"),
                clean(helperUsername, 100, "helper username"),
                policy.validateDenialReason(reason)
        );
    }

    public SpawnHelpTicket require(UUID id) {
        return repository.find(id).orElseThrow(() -> new IllegalArgumentException("Spawn-help ticket not found: " + id));
    }

    public void abortCreation(UUID id, String discordUserId) {
        repository.abortUninitialized(id, discordUserId);
    }

    private static String clean(String value, int max, String label) {
        String cleaned = value == null ? "" : value.trim();
        if (cleaned.isBlank()) throw new IllegalArgumentException(label + " is required.");
        return cleaned.length() <= max ? cleaned : cleaned.substring(0, max);
    }
}
