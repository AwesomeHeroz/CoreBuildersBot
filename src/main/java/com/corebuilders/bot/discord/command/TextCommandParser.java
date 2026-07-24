package com.corebuilders.bot.discord.command;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Parses optional Discord prefix commands without depending on JDA. */
public final class TextCommandParser {
    private final String prefix;

    public TextCommandParser(String prefix) {
        this.prefix = normalizePrefix(prefix);
    }

    public ParsedCommand parse(String rawMessage) {
        if (!matchesPrefix(rawMessage)) {
            return null;
        }

        String remainder = rawMessage.substring(prefix.length()).trim();
        if (remainder.isEmpty()) {
            return new ParsedCommand("help", List.of());
        }

        String[] tokens = remainder.split("\\s+");
        String command = tokens[0].toLowerCase(Locale.ROOT);
        List<String> arguments = tokens.length == 1
                ? List.of()
                : List.copyOf(Arrays.asList(tokens).subList(1, tokens.length));
        return new ParsedCommand(command, arguments);
    }

    public boolean matchesPrefix(String rawMessage) {
        if (rawMessage == null || rawMessage.length() < prefix.length()) {
            return false;
        }
        if (!rawMessage.regionMatches(true, 0, prefix, 0, prefix.length())) {
            return false;
        }
        return rawMessage.length() == prefix.length()
                || Character.isWhitespace(rawMessage.charAt(prefix.length()));
    }

    public String prefix() {
        return prefix;
    }

    private static String normalizePrefix(String value) {
        return value == null || value.isBlank() ? "!core" : value.trim();
    }

    public record ParsedCommand(String command, List<String> arguments) {
        public ParsedCommand {
            command = command == null || command.isBlank()
                    ? "help"
                    : command.toLowerCase(Locale.ROOT);
            arguments = arguments == null ? List.of() : List.copyOf(arguments);
        }

        public String argument(int index, String defaultValue) {
            return index >= 0 && index < arguments.size() ? arguments.get(index) : defaultValue;
        }
    }
}
