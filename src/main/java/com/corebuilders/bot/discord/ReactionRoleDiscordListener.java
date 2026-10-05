package com.corebuilders.bot.discord;

import com.corebuilders.bot.config.ReactionRoleConfig;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.message.react.GenericMessageReactionEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionRemoveEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/** Assigns/removes configured Discord roles based on reactions to one configured message. */
public final class ReactionRoleDiscordListener extends ListenerAdapter {
    private static final Logger log = LoggerFactory.getLogger(ReactionRoleDiscordListener.class);

    private final String guildId;
    private final ReactionRoleConfig config;
    private final DiscordResourceResolver resources = new DiscordResourceResolver();

    public ReactionRoleDiscordListener(String guildId, ReactionRoleConfig config) {
        this.guildId = Objects.requireNonNull(guildId, "guildId");
        this.config = Objects.requireNonNull(config, "config");
    }

    public void validateConfiguration(JDA jda) {
        if (!config.enabled()) {
            log.info("Discord reaction roles are disabled.");
            return;
        }

        Guild guild = jda.getGuildById(guildId);
        if (guild == null) {
            log.warn("Reaction roles are enabled but configured Discord guild {} is unavailable.", guildId);
            return;
        }

        try {
            TextChannel channel = resources.requireTextChannel(
                    guild, config.channel(), "discord.reaction-roles.channel");
            channel.retrieveMessageById(config.messageId()).complete();

            Member self = guild.getSelfMember();
            if (!self.hasPermission(Permission.MANAGE_ROLES)) {
                log.warn("Reaction roles require the bot to have the Manage Roles permission.");
            }

            for (ReactionRoleConfig.Mapping mapping : config.mappings()) {
                Role role = resources.requireRole(
                        guild, mapping.roleId(), "discord.reaction-roles.mappings.role-id");
                if (role.isManaged()) {
                    log.warn("Reaction-role target {} ({}) is integration-managed and cannot be assigned manually.",
                            role.getName(), role.getId());
                } else if (!self.canInteract(role)) {
                    log.warn("Reaction-role target {} ({}) is above/equal to the bot's highest role and cannot be managed.",
                            role.getName(), role.getId());
                }
            }
            log.info("Reaction-role configuration validated with {} mapping(s).", config.mappings().size());
        } catch (Exception error) {
            log.warn("Reaction-role configuration problem: {}", safeMessage(error));
        }
    }

    @Override
    public void onMessageReactionAdd(MessageReactionAddEvent event) {
        handle(event, true);
    }

    @Override
    public void onMessageReactionRemove(MessageReactionRemoveEvent event) {
        if (config.removeRoleOnReactionRemove()) {
            handle(event, false);
        }
    }

    private void handle(GenericMessageReactionEvent event, boolean add) {
        if (!config.enabled() || !event.isFromGuild()) return;
        Guild guild = event.getGuild();
        if (!guildId.equals(guild.getId())) return;
        if (!config.messageId().equals(event.getMessageId())) return;

        TextChannel configuredChannel;
        try {
            configuredChannel = resources.requireTextChannel(
                    guild, config.channel(), "discord.reaction-roles.channel");
        } catch (Exception error) {
            log.warn("Could not resolve reaction-role channel: {}", safeMessage(error));
            return;
        }
        if (!configuredChannel.getId().equals(event.getChannel().getId())) return;
        if (event.getUserId().equals(event.getJDA().getSelfUser().getId())) return;

        String reactionCode = event.getEmoji().getAsReactionCode();
        String roleId = config.roleIdForReaction(reactionCode);
        if (roleId == null) return;

        Role role = guild.getRoleById(roleId);
        if (role == null) {
            log.warn("Configured reaction-role target {} no longer exists.", roleId);
            return;
        }
        if (role.isManaged() || !guild.getSelfMember().canInteract(role)) {
            log.warn("Cannot manage reaction-role target {} ({}); check role hierarchy/integration ownership.",
                    role.getName(), role.getId());
            return;
        }

        Member cached = event.getMember();
        if (cached != null) {
            applyRole(guild, cached, role, add, reactionCode);
            return;
        }

        event.retrieveMember().queue(
                member -> applyRole(guild, member, role, add, reactionCode),
                error -> log.debug("Could not resolve member {} for reaction-role update: {}",
                        event.getUserId(), safeMessage(error))
        );
    }

    private void applyRole(Guild guild, Member member, Role role, boolean add, String reactionCode) {
        if (member.getUser().isBot()) return;

        if (add && member.getRoles().contains(role)) return;
        if (!add && !member.getRoles().contains(role)) return;

        var action = add
                ? guild.addRoleToMember(member, role)
                : guild.removeRoleFromMember(member, role);

        action.reason("Reaction role " + (add ? "added" : "removed") + " via " + reactionCode)
                .queue(
                        ignored -> log.debug("{} role {} ({}) {} member {} ({}) from reaction {}.",
                                add ? "Added" : "Removed",
                                role.getName(), role.getId(), add ? "to" : "from",
                                member.getEffectiveName(), member.getId(), reactionCode),
                        error -> log.warn("Failed to {} role {} ({}) {} member {} ({}): {}",
                                add ? "add" : "remove",
                                role.getName(), role.getId(), add ? "to" : "from",
                                member.getEffectiveName(), member.getId(), safeMessage(error))
                );
    }

    private static String safeMessage(Throwable error) {
        String message = error == null ? null : error.getMessage();
        return message == null || message.isBlank() ? String.valueOf(error) : message;
    }
}
