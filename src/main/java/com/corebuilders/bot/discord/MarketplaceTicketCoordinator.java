package com.corebuilders.bot.discord;

import com.corebuilders.bot.config.MarketplaceTicketConfig;
import com.corebuilders.bot.model.MarketplaceModels.MarketplaceOrder;
import com.corebuilders.bot.model.MarketplaceModels.MarketplaceOrderLine;
import com.corebuilders.bot.model.MarketplaceModels.MarketplaceTicket;
import com.corebuilders.bot.service.MarketplaceTicketStore;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

import java.awt.Color;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.corebuilders.bot.service.MarketplaceStates.*;

/** Reliable Discord ticket creation and status synchronization for marketplace lines. */
public final class MarketplaceTicketCoordinator {
    public static final String BUTTON_PREFIX = "market-ticket:";
    private static final String TOPIC_PREFIX = "marketplace-line:";

    private final JDA jda;
    private final String guildId;
    private final MarketplaceTicketConfig config;
    private final Set<String> leadershipRoleIds;
    private final MarketplaceTicketStore store;
    private final MarketplaceTicketChannels channels = new MarketplaceTicketChannels();
    private final DiscordResourceResolver resolver = new DiscordResourceResolver();
    private final Logger logger;

    public MarketplaceTicketCoordinator(JDA jda, String guildId, MarketplaceTicketConfig config,
                                        Set<String> leadershipRoleIds, MarketplaceTicketStore store,
                                        Logger logger) {
        this.jda = Objects.requireNonNull(jda, "jda");
        this.guildId = Objects.requireNonNull(guildId, "guildId");
        this.config = Objects.requireNonNull(config, "config");
        this.leadershipRoleIds = Set.copyOf(leadershipRoleIds == null ? Set.of() : leadershipRoleIds);
        this.store = Objects.requireNonNull(store, "store");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public boolean enabled() {
        return config.enabled();
    }

    public void validate() {
        if (!enabled()) return;
        Guild guild = requireGuild();
        requireCategory(guild);
        for (String roleId : leadershipRoleIds) {
            resolver.requireRole(guild, roleId, "discord.permissions.leadership-role-ids");
        }
        if (leadershipRoleIds.isEmpty()) {
            logger.warning("Marketplace tickets are enabled, but no leadership role IDs are configured.");
        }
    }

    public void reconcile() {
        if (!enabled()) return;
        for (MarketplaceTicket ticket : store.pending(config.reconciliationLimit())) {
            ensureTicket(ticket.lineId());
        }
    }

    public void ensureTickets(MarketplaceOrder order) {
        if (!enabled() || order == null) return;
        for (MarketplaceOrderLine line : order.lines()) {
            ensureTicket(line.id());
        }
    }

    /**
     * Best-effort side effect. A Discord outage must never turn a committed checkout into an HTTP failure.
     * The database ticket state remains PENDING/FAILED so reconciliation can retry it on restart.
     */
    public void ensureTicket(UUID lineId) {
        if (!enabled()) return;
        boolean claimed = false;
        try {
            MarketplaceTicket ticket = store.find(lineId).orElse(null);
            if (ticket == null || terminal(ticket.lineStatus())) return;
            if (MarketplaceTicketStore.TICKET_OPEN.equals(ticket.ticketState())
                    && ticket.channelId() != null && !ticket.channelId().isBlank()) {
                refresh(lineId);
                return;
            }
            claimed = store.claim(lineId);
            if (!claimed) return;

            Guild guild = requireGuild();
            Category category = requireCategory(guild);
            String topic = TOPIC_PREFIX + lineId;
            TextChannel channel = category.getTextChannels().stream()
                    .filter(candidate -> topic.equals(candidate.getTopic()))
                    .findFirst().orElse(null);
            Member buyer = requireMember(guild, ticket.buyerDiscordId(), "buyer");
            Member seller = requireMember(guild, ticket.sellerDiscordId(), "seller");
            if (channel == null) {
                List<Role> leaders = resolver.roles(guild, leadershipRoleIds);
                channel = channels.createPrivate(category, channelName(ticket), topic, buyer, seller, leaders,
                        guild.getSelfMember());
            }
            var message = channel.sendMessageEmbeds(embed(ticket))
                    .setComponents(components(ticket))
                    .complete();
            store.markOpen(lineId, channel.getId(), message.getId());
        } catch (Exception error) {
            if (claimed) markFailedQuietly(lineId);
            logger.log(Level.WARNING, "Could not create marketplace ticket for line " + lineId, error);
        }
    }

    /** Keeps the control message synchronized without affecting the completed marketplace transaction. */
    public void refresh(UUID lineId) {
        if (!enabled()) return;
        MarketplaceTicket ticket = null;
        try {
            ticket = store.find(lineId).orElse(null);
            if (ticket == null) return;
            if (ticket.channelId() == null || ticket.channelId().isBlank()) {
                ensureTicket(lineId);
                return;
            }
            TextChannel channel = requireGuild().getTextChannelById(ticket.channelId());
            if (channel == null) {
                store.markFailed(lineId);
                return;
            }
            if (ticket.messageId() == null || ticket.messageId().isBlank()) {
                var message = channel.sendMessageEmbeds(embed(ticket)).setComponents(components(ticket)).complete();
                store.markOpen(lineId, channel.getId(), message.getId());
            } else {
                channel.retrieveMessageById(ticket.messageId()).complete()
                        .editMessageEmbeds(embed(ticket))
                        .setComponents(components(ticket))
                        .complete();
            }
            if (terminal(ticket.lineStatus())) {
                if (config.lockOnTerminalState()) lockParticipants(channel, ticket);
                store.markClosed(lineId);
            }
        } catch (Exception error) {
            if (ticket == null || !terminal(ticket.lineStatus())) markFailedQuietly(lineId);
            logger.log(Level.WARNING, "Could not refresh marketplace ticket for line " + lineId, error);
        }
    }

    private void markFailedQuietly(UUID lineId) {
        try {
            store.markFailed(lineId);
        } catch (Exception stateError) {
            logger.log(Level.WARNING, "Could not persist failed marketplace ticket state for line " + lineId,
                    stateError);
        }
    }

    private void lockParticipants(TextChannel channel, MarketplaceTicket ticket) {
        Guild guild = channel.getGuild();
        lockParticipantQuietly(channel, guild.getMemberById(ticket.buyerDiscordId()), "buyer", ticket.lineId());
        lockParticipantQuietly(channel, guild.getMemberById(ticket.sellerDiscordId()), "seller", ticket.lineId());
    }

    private void lockParticipantQuietly(TextChannel channel, Member member, String role, UUID lineId) {
        if (member == null) return;
        try {
            channels.lockParticipant(channel, member);
        } catch (Exception error) {
            logger.log(Level.WARNING,
                    "Could not lock marketplace ticket " + role + " for line " + lineId, error);
        }
    }

    private MessageEmbed embed(MarketplaceTicket ticket) {
        String status = displayStatus(ticket.lineStatus());
        EmbedBuilder builder = new EmbedBuilder()
                .setTitle("Marketplace Order · " + status)
                .setDescription("Use this private channel to coordinate delivery. Buyer, seller, and configured leadership roles can chat here.")
                .addField("Item", ticket.itemName(), false)
                .addField("Quantity", Integer.toString(ticket.quantity()), true)
                .addField("Total", ticket.lineTotal() + " coins", true)
                .addField("Buyer", mention(ticket.buyerDiscordId(), ticket.buyerUsername()), true)
                .addField("Seller", mention(ticket.sellerDiscordId(), ticket.sellerUsername()), true)
                .addField("Order line", "`" + ticket.lineId() + "`", false)
                .setColor(color(ticket.lineStatus()));
        if (LINE_PENDING.equals(ticket.lineStatus())) {
            builder.addField("Next step", "Buyer marks the order delivered/received. The seller then confirms completion and receives the escrowed coins.", false);
        } else if (LINE_DELIVERED.equals(ticket.lineStatus())) {
            builder.addField("Next step", "Seller must confirm delivery to release the escrowed coins.", false);
        } else if (LINE_DISPUTED.equals(ticket.lineStatus())) {
            builder.addField("Next step", "Leadership must resolve the dispute with the marketplace dispute command.", false);
        }
        return builder.build();
    }

    private List<ActionRow> components(MarketplaceTicket ticket) {
        if (LINE_PENDING.equals(ticket.lineStatus())) {
            return List.of(ActionRow.of(
                    Button.success(BUTTON_PREFIX + "delivered:" + ticket.lineId(), "Buyer: Mark delivered"),
                    Button.danger(BUTTON_PREFIX + "buyer-cancel:" + ticket.lineId(), "Buyer: Cancel"),
                    Button.danger(BUTTON_PREFIX + "seller-cancel:" + ticket.lineId(), "Seller: Cancel")
            ));
        }
        if (LINE_DELIVERED.equals(ticket.lineStatus())) {
            return List.of(ActionRow.of(
                    Button.success(BUTTON_PREFIX + "seller-confirm:" + ticket.lineId(), "Seller: Confirm delivery")
            ));
        }
        return List.of();
    }

    private Category requireCategory(Guild guild) {
        String reference = config.category();
        if (reference.matches("\\d{15,22}")) {
            Category category = guild.getCategoryById(reference);
            if (category == null) {
                throw new IllegalStateException(
                        "discord.marketplace-tickets.category could not be resolved as a Discord category ID: "
                                + reference);
            }
            return category;
        }
        return guild.getCategoriesByName(reference, true).stream().findFirst()
                .orElseGet(() -> guild.createCategory(reference).complete());
    }

    private Guild requireGuild() {
        Guild guild = jda.getGuildById(guildId);
        if (guild == null) throw new IllegalStateException("Configured Discord guild is unavailable: " + guildId);
        return guild;
    }

    private static Member requireMember(Guild guild, String discordId, String role) {
        if (discordId == null || !discordId.matches("\\d{15,22}")) {
            throw new IllegalStateException("Marketplace " + role + " has no linked Discord account.");
        }
        Member member = guild.getMemberById(discordId);
        if (member != null) return member;
        return guild.retrieveMemberById(discordId).complete();
    }

    private String channelName(MarketplaceTicket ticket) {
        String id = ticket.lineId().toString().substring(0, 8);
        String value = config.namePattern()
                .replace("{item}", ticket.itemName())
                .replace("{buyer}", ticket.buyerUsername() == null ? "buyer" : ticket.buyerUsername())
                .replace("{seller}", ticket.sellerUsername() == null ? "seller" : ticket.sellerUsername())
                .replace("{id}", id);
        value = value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9-]+", "-")
                .replaceAll("-+", "-").replaceAll("^-|-$", "");
        if (value.isBlank()) value = "order-" + id;
        return value.length() <= 90 ? value : value.substring(0, 90);
    }

    private static String mention(String discordId, String fallback) {
        return discordId != null && discordId.matches("\\d{15,22}")
                ? "<@" + discordId + ">"
                : (fallback == null || fallback.isBlank() ? "Unknown" : fallback);
    }

    private static boolean terminal(String status) {
        return LINE_SETTLED.equals(status) || LINE_CANCELLED.equals(status) || LINE_REFUNDED.equals(status);
    }

    private static String displayStatus(String status) {
        if (LINE_PENDING.equals(status)) return "Awaiting buyer";
        if (LINE_DELIVERED.equals(status)) return "Awaiting seller";
        if (LINE_SETTLED.equals(status)) return "Completed";
        if (LINE_CANCELLED.equals(status)) return "Cancelled";
        if (LINE_REFUNDED.equals(status)) return "Refunded";
        if (LINE_DISPUTED.equals(status)) return "Disputed";
        return status == null ? "Unknown" : status.replace('_', ' ');
    }

    private static Color color(String status) {
        if (LINE_SETTLED.equals(status)) return new Color(0x2ECC71);
        if (LINE_CANCELLED.equals(status) || LINE_REFUNDED.equals(status)) return new Color(0x95A5A6);
        if (LINE_DISPUTED.equals(status)) return new Color(0xE74C3C);
        if (LINE_DELIVERED.equals(status)) return new Color(0xF1C40F);
        return new Color(0x3498DB);
    }
}
