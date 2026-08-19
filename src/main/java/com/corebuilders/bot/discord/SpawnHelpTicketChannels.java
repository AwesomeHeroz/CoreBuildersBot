package com.corebuilders.bot.discord;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

/** Creates and closes private spawn-help ticket channels. */
public final class SpawnHelpTicketChannels {
    private static final EnumSet<Permission> PARTICIPANT_PERMISSIONS = EnumSet.of(
            Permission.VIEW_CHANNEL,
            Permission.MESSAGE_SEND,
            Permission.MESSAGE_HISTORY,
            Permission.MESSAGE_ATTACH_FILES,
            Permission.MESSAGE_EMBED_LINKS
    );
    private static final EnumSet<Permission> CLOSED_PERMISSIONS = EnumSet.of(
            Permission.VIEW_CHANNEL,
            Permission.MESSAGE_HISTORY
    );
    private static final EnumSet<Permission> BOT_PERMISSIONS = EnumSet.of(
            Permission.VIEW_CHANNEL,
            Permission.MESSAGE_SEND,
            Permission.MESSAGE_HISTORY,
            Permission.MESSAGE_ATTACH_FILES,
            Permission.MESSAGE_EMBED_LINKS,
            Permission.MANAGE_CHANNEL,
            Permission.MANAGE_PERMISSIONS
    );

    public TextChannel createPrivate(
            Category category,
            String channelName,
            String topic,
            Member requester,
            List<Role> helperRoles,
            Member bot
    ) {
        var action = category.createTextChannel(channelName)
                .setTopic(topic)
                .addPermissionOverride(
                        category.getGuild().getPublicRole(),
                        Collections.emptySet(),
                        EnumSet.of(Permission.VIEW_CHANNEL)
                )
                .addPermissionOverride(requester, PARTICIPANT_PERMISSIONS, Collections.emptySet())
                .addPermissionOverride(bot, BOT_PERMISSIONS, Collections.emptySet());
        for (Role role : helperRoles) {
            action.addPermissionOverride(role, PARTICIPANT_PERMISSIONS, Collections.emptySet());
        }
        return action.complete();
    }

    public void close(TextChannel channel, Member requester, List<Role> helperRoles) {
        if (requester != null) {
            channel.upsertPermissionOverride(requester)
                    .setAllowed(CLOSED_PERMISSIONS)
                    .setDenied(EnumSet.of(Permission.MESSAGE_SEND))
                    .complete();
        }
        for (Role role : helperRoles) {
            channel.upsertPermissionOverride(role)
                    .setAllowed(CLOSED_PERMISSIONS)
                    .setDenied(EnumSet.of(Permission.MESSAGE_SEND))
                    .complete();
        }
    }
}
