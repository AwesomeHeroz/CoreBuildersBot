package com.corebuilders.bot.config;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Configuration for role-gated /notify notification types. */
public final class NotifyConfig {
    private final boolean enabled;
    private final Map<String, NotifyType> types;

    public NotifyConfig(FileConfiguration config) {
        this.enabled = config.getBoolean("discord.notifications.enabled", false);
        if (!this.enabled) {
            this.types = Map.of();
            return;
        }
        Map<String, NotifyType> parsed = new LinkedHashMap<>();
        List<Map<?, ?>> entries = config.getMapList("discord.notifications.types");
        if (entries.size() > 25) {
            throw new IllegalStateException("discord.notifications.types supports at most 25 entries because Discord slash-command choices are limited to 25.");
        }
        for (Map<?, ?> entry : entries) {
            String id = string(entry.get("id")).toLowerCase(Locale.ROOT);
            if (id.isBlank()) throw new IllegalStateException("discord.notifications.types[].id is required");
            if (!id.matches("[a-z0-9_-]{1,32}")) {
                throw new IllegalStateException("Invalid notification type id '" + id + "'. Use a-z, 0-9, _ or - (max 32 chars).");
            }
            if (parsed.containsKey(id)) throw new IllegalStateException("Duplicate notification type id: " + id);

            String name = string(entry.get("name"));
            if (name.isBlank()) name = id;
            if (name.length() > 60) throw new IllegalStateException("Notify type name is too long for " + id + " (max 60 characters).");
            String channelId = requireSnowflake(string(entry.get("channel-id")), "channel-id for notify type " + id);
            String mentionRoleId = requireSnowflake(string(entry.get("mention-role-id")), "mention-role-id for notify type " + id);
            Set<String> allowedRoleIds = snowflakeSet(entry.get("allowed-role-ids"), "allowed-role-ids for notify type " + id);
            if (allowedRoleIds.isEmpty()) {
                throw new IllegalStateException("At least one allowed-role-id is required for notify type " + id);
            }
            parsed.put(id, new NotifyType(id, name, channelId, mentionRoleId, allowedRoleIds));
        }
        this.types = Map.copyOf(parsed);
    }

    public boolean enabled() { return enabled; }
    public Map<String, NotifyType> types() { return types; }
    public NotifyType type(String id) {
        if (id == null) return null;
        return types.get(id.toLowerCase(Locale.ROOT));
    }

    private static String string(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static Set<String> snowflakeSet(Object raw, String path) {
        List<?> values;
        if (raw instanceof List<?> list) values = list;
        else if (raw == null) values = List.of();
        else values = List.of(raw);
        Set<String> result = new LinkedHashSet<>();
        for (Object value : values) {
            String id = requireSnowflake(string(value), path);
            result.add(id);
        }
        return Set.copyOf(result);
    }

    private static String requireSnowflake(String value, String path) {
        if (!value.matches("\\d{15,22}")) {
            throw new IllegalStateException(path + " must be a Discord ID; invalid value: " + value);
        }
        return value;
    }

    public record NotifyType(
            String id,
            String name,
            String channelId,
            String mentionRoleId,
            Set<String> allowedRoleIds
    ) {}
}
