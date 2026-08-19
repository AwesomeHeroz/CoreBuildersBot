package com.corebuilders.bot.discord;

import com.corebuilders.bot.model.Domain.SpawnHelpStatus;
import com.corebuilders.bot.model.Models.SpawnHelpTicket;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SpawnHelpTextFormatterTest {
    @Test
    void buildsSafeDiscordChannelNameFromConfiguredPattern() {
        SpawnHelpTextFormatter formatter = new SpawnHelpTextFormatter("Help {server} {ign} {id}");
        SpawnHelpTicket ticket = ticket("2B2T Main", "Player_Name");
        String name = formatter.ticketName(ticket);

        assertTrue(name.startsWith("help-2b2t-main-player-name-"));
        assertTrue(name.matches("[a-z0-9-]+"));
        assertTrue(name.length() <= 90);
    }

    @Test
    void exposesHumanStatusLabel() {
        assertEquals("OPEN", SpawnHelpTextFormatter.statusLabel(SpawnHelpStatus.OPEN));
        assertEquals("ON HELP", SpawnHelpTextFormatter.statusLabel(SpawnHelpStatus.ON_HELP));
        assertEquals("COMPLETED", SpawnHelpTextFormatter.statusLabel(SpawnHelpStatus.COMPLETED));
        assertEquals("DENIED", SpawnHelpTextFormatter.statusLabel(SpawnHelpStatus.DENIED));
    }

    private static SpawnHelpTicket ticket(String server, String ign) {
        Instant now = Instant.now();
        return new SpawnHelpTicket(
                UUID.fromString("12345678-1234-1234-1234-123456789012"),
                "123456789012345", "Player", "2b2t", server, ign, SpawnHelpStatus.OPEN,
                null, null, null, null, null, now, now, null
        );
    }
}
