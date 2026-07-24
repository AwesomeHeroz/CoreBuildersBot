package com.corebuilders.bot.discord.command;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CommandCoverageTest {
    @Test
    void acceptsMatchingCommandSets() {
        assertDoesNotThrow(() -> CommandCoverage.requireExact(
                Set.of("core", "apply"),
                Set.of("apply", "core")
        ));
    }

    @Test
    void reportsMissingAndUnregisteredHandlers() {
        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> CommandCoverage.requireExact(Set.of("core", "apply"), Set.of("core", "profile"))
        );

        assertTrue(error.getMessage().contains("apply"));
        assertTrue(error.getMessage().contains("profile"));
    }
}
