package com.corebuilders.bot.service;

import com.corebuilders.bot.model.Domain.SpawnHelpStatus;
import com.corebuilders.bot.model.Models.SpawnHelpTicket;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SpawnHelpTicketPolicyTest {
    private final SpawnHelpTicketPolicy policy = new SpawnHelpTicketPolicy();

    @Test
    void validatesMinecraftNames() {
        assertEquals("Player_123", policy.validateInGameName(" Player_123 "));
        assertThrows(IllegalArgumentException.class, () -> policy.validateInGameName("ab"));
        assertThrows(IllegalArgumentException.class, () -> policy.validateInGameName("player-name"));
        assertThrows(IllegalArgumentException.class, () -> policy.validateInGameName("this_name_is_way_too_long"));
    }

    @Test
    void onlyAssignedHelperCanCompleteOnHelpTicket() {
        SpawnHelpTicket ticket = ticket(SpawnHelpStatus.ON_HELP, "222222222222222");
        assertDoesNotThrow(() -> policy.requireCompletableBy(ticket, "222222222222222"));
        assertThrows(SecurityException.class, () -> policy.requireCompletableBy(ticket, "333333333333333"));
        assertThrows(IllegalStateException.class,
                () -> policy.requireCompletableBy(ticket(SpawnHelpStatus.OPEN, null), "222222222222222"));
    }

    @Test
    void denialReasonMustBeMeaningfulAndIsBounded() {
        assertThrows(IllegalArgumentException.class, () -> policy.validateDenialReason("no"));
        assertEquals("Not at spawn", policy.validateDenialReason(" Not at spawn "));
        assertEquals(500, policy.validateDenialReason("x".repeat(700)).length());
    }

    private static SpawnHelpTicket ticket(SpawnHelpStatus status, String helper) {
        Instant now = Instant.now();
        return new SpawnHelpTicket(
                UUID.randomUUID(), "123456789012345", "Player", "2b2t", "2b2t", "Steve",
                status, helper, helper == null ? null : "Helper", null, "444444444444444", "555555555555555",
                now, now, null
        );
    }
}
