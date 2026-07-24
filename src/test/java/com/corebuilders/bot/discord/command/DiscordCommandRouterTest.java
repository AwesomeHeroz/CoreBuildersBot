package com.corebuilders.bot.discord.command;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DiscordCommandRouterTest {
    @Test
    void dispatchesRegisteredCommandCaseInsensitively() {
        DiscordCommandRouter<String, String> router = DiscordCommandRouter.<String, String>builder()
                .register("profile", value -> "handled:" + value)
                .build();

        assertEquals("handled:event", router.dispatch("PROFILE", "event"));
        assertTrue(router.supports("profile"));
        assertEquals(1, router.size());
        assertEquals(java.util.Set.of("profile"), router.commandNames());
    }

    @Test
    void rejectsDuplicateHandlers() {
        var builder = DiscordCommandRouter.<String, String>builder()
                .register("profile", value -> value);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> builder.register("PROFILE", value -> value)
        );

        assertTrue(error.getMessage().contains("Duplicate"));
    }

    @Test
    void rejectsUnknownCommands() {
        DiscordCommandRouter<String, String> router = DiscordCommandRouter.<String, String>builder().build();
        assertThrows(IllegalArgumentException.class, () -> router.dispatch("missing", "event"));
    }
}
