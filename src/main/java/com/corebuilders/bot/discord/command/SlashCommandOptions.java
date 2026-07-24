package com.corebuilders.bot.discord.command;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;

import java.util.UUID;

/** Small, reusable parser for JDA slash-command options. */
public final class SlashCommandOptions {
    private SlashCommandOptions() {}

    public static String requiredSubcommand(SlashCommandInteractionEvent event) {
        String name = event.getSubcommandName();
        if (name == null) {
            throw new IllegalArgumentException("A subcommand is required.");
        }
        return name;
    }

    public static String requiredString(SlashCommandInteractionEvent event, String name) {
        OptionMapping option = event.getOption(name);
        if (option == null) {
            throw new IllegalArgumentException("Missing required option: " + name);
        }
        return option.getAsString();
    }

    public static String string(SlashCommandInteractionEvent event, String name, String defaultValue) {
        OptionMapping option = event.getOption(name);
        return option == null ? defaultValue : option.getAsString();
    }

    public static int boundedInt(
            SlashCommandInteractionEvent event,
            String name,
            int defaultValue,
            int minimum,
            int maximum
    ) {
        OptionMapping option = event.getOption(name);
        long value = option == null ? defaultValue : option.getAsLong();
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(name + " must be between " + minimum + " and " + maximum + ".");
        }
        return (int) value;
    }

    public static long requiredLong(SlashCommandInteractionEvent event, String name) {
        OptionMapping option = event.getOption(name);
        if (option == null) {
            throw new IllegalArgumentException("Missing required option: " + name);
        }
        return option.getAsLong();
    }

    public static Long nullableLong(SlashCommandInteractionEvent event, String name) {
        OptionMapping option = event.getOption(name);
        return option == null ? null : option.getAsLong();
    }

    public static User requiredUser(SlashCommandInteractionEvent event, String name) {
        User value = user(event, name, null);
        if (value == null) {
            throw new IllegalArgumentException("Missing required user: " + name);
        }
        return value;
    }

    public static User user(SlashCommandInteractionEvent event, String name, User defaultValue) {
        OptionMapping option = event.getOption(name);
        return option == null ? defaultValue : option.getAsUser();
    }

    public static UUID uuid(String value, String label) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("Invalid " + label + ": " + value);
        }
    }
}
