package com.corebuilders.bot.discord.command;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * Small command registry that keeps dispatch policy out of Discord listeners.
 * The router is intentionally framework-agnostic and can be unit tested without JDA.
 */
public final class DiscordCommandRouter<E, R> {
    private final Map<String, Function<E, R>> handlers;

    private DiscordCommandRouter(Map<String, Function<E, R>> handlers) {
        this.handlers = Map.copyOf(handlers);
    }

    public R dispatch(String commandName, E event) {
        Function<E, R> handler = handlers.get(normalize(commandName));
        if (handler == null) {
            throw new IllegalArgumentException("Unknown Discord command: /" + commandName);
        }
        return handler.apply(event);
    }

    public boolean supports(String commandName) {
        return handlers.containsKey(normalize(commandName));
    }

    public int size() {
        return handlers.size();
    }

    public Set<String> commandNames() {
        return handlers.keySet();
    }

    public static <E, R> Builder<E, R> builder() {
        return new Builder<>();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
    }

    public static final class Builder<E, R> {
        private final Map<String, Function<E, R>> handlers = new LinkedHashMap<>();

        public Builder<E, R> register(String commandName, Function<E, R> handler) {
            String key = normalize(commandName);
            if (key.isBlank()) {
                throw new IllegalArgumentException("Command name cannot be blank.");
            }
            Objects.requireNonNull(handler, "handler");
            if (handlers.putIfAbsent(key, handler) != null) {
                throw new IllegalArgumentException("Duplicate Discord command handler: /" + key);
            }
            return this;
        }

        public DiscordCommandRouter<E, R> build() {
            return new DiscordCommandRouter<>(handlers);
        }
    }
}
