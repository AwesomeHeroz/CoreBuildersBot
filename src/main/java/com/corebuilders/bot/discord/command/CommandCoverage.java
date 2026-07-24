package com.corebuilders.bot.discord.command;

import java.util.Set;
import java.util.TreeSet;

/** Ensures Discord command registration and interaction handling cannot drift apart. */
public final class CommandCoverage {
    private CommandCoverage() {}

    public static void requireExact(Set<String> registered, Set<String> handled) {
        Set<String> missingHandlers = new TreeSet<>(registered);
        missingHandlers.removeAll(handled);
        Set<String> unregisteredHandlers = new TreeSet<>(handled);
        unregisteredHandlers.removeAll(registered);

        if (!missingHandlers.isEmpty() || !unregisteredHandlers.isEmpty()) {
            throw new IllegalStateException(
                    "Discord command wiring mismatch. Missing handlers=" + missingHandlers
                            + ", handlers without registration=" + unregisteredHandlers
            );
        }
    }
}
