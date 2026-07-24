package com.corebuilders.bot.discord;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Resolves Discord resources. Security-sensitive roles are resolved by immutable IDs only. */
public final class DiscordResourceResolver {

    public List<Role> roles(Guild guild, Set<String> references) {
        return references.stream()
                .map(reference -> role(guild, reference))
                .flatMap(Optional::stream)
                .distinct()
                .sorted(Comparator.comparingInt(Role::getPosition).reversed())
                .toList();
    }

    public Optional<Role> role(Guild guild, String roleId) {
        if (!isSnowflake(roleId)) return Optional.empty();
        return Optional.ofNullable(guild.getRoleById(roleId));
    }

    public Role requireRole(Guild guild, String roleId, String configPath) {
        return role(guild, roleId).orElseThrow(() ->
                new IllegalStateException(configPath + " could not be resolved as a Discord role ID: " + roleId)
        );
    }

    public TextChannel requireTextChannel(Guild guild, String reference, String configPath) {
        TextChannel byId = isSnowflake(reference) ? guild.getTextChannelById(reference) : null;
        if (byId != null) return byId;
        return guild.getTextChannelsByName(reference, true).stream().findFirst().orElseThrow(() ->
                new IllegalStateException(configPath + " could not be resolved as a text-channel ID or exact name: " + reference)
        );
    }

    public Category requireCategory(Guild guild, String reference, String configPath) {
        Category byId = isSnowflake(reference) ? guild.getCategoryById(reference) : null;
        if (byId != null) return byId;
        return guild.getCategoriesByName(reference, true).stream().findFirst().orElseThrow(() ->
                new IllegalStateException(configPath + " could not be resolved as a category ID or exact name: " + reference)
        );
    }


    private static boolean isSnowflake(String value) {
        return value != null && value.matches("\\d{15,22}");
    }
}
