package com.corebuilders.bot.config;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Configuration for assigning Discord roles from reactions on one configured message. */
public final class ReactionRoleConfig {
    private static final Pattern SNOWFLAKE = Pattern.compile("\\d{15,22}");
    private static final Pattern CUSTOM_MENTION = Pattern.compile("<a?:[^:>]+:(\\d{15,22})>");
    private static final Pattern CUSTOM_REACTION_CODE = Pattern.compile("[^:<>\\s]+:(\\d{15,22})");

    public record Mapping(String reaction, String reactionKey, String roleId) {}

    private final boolean enabled;
    private final String channel;
    private final String messageId;
    private final boolean removeRoleOnReactionRemove;
    private final List<Mapping> mappings;
    private final Map<String, String> roleIdByReactionKey;

    public ReactionRoleConfig(FileConfiguration config) {
        this.enabled = config.getBoolean("discord.reaction-roles.enabled", false);
        this.channel = clean(config.getString("discord.reaction-roles.channel", ""));
        this.messageId = clean(config.getString("discord.reaction-roles.message-id", ""));
        this.removeRoleOnReactionRemove = config.getBoolean(
                "discord.reaction-roles.remove-role-on-reaction-remove", true);
        this.mappings = enabled ? List.copyOf(parseMappings(config)) : List.of();

        LinkedHashMap<String, String> lookup = new LinkedHashMap<>();
        for (Mapping mapping : mappings) {
            lookup.put(mapping.reactionKey(), mapping.roleId());
        }
        this.roleIdByReactionKey = Map.copyOf(lookup);
        validate();
    }

    private void validate() {
        if (!enabled) return;
        if (channel.isBlank()) {
            throw new IllegalStateException("discord.reaction-roles.channel is required when reaction roles are enabled.");
        }
        if (!isSnowflake(messageId)) {
            throw new IllegalStateException("discord.reaction-roles.message-id must be a Discord message ID when reaction roles are enabled.");
        }
        if (mappings.isEmpty()) {
            throw new IllegalStateException("discord.reaction-roles.mappings must contain at least one reaction-to-role mapping.");
        }
    }

    private static List<Mapping> parseMappings(FileConfiguration config) {
        List<Map<?, ?>> rawMappings = config.getMapList("discord.reaction-roles.mappings");
        List<Mapping> result = new ArrayList<>();
        Map<String, String> seen = new LinkedHashMap<>();

        for (int i = 0; i < rawMappings.size(); i++) {
            Map<?, ?> raw = rawMappings.get(i);
            String reaction = clean(value(raw, "reaction"));
            String roleId = clean(value(raw, "role-id"));
            String path = "discord.reaction-roles.mappings[" + i + "]";

            if (reaction.isBlank()) {
                throw new IllegalStateException(path + ".reaction is required.");
            }
            if (!isSnowflake(roleId)) {
                throw new IllegalStateException(path + ".role-id must be a Discord role ID; invalid value: " + roleId);
            }

            String reactionKey = normalizeReaction(reaction);
            String previous = seen.putIfAbsent(reactionKey, roleId);
            if (previous != null) {
                throw new IllegalStateException("Duplicate reaction mapping for '" + reaction + "' in discord.reaction-roles.mappings.");
            }
            result.add(new Mapping(reaction, reactionKey, roleId));
        }
        return result;
    }

    /**
     * Normalizes unicode and custom Discord emoji references.
     * Supported custom forms: emoji ID, name:id, <:name:id>, and <a:name:id>.
     */
    public static String normalizeReaction(String reaction) {
        String cleaned = clean(reaction);
        if (cleaned.isBlank()) return "";

        if (isSnowflake(cleaned)) {
            return "custom:" + cleaned;
        }

        Matcher mention = CUSTOM_MENTION.matcher(cleaned);
        if (mention.matches()) {
            return "custom:" + mention.group(1);
        }

        Matcher reactionCode = CUSTOM_REACTION_CODE.matcher(cleaned);
        if (reactionCode.matches()) {
            return "custom:" + reactionCode.group(1);
        }

        return "unicode:" + cleaned;
    }

    private static String value(Map<?, ?> map, String key) {
        Object value = map.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private static boolean isSnowflake(String value) {
        return value != null && SNOWFLAKE.matcher(value).matches();
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    public boolean enabled() { return enabled; }
    public String channel() { return channel; }
    public String messageId() { return messageId; }
    public boolean removeRoleOnReactionRemove() { return removeRoleOnReactionRemove; }
    public List<Mapping> mappings() { return mappings; }
    public String roleIdForReaction(String reactionCode) {
        return roleIdByReactionKey.get(normalizeReaction(reactionCode));
    }
}
