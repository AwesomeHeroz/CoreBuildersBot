package com.corebuilders.bot.discord;

import com.corebuilders.bot.model.Models.Member;
import com.corebuilders.bot.service.MarketplaceOrderOperations;
import com.corebuilders.bot.service.MemberService;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Handles buyer/seller marketplace actions directly inside private Discord tickets. */
public final class MarketplaceTicketDiscordListener extends ListenerAdapter implements AutoCloseable {
    private final String guildId;
    private final MemberService members;
    private final MarketplaceOrderOperations orders;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public MarketplaceTicketDiscordListener(String guildId, MemberService members, MarketplaceOrderOperations orders) {
        this.guildId = Objects.requireNonNull(guildId, "guildId");
        this.members = Objects.requireNonNull(members, "members");
        this.orders = Objects.requireNonNull(orders, "orders");
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String id = event.getComponentId();
        if (!id.startsWith(MarketplaceTicketCoordinator.BUTTON_PREFIX)) return;
        if (!event.isFromGuild() || event.getGuild() == null || !guildId.equals(event.getGuild().getId())) {
            event.reply("This marketplace action can only be used in the configured Core Builders server.")
                    .setEphemeral(true).queue();
            return;
        }
        event.deferReply(true).queue(hook -> executor.submit(() -> {
            try {
                Action action = parse(id);
                Member actor = members.requireByDiscordId(event.getUser().getId());
                switch (action.type()) {
                    case "delivered" -> orders.markDelivered(actor.id(), action.lineId());
                    case "seller-confirm" -> orders.confirmDelivery(actor.id(), action.lineId());
                    case "buyer-cancel" -> orders.cancelLine(actor.id(), action.lineId());
                    case "seller-cancel" -> orders.cancelSale(actor.id(), action.lineId());
                    default -> throw new IllegalArgumentException("Unknown marketplace ticket action.");
                }
                hook.editOriginal(success(action.type())).queue();
            } catch (Exception error) {
                String message = error.getMessage();
                hook.editOriginal("❌ " + (message == null || message.isBlank()
                        ? "Marketplace action failed." : message)).queue();
            }
        }));
    }

    private static Action parse(String componentId) {
        String value = componentId.substring(MarketplaceTicketCoordinator.BUTTON_PREFIX.length());
        int separator = value.lastIndexOf(':');
        if (separator < 1 || separator == value.length() - 1) {
            throw new IllegalArgumentException("Invalid marketplace ticket action.");
        }
        return new Action(value.substring(0, separator), UUID.fromString(value.substring(separator + 1)));
    }

    private static String success(String action) {
        return switch (action) {
            case "delivered" -> "✅ Delivery was marked by the buyer. The seller must now confirm it.";
            case "seller-confirm" -> "✅ Delivery confirmed. Escrowed coins were released to the seller.";
            case "buyer-cancel" -> "✅ The buyer cancelled the order. Coins were refunded and stock restored.";
            case "seller-cancel" -> "✅ The seller cancelled the order. Coins were refunded and stock restored.";
            default -> "✅ Marketplace order updated.";
        };
    }

    @Override public void close() { executor.close(); }

    private record Action(String type, UUID lineId) {}
}
