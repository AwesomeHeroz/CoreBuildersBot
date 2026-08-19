package com.corebuilders.bot.discord;

import com.corebuilders.bot.config.SpawnHelpConfig;
import com.corebuilders.bot.model.Domain.SpawnHelpStatus;
import com.corebuilders.bot.model.Models.SpawnHelpTicket;
import com.corebuilders.bot.service.SpawnHelpService;
import com.corebuilders.bot.util.ErrorMessages;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
import net.dv8tion.jda.api.modals.Modal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/** Discord adapter for the spawn-help request flow and private ticket lifecycle. */
public final class SpawnHelpDiscordListener extends ListenerAdapter implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(SpawnHelpDiscordListener.class);
    private static final String SERVER_SELECT_ID = "spawnhelp:server";
    private static final String IGN_MODAL_PREFIX = "spawnhelp:ign:";
    private static final String TICKET_BUTTON_PREFIX = "spawnhelp:ticket:";
    private static final String DENY_MODAL_PREFIX = "spawnhelp:deny:";
    private static final String TOPIC_PREFIX = "corebuilders-spawn-help:";

    private final String guildId;
    private final SpawnHelpConfig config;
    private final SpawnHelpService service;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final DiscordResourceResolver resources = new DiscordResourceResolver();
    private final SpawnHelpTicketChannels ticketChannels = new SpawnHelpTicketChannels();
    private final SpawnHelpTextFormatter textFormatter;

    public SpawnHelpDiscordListener(String guildId, SpawnHelpConfig config, SpawnHelpService service) {
        this.guildId = Objects.requireNonNull(guildId, "guildId");
        this.config = Objects.requireNonNull(config, "config");
        this.service = Objects.requireNonNull(service, "service");
        this.textFormatter = new SpawnHelpTextFormatter(config.ticketNamePattern());
    }

    public void validateConfiguration(JDA jda) {
        if (!config.enabled()) {
            log.info("Discord spawn-help workflow is disabled.");
            return;
        }
        Guild guild = jda.getGuildById(guildId);
        if (guild == null) {
            log.warn("Spawn help is enabled but the configured Discord guild {} is unavailable.", guildId);
            return;
        }
        List<String> problems = new ArrayList<>();
        tryResolve(problems, () -> resources.requireTextChannel(
                guild, config.panelChannel(), "discord.spawn-help.panel.channel"));
        tryResolve(problems, () -> resources.requireCategory(
                guild, config.ticketCategory(), "discord.spawn-help.tickets.category"));
        for (String roleId : config.helperRoleIds()) {
            tryResolve(problems, () -> resources.requireRole(
                    guild, roleId, "discord.spawn-help.helper-role-ids"));
        }
        if (problems.isEmpty()) {
            log.info("Spawn-help Discord configuration validated for {} server option(s).", config.servers().size());
        } else {
            problems.forEach(problem -> log.warn("Spawn-help configuration problem: {}", problem));
        }
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String id = event.getComponentId();
        if (SpawnHelpPanelService.REQUEST_BUTTON_ID.equals(id)) {
            handleRequestButton(event);
            return;
        }
        if (!id.startsWith(TICKET_BUTTON_PREFIX)) return;
        if (!validGuild(event.isFromGuild() ? event.getGuild() : null)) {
            event.reply("This spawn-help action can only be used in the configured Core Builders Discord server.")
                    .setEphemeral(true).queue();
            return;
        }
        String value = id.substring(TICKET_BUTTON_PREFIX.length());
        int split = value.indexOf(':');
        if (split < 1 || split >= value.length() - 1) {
            event.reply("Invalid spawn-help ticket action.").setEphemeral(true).queue();
            return;
        }
        String action = value.substring(0, split);
        UUID ticketId;
        try {
            ticketId = UUID.fromString(value.substring(split + 1));
        } catch (IllegalArgumentException error) {
            event.reply("Invalid spawn-help ticket ID.").setEphemeral(true).queue();
            return;
        }

        try {
            requireHelper(event.getMember());
            if ("deny".equals(action)) {
                showDenyModal(event, ticketId);
                return;
            }
        } catch (Exception error) {
            event.reply("❌ " + ErrorMessages.safe(error)).setEphemeral(true).queue();
            return;
        }

        event.deferReply(true).queue(hook -> executor.submit(() -> {
            try {
                SpawnHelpTicket updated = switch (action) {
                    case "pickup" -> service.claim(ticketId, event.getUser().getId(), event.getUser().getName());
                    case "complete" -> service.complete(ticketId, event.getUser().getId());
                    default -> throw new IllegalArgumentException("Unknown spawn-help ticket action.");
                };
                TextChannel channel = ticketChannel(event.getGuild(), updated).orElse(null);
                refreshControlMessageQuietly(channel, updated);
                notifyStateChange(event.getJDA(), channel, updated);
                if (terminal(updated.status())) closeTicketChannelQuietly(channel, updated);
                hook.editOriginal(successMessage(updated)).queue();
            } catch (Exception error) {
                hook.editOriginal("❌ " + ErrorMessages.safe(error)).queue();
            }
        }));
    }

    @Override
    public void onStringSelectInteraction(StringSelectInteractionEvent event) {
        if (!SERVER_SELECT_ID.equals(event.getComponentId())) return;
        if (!validGuild(event.isFromGuild() ? event.getGuild() : null)) {
            event.reply("This spawn-help action can only be used in the configured Core Builders Discord server.")
                    .setEphemeral(true).queue();
            return;
        }
        if (event.getSelectedOptions().size() != 1) {
            event.reply("Please select exactly one server.").setEphemeral(true).queue();
            return;
        }
        String serverId = event.getSelectedOptions().get(0).getValue();
        if (config.servers().stream().noneMatch(server -> server.id().equals(serverId))) {
            event.reply("That server is no longer available for spawn help.").setEphemeral(true).queue();
            return;
        }
        TextInput ign = TextInput.create("ign", TextInputStyle.SHORT)
                .setPlaceholder("Your Minecraft in-game name")
                .setRequiredRange(3, 16)
                .build();
        Modal modal = Modal.create(IGN_MODAL_PREFIX + serverId, "Spawn Help Request")
                .addComponents(Label.of("In-game name", ign))
                .build();
        event.replyModal(modal).queue();
    }

    @Override
    public void onModalInteraction(ModalInteractionEvent event) {
        String id = event.getModalId();
        if (!id.startsWith(IGN_MODAL_PREFIX) && !id.startsWith(DENY_MODAL_PREFIX)) return;
        if (!validGuild(event.isFromGuild() ? event.getGuild() : null)) {
            event.reply("This spawn-help action can only be used in the configured Core Builders Discord server.")
                    .setEphemeral(true).queue();
            return;
        }
        if (id.startsWith(IGN_MODAL_PREFIX)) {
            handleIgnModal(event, id.substring(IGN_MODAL_PREFIX.length()));
        } else {
            handleDenyModal(event, id.substring(DENY_MODAL_PREFIX.length()));
        }
    }

    private void handleRequestButton(ButtonInteractionEvent event) {
        if (!config.enabled()) {
            event.reply("Spawn-help requests are currently disabled.").setEphemeral(true).queue();
            return;
        }
        if (!validGuild(event.isFromGuild() ? event.getGuild() : null)) {
            event.reply("This request button can only be used in the configured Core Builders Discord server.")
                    .setEphemeral(true).queue();
            return;
        }
        event.deferReply(true).queue(hook -> executor.submit(() -> {
            try {
                Optional<SpawnHelpTicket> active = service.activeForUser(event.getUser().getId());
                if (active.isPresent()) {
                    SpawnHelpTicket ticket = active.get();
                    String location = ticket.channelId() == null ? "" : " <#" + ticket.channelId() + ">";
                    hook.editOriginal("You already have an active spawn-help ticket (" +
                            SpawnHelpTextFormatter.statusLabel(ticket.status()) + ")." + location).queue();
                    return;
                }
                StringSelectMenu.Builder menu = StringSelectMenu.create(SERVER_SELECT_ID)
                        .setPlaceholder("Choose the server where you need help")
                        .setRequiredRange(1, 1);
                for (SpawnHelpConfig.ServerOption server : config.servers()) {
                    if (server.description().isBlank()) menu.addOption(server.name(), server.id());
                    else menu.addOption(server.name(), server.id(), server.description());
                }
                hook.editOriginal("Which server do you need help on?")
                        .setComponents(ActionRow.of(menu.build()))
                        .queue();
            } catch (Exception error) {
                hook.editOriginal("❌ " + ErrorMessages.safe(error)).queue();
            }
        }));
    }

    private void handleIgnModal(ModalInteractionEvent event, String serverId) {
        String ign = Optional.ofNullable(event.getValue("ign"))
                .map(ModalMapping::getAsOptionalString)
                .orElse("");
        event.deferReply(true).queue(hook -> executor.submit(() -> {
            SpawnHelpTicket created = null;
            TextChannel channel = null;
            boolean attached = false;
            try {
                Guild guild = event.getGuild();
                created = service.create(event.getUser().getId(), event.getUser().getName(), serverId, ign);
                Category category = resources.requireCategory(
                        guild, config.ticketCategory(), "discord.spawn-help.tickets.category");
                List<Role> helperRoles = helperRoles(guild);
                Member requester = requireMember(guild, event.getUser().getId(), "requester");
                String topic = TOPIC_PREFIX + created.id();
                channel = ticketChannels.createPrivate(
                        category,
                        textFormatter.ticketName(created),
                        topic,
                        requester,
                        helperRoles,
                        guild.getSelfMember()
                );
                String helperMentions = helperRoles.stream().map(Role::getAsMention).collect(Collectors.joining(" "));
                channel.sendMessage(helperMentions + " New spawn-help request from " + requester.getAsMention() + ".")
                        .complete();
                var control = channel.sendMessageEmbeds(embed(created))
                        .setComponents(components(created))
                        .complete();
                created = service.attachChannel(created.id(), channel.getId(), control.getId());
                attached = true;
                notifyStateChange(event.getJDA(), channel, created);
                hook.editOriginal("✅ Spawn-help ticket created: " + channel.getAsMention()).queue();
            } catch (Exception error) {
                if (!attached && created != null) {
                    if (channel != null) {
                        try { channel.delete().complete(); } catch (Exception cleanup) { error.addSuppressed(cleanup); }
                    }
                    try { service.abortCreation(created.id(), event.getUser().getId()); }
                    catch (Exception cleanup) { error.addSuppressed(cleanup); }
                }
                hook.editOriginal("❌ " + ErrorMessages.safe(error)).queue();
            }
        }));
    }

    private void showDenyModal(ButtonInteractionEvent event, UUID ticketId) {
        TextInput reason = TextInput.create("reason", TextInputStyle.PARAGRAPH)
                .setPlaceholder("Why is this help request being denied?")
                .setRequiredRange(3, 500)
                .build();
        Modal modal = Modal.create(DENY_MODAL_PREFIX + ticketId, "Deny Spawn Help")
                .addComponents(Label.of("Denial reason", reason))
                .build();
        event.replyModal(modal).queue();
    }

    private void handleDenyModal(ModalInteractionEvent event, String rawTicketId) {
        UUID ticketId;
        try {
            ticketId = UUID.fromString(rawTicketId);
            requireHelper(event.getMember());
        } catch (Exception error) {
            event.reply("❌ " + ErrorMessages.safe(error)).setEphemeral(true).queue();
            return;
        }
        String reason = Optional.ofNullable(event.getValue("reason"))
                .map(ModalMapping::getAsOptionalString)
                .orElse("");
        event.deferReply(true).queue(hook -> executor.submit(() -> {
            try {
                SpawnHelpTicket updated = service.deny(
                        ticketId, event.getUser().getId(), event.getUser().getName(), reason);
                TextChannel channel = ticketChannel(event.getGuild(), updated).orElse(null);
                refreshControlMessageQuietly(channel, updated);
                notifyStateChange(event.getJDA(), channel, updated);
                closeTicketChannelQuietly(channel, updated);
                hook.editOriginal("✅ Spawn-help ticket denied and closed.").queue();
            } catch (Exception error) {
                hook.editOriginal("❌ " + ErrorMessages.safe(error)).queue();
            }
        }));
    }

    private MessageEmbed embed(SpawnHelpTicket ticket) {
        EmbedBuilder builder = new EmbedBuilder()
                .setTitle("Spawn Help · " + SpawnHelpTextFormatter.statusLabel(ticket.status()))
                .setDescription("Private spawn-help request. Only the requester, configured Spawn Helper roles, and Discord administrators can access this channel.")
                .addField("Server", ticket.serverName(), true)
                .addField("In-game name", "`" + ticket.inGameName() + "`", true)
                .addField("Requester", "<@" + ticket.discordUserId() + ">", true)
                .addField("Ticket", "`" + ticket.id() + "`", false)
                .setColor(color(ticket.status()));
        if (ticket.helperDiscordId() != null && !ticket.helperDiscordId().isBlank()) {
            builder.addField("Spawn helper", "<@" + ticket.helperDiscordId() + ">", true);
        }
        if (ticket.status() == SpawnHelpStatus.DENIED && ticket.denialReason() != null) {
            builder.addField("Denial reason", ticket.denialReason(), false);
        }
        if (ticket.status() == SpawnHelpStatus.OPEN) {
            builder.addField("Next step", "A Spawn Helper should click **Pick Up** before starting assistance.", false);
        } else if (ticket.status() == SpawnHelpStatus.ON_HELP) {
            builder.addField("Next step", "The assigned Spawn Helper should click **Complete** after the player has been helped.", false);
        }
        return builder.build();
    }

    private List<ActionRow> components(SpawnHelpTicket ticket) {
        if (ticket.status() == SpawnHelpStatus.OPEN) {
            return List.of(ActionRow.of(
                    Button.success(TICKET_BUTTON_PREFIX + "pickup:" + ticket.id(), "Pick Up"),
                    Button.danger(TICKET_BUTTON_PREFIX + "deny:" + ticket.id(), "Deny")
            ));
        }
        if (ticket.status() == SpawnHelpStatus.ON_HELP) {
            return List.of(ActionRow.of(
                    Button.success(TICKET_BUTTON_PREFIX + "complete:" + ticket.id(), "Complete"),
                    Button.danger(TICKET_BUTTON_PREFIX + "deny:" + ticket.id(), "Deny")
            ));
        }
        return List.of();
    }

    private void refreshControlMessage(TextChannel channel, SpawnHelpTicket ticket) {
        if (ticket.controlMessageId() == null || ticket.controlMessageId().isBlank()) {
            throw new IllegalStateException("Spawn-help ticket has no control message ID.");
        }
        channel.retrieveMessageById(ticket.controlMessageId()).complete()
                .editMessageEmbeds(embed(ticket))
                .setComponents(components(ticket))
                .complete();
    }

    private void notifyStateChange(JDA jda, TextChannel channel, SpawnHelpTicket ticket) {
        String text = stateMessage(ticket);
        if (channel != null) {
            try {
                channel.sendMessage("<@" + ticket.discordUserId() + "> " + text).complete();
            } catch (Exception error) {
                log.warn("Could not post spawn-help state {} for ticket {} in channel {}: {}",
                        ticket.status(), ticket.id(), channel.getId(), error.getMessage());
            }
        }
        try {
            User user = jda.retrieveUserById(ticket.discordUserId()).complete();
            String location = channel == null ? "" : "\nTicket: <#" + channel.getId() + ">";
            user.openPrivateChannel().complete().sendMessage(text + location).complete();
        } catch (Exception error) {
            log.warn("Could not DM spawn-help state {} for ticket {} to Discord user {}: {}",
                    ticket.status(), ticket.id(), ticket.discordUserId(), error.getMessage());
        }
    }

    private String stateMessage(SpawnHelpTicket ticket) {
        String template = switch (ticket.status()) {
            case OPEN -> config.openMessage();
            case ON_HELP -> config.pickedMessage();
            case COMPLETED -> config.completedMessage();
            case DENIED -> config.deniedMessage();
        };
        String helper = ticket.helperDiscordId() == null ? "a spawn helper" : "<@" + ticket.helperDiscordId() + ">";
        String reason = ticket.denialReason() == null || ticket.denialReason().isBlank() ? "No reason provided" : ticket.denialReason();
        return template
                .replace("{server}", ticket.serverName())
                .replace("{ign}", ticket.inGameName())
                .replace("{helper}", helper)
                .replace("{reason}", reason)
                .replace("{status}", SpawnHelpTextFormatter.statusLabel(ticket.status()));
    }

    private void refreshControlMessageQuietly(TextChannel channel, SpawnHelpTicket ticket) {
        if (channel == null) return;
        try {
            refreshControlMessage(channel, ticket);
        } catch (Exception error) {
            log.warn("Could not refresh spawn-help control message for ticket {}: {}", ticket.id(), error.getMessage());
        }
    }

    private void closeTicketChannelQuietly(TextChannel channel, SpawnHelpTicket ticket) {
        if (channel == null) return;
        try {
            Guild guild = channel.getGuild();
            Member requester = guild.getMemberById(ticket.discordUserId());
            ticketChannels.close(channel, requester, helperRoles(guild));
        } catch (Exception error) {
            log.warn("Could not lock closed spawn-help channel for ticket {}: {}", ticket.id(), error.getMessage());
        }
    }

    private List<Role> helperRoles(Guild guild) {
        List<Role> roles = resources.roles(guild, config.helperRoleIds());
        if (roles.size() != config.helperRoleIds().size()) {
            throw new IllegalStateException("One or more configured Spawn Helper role IDs could not be resolved.");
        }
        return roles;
    }

    private void requireHelper(Member member) {
        if (member == null) throw new SecurityException("Could not resolve your Discord membership.");
        boolean allowed = member.getRoles().stream().anyMatch(role -> config.helperRoleIds().contains(role.getId()));
        if (!allowed) throw new SecurityException("Only members with a configured Spawn Helper role can perform this action.");
    }

    private static Member requireMember(Guild guild, String discordId, String label) {
        Member member = guild.getMemberById(discordId);
        if (member != null) return member;
        try {
            return guild.retrieveMemberById(discordId).complete();
        } catch (Exception error) {
            throw new IllegalStateException("Could not resolve the spawn-help " + label + " in this Discord server.", error);
        }
    }

    private static Optional<TextChannel> ticketChannel(Guild guild, SpawnHelpTicket ticket) {
        if (guild == null || ticket.channelId() == null || ticket.channelId().isBlank()) return Optional.empty();
        return Optional.ofNullable(guild.getTextChannelById(ticket.channelId()));
    }

    private boolean validGuild(Guild guild) {
        return guild != null && guildId.equals(guild.getId());
    }

    private static boolean terminal(SpawnHelpStatus status) {
        return status == SpawnHelpStatus.COMPLETED || status == SpawnHelpStatus.DENIED;
    }

    private static String successMessage(SpawnHelpTicket ticket) {
        return switch (ticket.status()) {
            case ON_HELP -> "✅ Ticket picked up. You are now the assigned Spawn Helper.";
            case COMPLETED -> "✅ Help completed. The ticket has been closed.";
            case DENIED -> "✅ Ticket denied and closed.";
            case OPEN -> "✅ Ticket updated.";
        };
    }

    private static Color color(SpawnHelpStatus status) {
        return switch (status) {
            case OPEN -> new Color(52, 152, 219);
            case ON_HELP -> new Color(241, 196, 15);
            case COMPLETED -> new Color(46, 204, 113);
            case DENIED -> new Color(231, 76, 60);
        };
    }

    private static void tryResolve(List<String> problems, Runnable action) {
        try { action.run(); }
        catch (Exception error) { problems.add(ErrorMessages.safe(error)); }
    }

    @Override
    public void close() {
        executor.close();
    }
}
