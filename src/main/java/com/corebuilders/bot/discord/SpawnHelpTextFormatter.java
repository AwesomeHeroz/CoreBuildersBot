package com.corebuilders.bot.discord;

import com.corebuilders.bot.model.Domain.SpawnHelpStatus;
import com.corebuilders.bot.model.Models.SpawnHelpTicket;

import java.util.Locale;

/** Pure formatting helper for spawn-help Discord channel names and status labels. */
public final class SpawnHelpTextFormatter {
    private final String namePattern;

    public SpawnHelpTextFormatter(String namePattern) {
        this.namePattern = namePattern == null || namePattern.isBlank() ? "help-{server}-{ign}-{id}" : namePattern;
    }

    public String ticketName(SpawnHelpTicket ticket) {
        String shortId = ticket.id().toString().substring(0, 8);
        String value = namePattern
                .replace("{server}", ticket.serverName())
                .replace("{server-id}", ticket.serverId())
                .replace("{ign}", ticket.inGameName())
                .replace("{user}", ticket.discordUsername())
                .replace("{id}", shortId)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9-]+", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
        if (value.isBlank()) value = "spawn-help-" + shortId;
        return value.length() <= 90 ? value : value.substring(0, 90);
    }

    public static String statusLabel(SpawnHelpStatus status) {
        return switch (status) {
            case OPEN -> "OPEN";
            case ON_HELP -> "ON HELP";
            case COMPLETED -> "COMPLETED";
            case DENIED -> "DENIED";
        };
    }
}
