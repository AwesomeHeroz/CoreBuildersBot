package com.corebuilders.bot.config;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Configuration for the Discord spawn-help request and ticket workflow. */
public final class SpawnHelpConfig {
    public record ServerOption(String id, String name, String description) {}

    private final boolean enabled;
    private final String panelChannel;
    private final String panelMessageId;
    private final String panelTitle;
    private final String panelDescription;
    private final String panelButtonLabel;
    private final String ticketCategory;
    private final String ticketNamePattern;
    private final Set<String> helperRoleIds;
    private final List<ServerOption> servers;
    private final String openMessage;
    private final String pickedMessage;
    private final String completedMessage;
    private final String deniedMessage;

    public SpawnHelpConfig(FileConfiguration config) {
        this.enabled = config.getBoolean("discord.spawn-help.enabled", false);
        this.panelChannel = clean(config.getString("discord.spawn-help.panel.channel", "spawn-help"));
        this.panelMessageId = clean(config.getString("discord.spawn-help.panel.message-id", ""));
        this.panelTitle = defaultIfBlank(config.getString("discord.spawn-help.panel.title", "Need Help at Spawn?"), "Need Help at Spawn?");
        this.panelDescription = defaultIfBlank(
                config.getString("discord.spawn-help.panel.description", "Click the button below to request help from a Core Builders spawn helper."),
                "Click the button below to request help from a Core Builders spawn helper."
        );
        this.panelButtonLabel = defaultIfBlank(config.getString("discord.spawn-help.panel.button-label", "Request Spawn Help"), "Request Spawn Help");
        this.ticketCategory = clean(config.getString("discord.spawn-help.tickets.category", "spawn-help-tickets"));
        this.ticketNamePattern = defaultIfBlank(
                config.getString("discord.spawn-help.tickets.name-pattern", "help-{server}-{ign}-{id}"),
                "help-{server}-{ign}-{id}"
        );
        this.helperRoleIds = enabled
                ? parseSnowflakeSet(config.getStringList("discord.spawn-help.helper-role-ids"), "discord.spawn-help.helper-role-ids")
                : Set.of();
        this.servers = List.copyOf(parseServers(config));
        this.openMessage = defaultIfBlank(
                config.getString("discord.spawn-help.messages.open", "Your spawn-help ticket for {server} is now OPEN. A helper will pick it up when available."),
                "Your spawn-help ticket for {server} is now OPEN. A helper will pick it up when available."
        );
        this.pickedMessage = defaultIfBlank(
                config.getString("discord.spawn-help.messages.picked", "Your spawn-help ticket for {server} is now ON HELP. {helper} picked it up."),
                "Your spawn-help ticket for {server} is now ON HELP. {helper} picked it up."
        );
        this.completedMessage = defaultIfBlank(
                config.getString("discord.spawn-help.messages.completed", "Your spawn-help request for {server} has been COMPLETED. Thanks for using Core Builders spawn help."),
                "Your spawn-help request for {server} has been COMPLETED. Thanks for using Core Builders spawn help."
        );
        this.deniedMessage = defaultIfBlank(
                config.getString("discord.spawn-help.messages.denied", "Your spawn-help request for {server} has been DENIED. Reason: {reason}"),
                "Your spawn-help request for {server} has been DENIED. Reason: {reason}"
        );
        validate();
    }

    private void validate() {
        if (!enabled) return;
        if (panelChannel.isBlank()) {
            throw new IllegalStateException("discord.spawn-help.panel.channel is required when spawn help is enabled.");
        }
        if (ticketCategory.isBlank()) {
            throw new IllegalStateException("discord.spawn-help.tickets.category is required when spawn help is enabled.");
        }
        if (helperRoleIds.isEmpty()) {
            throw new IllegalStateException("discord.spawn-help.helper-role-ids must contain at least one Discord role ID.");
        }
        if (servers.isEmpty()) {
            throw new IllegalStateException("discord.spawn-help.servers must contain at least one server.");
        }
        if (servers.size() > 25) {
            throw new IllegalStateException("discord.spawn-help.servers supports at most 25 servers because Discord select menus support at most 25 options.");
        }
        Set<String> ids = new LinkedHashSet<>();
        for (ServerOption server : servers) {
            if (!server.id().matches("[a-z0-9_-]{1,40}")) {
                throw new IllegalStateException("Invalid spawn-help server id '" + server.id() + "'. Use 1-40 lowercase letters, numbers, underscores, or hyphens.");
            }
            if (!ids.add(server.id())) {
                throw new IllegalStateException("Duplicate spawn-help server id: " + server.id());
            }
            if (server.name().isBlank() || server.name().length() > 100) {
                throw new IllegalStateException("Spawn-help server name must be 1-100 characters for " + server.id() + ".");
            }
            if (server.description().length() > 100) {
                throw new IllegalStateException("Spawn-help server description must be at most 100 characters for " + server.id() + ".");
            }
        }
    }

    private static List<ServerOption> parseServers(FileConfiguration config) {
        List<Map<?, ?>> rawServers = config.getMapList("discord.spawn-help.servers");
        List<ServerOption> result = new ArrayList<>();
        for (Map<?, ?> raw : rawServers) {
            String name = clean(value(raw, "name", ""));
            String id = clean(value(raw, "id", name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]+", "-")));
            String description = clean(value(raw, "description", ""));
            if (!id.isBlank() || !name.isBlank()) {
                result.add(new ServerOption(id, name, description));
            }
        }
        return result;
    }

    private static Set<String> parseSnowflakeSet(List<String> configured, String path) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (configured != null) {
            for (String item : configured) {
                String value = clean(item);
                if (value.isBlank()) continue;
                if (!value.matches("\\d{15,22}")) {
                    throw new IllegalStateException(path + " must contain Discord role IDs only; invalid value: " + value);
                }
                result.add(value);
            }
        }
        return Set.copyOf(result);
    }

    private static String value(Map<?, ?> map, String key, String fallback) {
        Object value = map.get(key);
        return value == null ? fallback : String.valueOf(value);
    }

    private static String clean(String value) { return value == null ? "" : value.trim(); }

    private static String defaultIfBlank(String value, String fallback) {
        String cleaned = clean(value);
        return cleaned.isBlank() ? fallback : cleaned;
    }

    public boolean enabled() { return enabled; }
    public String panelChannel() { return panelChannel; }
    public String panelMessageId() { return panelMessageId; }
    public String panelTitle() { return panelTitle; }
    public String panelDescription() { return panelDescription; }
    public String panelButtonLabel() { return panelButtonLabel; }
    public String ticketCategory() { return ticketCategory; }
    public String ticketNamePattern() { return ticketNamePattern; }
    public Set<String> helperRoleIds() { return helperRoleIds; }
    public List<ServerOption> servers() { return servers; }
    public String openMessage() { return openMessage; }
    public String pickedMessage() { return pickedMessage; }
    public String completedMessage() { return completedMessage; }
    public String deniedMessage() { return deniedMessage; }
}
