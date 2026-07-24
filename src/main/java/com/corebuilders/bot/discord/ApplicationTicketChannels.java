package com.corebuilders.bot.discord;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

/** Creates private application discussion channels atomically with their permission overrides. */
public final class ApplicationTicketChannels {
    private static final EnumSet<Permission> PARTICIPANT_PERMISSIONS = EnumSet.of(
            Permission.VIEW_CHANNEL,
            Permission.MESSAGE_SEND,
            Permission.MESSAGE_HISTORY,
            Permission.MESSAGE_ATTACH_FILES,
            Permission.MESSAGE_EMBED_LINKS
    );
    private static final EnumSet<Permission> BOT_PERMISSIONS = EnumSet.of(
            Permission.VIEW_CHANNEL,
            Permission.MESSAGE_SEND,
            Permission.MESSAGE_HISTORY,
            Permission.MESSAGE_ATTACH_FILES,
            Permission.MESSAGE_EMBED_LINKS,
            Permission.MANAGE_CHANNEL
    );

    public TextChannel createPrivate(
            Category category,
            String channelName,
            Member applicant,
            List<Role> reviewerRoles,
            Member bot
    ) {
        var action = category.createTextChannel(channelName)
                .addPermissionOverride(
                        category.getGuild().getPublicRole(),
                        Collections.emptySet(),
                        EnumSet.of(Permission.VIEW_CHANNEL)
                )
                .addPermissionOverride(applicant, PARTICIPANT_PERMISSIONS, Collections.emptySet())
                .addPermissionOverride(bot, BOT_PERMISSIONS, Collections.emptySet());

        for (Role role : reviewerRoles) {
            action.addPermissionOverride(role, PARTICIPANT_PERMISSIONS, Collections.emptySet());
        }
        return action.complete();
    }
}
