package com.corebuilders.bot.service;

import com.corebuilders.bot.model.Domain.SpawnHelpStatus;
import com.corebuilders.bot.model.Models.SpawnHelpTicket;

/** Framework-free validation and state-transition rules for spawn-help tickets. */
public final class SpawnHelpTicketPolicy {
    public String validateInGameName(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (!value.matches("[A-Za-z0-9_]{3,16}")) {
            throw new IllegalArgumentException("In-game name must be 3-16 characters using only letters, numbers, or underscores.");
        }
        return value;
    }

    public void requireOpenForClaim(SpawnHelpTicket ticket) {
        if (ticket.status() != SpawnHelpStatus.OPEN) {
            throw new IllegalStateException("This ticket is no longer open for pickup.");
        }
    }

    public void requireCompletableBy(SpawnHelpTicket ticket, String helperDiscordId) {
        if (ticket.status() != SpawnHelpStatus.ON_HELP) {
            throw new IllegalStateException("Only a ticket currently ON HELP can be completed.");
        }
        if (ticket.helperDiscordId() == null || !ticket.helperDiscordId().equals(helperDiscordId)) {
            throw new SecurityException("Only the spawn helper who picked up this ticket can complete it.");
        }
    }

    public void requireDeniableBy(SpawnHelpTicket ticket, String helperDiscordId) {
        if (ticket.status() == SpawnHelpStatus.COMPLETED || ticket.status() == SpawnHelpStatus.DENIED) {
            throw new IllegalStateException("This ticket is already closed.");
        }
        if (ticket.status() == SpawnHelpStatus.ON_HELP
                && ticket.helperDiscordId() != null
                && !ticket.helperDiscordId().equals(helperDiscordId)) {
            throw new SecurityException("Only the spawn helper who picked up this ticket can deny it now.");
        }
    }

    public String validateDenialReason(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.length() < 3) throw new IllegalArgumentException("Please provide a denial reason of at least 3 characters.");
        return value.length() <= 500 ? value : value.substring(0, 500);
    }
}
