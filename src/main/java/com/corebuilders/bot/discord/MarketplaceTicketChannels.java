package com.corebuilders.bot.discord;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

/** Creates private buyer/seller marketplace channels with leadership access. */
public final class MarketplaceTicketChannels {
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
            Permission.MANAGE_CHANNEL,
            Permission.MANAGE_PERMISSIONS
    );

    public TextChannel createPrivate(Category category, String channelName, String topic,
                                     Member buyer, Member seller, List<Role> leadershipRoles, Member bot) {
        var action = category.createTextChannel(channelName)
                .setTopic(topic)
                .addPermissionOverride(category.getGuild().getPublicRole(), Collections.emptySet(),
                        EnumSet.of(Permission.VIEW_CHANNEL))
                .addPermissionOverride(buyer, PARTICIPANT_PERMISSIONS, Collections.emptySet())
                .addPermissionOverride(seller, PARTICIPANT_PERMISSIONS, Collections.emptySet())
                .addPermissionOverride(bot, BOT_PERMISSIONS, Collections.emptySet());
        for (Role role : leadershipRoles) {
            action.addPermissionOverride(role, PARTICIPANT_PERMISSIONS, Collections.emptySet());
        }
        return action.complete();
    }

    public void lockParticipant(TextChannel channel, Member member) {
        channel.upsertPermissionOverride(member)
                .setAllowed(EnumSet.of(Permission.VIEW_CHANNEL, Permission.MESSAGE_HISTORY))
                .setDenied(EnumSet.of(Permission.MESSAGE_SEND))
                .complete();
    }
}
