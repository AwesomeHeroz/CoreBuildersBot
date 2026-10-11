package com.corebuilders.bot.discord;

import com.corebuilders.bot.config.NotifyConfig;
import com.corebuilders.bot.config.NotifyConfig.NotifyType;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.modals.Modal;

import java.util.Set;

/** Handles configurable, role-gated /notify <type> announcements using a modal message prompt. */
public final class NotifyDiscordListener extends ListenerAdapter {
    private static final String MODAL_PREFIX = "notify:";
    private static final String MESSAGE_INPUT_ID = "message";

    private final String guildId;
    private final NotifyConfig config;

    public NotifyDiscordListener(String guildId, NotifyConfig config) {
        this.guildId = guildId;
        this.config = config;
    }

    public Set<String> handledCommandNames() {
        return config.enabled() && !config.types().isEmpty() ? Set.of("notify") : Set.of();
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!"notify".equals(event.getName())) return;

        NotifyType type = validateRequest(event.getGuild(), event.getMember(), event.getSubcommandName());
        if (type == null) {
            replyValidationFailure(event, event.getSubcommandName());
            return;
        }

        TextInput message = TextInput.create(MESSAGE_INPUT_ID, TextInputStyle.PARAGRAPH)
                .setPlaceholder("Enter the message you want to send...")
                .setRequired(true)
                .setMinLength(1)
                .setMaxLength(1800)
                .build();

        Modal modal = Modal.create(MODAL_PREFIX + type.id(), type.name() + " Notification")
                .addComponents(Label.of("message", message))
                .build();
        event.replyModal(modal).queue();
    }

    @Override
    public void onModalInteraction(ModalInteractionEvent event) {
        if (!event.getModalId().startsWith(MODAL_PREFIX)) return;

        String typeId = event.getModalId().substring(MODAL_PREFIX.length());
        NotifyType type = validateRequest(event.getGuild(), event.getMember(), typeId);
        if (type == null) {
            event.reply(validationFailureMessage(event.getGuild(), event.getMember(), typeId))
                    .setEphemeral(true).queue();
            return;
        }

        var mapping = event.getValue(MESSAGE_INPUT_ID);
        String message = mapping == null ? "" : mapping.getAsString();
        if (message.isBlank()) {
            event.reply("Notification message cannot be empty.").setEphemeral(true).queue();
            return;
        }

        Guild guild = event.getGuild();
        TextChannel channel = guild.getTextChannelById(type.channelId());
        Role mentionRole = guild.getRoleById(type.mentionRoleId());
        if (channel == null || mentionRole == null) {
            event.reply("This notification type is misconfigured. Please contact an administrator.")
                    .setEphemeral(true).queue();
            return;
        }

        String content = mentionRole.getAsMention() + "\n" + message;
        channel.sendMessage(content)
                // User-entered text is displayed unchanged, but cannot ping arbitrary users/@everyone/other roles.
                .setAllowedMentions(Set.of(Message.MentionType.ROLE))
                .mentionRoles(type.mentionRoleId())
                .queue(
                        ignored -> event.reply("✅ Sent **" + type.name() + "** notification in " + channel.getAsMention() + ".")
                                .setEphemeral(true).queue(),
                        error -> event.reply("❌ Failed to send the notification: " + safe(error.getMessage()))
                                .setEphemeral(true).queue()
                );
    }

    private NotifyType validateRequest(Guild guild, Member member, String typeId) {
        if (!config.enabled()) return null;
        if (guild == null || !guildId.equals(guild.getId())) return null;

        NotifyType type = config.type(typeId);
        if (type == null) return null;

        boolean allowed = member != null && member.getRoles().stream()
                .anyMatch(role -> type.allowedRoleIds().contains(role.getId()));
        return allowed ? type : null;
    }

    private void replyValidationFailure(SlashCommandInteractionEvent event, String typeId) {
        event.reply(validationFailureMessage(event.getGuild(), event.getMember(), typeId))
                .setEphemeral(true).queue();
    }

    private String validationFailureMessage(Guild guild, Member member, String typeId) {
        if (!config.enabled()) return "Notifications are disabled.";
        if (guild == null || !guildId.equals(guild.getId())) {
            return "This command can only be used in the configured Core Builders server.";
        }
        NotifyType type = config.type(typeId);
        if (type == null) return "Unknown notification type.";

        boolean allowed = member != null && member.getRoles().stream()
                .anyMatch(role -> type.allowedRoleIds().contains(role.getId()));
        if (!allowed) return "You do not have permission to use `/notify " + type.id() + "`.";
        return "This notification request could not be processed.";
    }

    public void validateConfiguration(JDA jda) {
        if (!config.enabled()) return;
        Guild guild = jda.getGuildById(guildId);
        if (guild == null) throw new IllegalStateException("Configured guild is not available for notifications.");

        for (NotifyType type : config.types().values()) {
            TextChannel channel = guild.getTextChannelById(type.channelId());
            if (channel == null) {
                throw new IllegalStateException("Notify type '" + type.id() + "' channel does not exist: " + type.channelId());
            }
            Role mentionRole = guild.getRoleById(type.mentionRoleId());
            if (mentionRole == null) {
                throw new IllegalStateException("Notify type '" + type.id() + "' mention role does not exist: " + type.mentionRoleId());
            }
            for (String allowedRoleId : type.allowedRoleIds()) {
                if (guild.getRoleById(allowedRoleId) == null) {
                    throw new IllegalStateException("Notify type '" + type.id() + "' allowed role does not exist: " + allowedRoleId);
                }
            }
            Member self = guild.getSelfMember();
            if (!self.hasPermission(channel, Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND)) {
                throw new IllegalStateException("Bot cannot view/send messages in notify channel for type '" + type.id() + "'.");
            }
            if (!mentionRole.isMentionable() && !self.hasPermission(channel, Permission.MESSAGE_MENTION_EVERYONE)) {
                throw new IllegalStateException(
                        "Notify role for type '" + type.id() + "' is not mentionable and the bot lacks Mention Everyone permission in the target channel."
                );
            }
        }
    }

    private static String safe(String value) {
        if (value == null || value.isBlank()) return "Unknown Discord error.";
        return value.length() <= 500 ? value : value.substring(0, 500);
    }
}
