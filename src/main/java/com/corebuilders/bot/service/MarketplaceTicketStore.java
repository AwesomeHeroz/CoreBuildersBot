package com.corebuilders.bot.service;

import com.corebuilders.bot.db.QueryDslDatabase;
import com.corebuilders.bot.db.Schema.QMembers;
import com.corebuilders.bot.model.MarketplaceModels.MarketplaceTicket;
import com.querydsl.core.Tuple;

import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static com.corebuilders.bot.db.DbValues.now;
import static com.corebuilders.bot.db.DbValues.uuid;
import static com.corebuilders.bot.db.Schema.MARKETPLACE_ORDER_ITEMS;
import static com.corebuilders.bot.db.Schema.MARKETPLACE_ORDERS;
import static com.corebuilders.bot.service.MarketplaceStates.LINE_DELIVERED;
import static com.corebuilders.bot.service.MarketplaceStates.LINE_DISPUTED;
import static com.corebuilders.bot.service.MarketplaceStates.LINE_PENDING;

/** Persistent outbox/state store for Discord marketplace tickets. */
public final class MarketplaceTicketStore {
    public static final String TICKET_PENDING = "PENDING";
    public static final String TICKET_CREATING = "CREATING";
    public static final String TICKET_OPEN = "OPEN";
    public static final String TICKET_FAILED = "FAILED";
    public static final String TICKET_CLOSED = "CLOSED";

    private final QueryDslDatabase database;
    private final QMembers buyer = new QMembers("ticket_buyer");
    private final QMembers seller = new QMembers("ticket_seller");

    public MarketplaceTicketStore(QueryDslDatabase database) {
        this.database = Objects.requireNonNull(database, "database");
    }

    public List<MarketplaceTicket> pending(int rawLimit) {
        int limit = Math.max(1, Math.min(500, rawLimit));
        var stale = now().minus(10, ChronoUnit.MINUTES);
        return database.query(q -> q.select(ticketColumns())
                .from(MARKETPLACE_ORDER_ITEMS)
                .join(MARKETPLACE_ORDERS).on(MARKETPLACE_ORDERS.id.eq(MARKETPLACE_ORDER_ITEMS.orderId))
                .join(buyer).on(buyer.id.eq(MARKETPLACE_ORDERS.buyerMemberId))
                .join(seller).on(seller.id.eq(MARKETPLACE_ORDER_ITEMS.sellerMemberId))
                .where(MARKETPLACE_ORDER_ITEMS.status.in(LINE_PENDING, LINE_DELIVERED, LINE_DISPUTED),
                        MARKETPLACE_ORDER_ITEMS.discordTicketState.in(TICKET_PENDING, TICKET_FAILED)
                                .or(MARKETPLACE_ORDER_ITEMS.discordTicketState.eq(TICKET_CREATING)
                                        .and(MARKETPLACE_ORDER_ITEMS.discordTicketUpdatedAt.lt(stale))))
                .orderBy(MARKETPLACE_ORDER_ITEMS.createdAt.asc())
                .limit(limit)
                .fetch().stream().map(this::map).toList());
    }

    public Optional<MarketplaceTicket> find(UUID lineId) {
        Objects.requireNonNull(lineId, "lineId");
        return database.query(q -> Optional.ofNullable(q.select(ticketColumns())
                .from(MARKETPLACE_ORDER_ITEMS)
                .join(MARKETPLACE_ORDERS).on(MARKETPLACE_ORDERS.id.eq(MARKETPLACE_ORDER_ITEMS.orderId))
                .join(buyer).on(buyer.id.eq(MARKETPLACE_ORDERS.buyerMemberId))
                .join(seller).on(seller.id.eq(MARKETPLACE_ORDER_ITEMS.sellerMemberId))
                .where(MARKETPLACE_ORDER_ITEMS.id.eq(uuid(lineId)))
                .fetchOne()).map(this::map));
    }

    public boolean claim(UUID lineId) {
        var stale = now().minus(10, ChronoUnit.MINUTES);
        long changed = database.query(q -> q.update(MARKETPLACE_ORDER_ITEMS)
                .set(MARKETPLACE_ORDER_ITEMS.discordTicketState, TICKET_CREATING)
                .set(MARKETPLACE_ORDER_ITEMS.discordTicketUpdatedAt, now())
                .where(MARKETPLACE_ORDER_ITEMS.id.eq(uuid(lineId)),
                        MARKETPLACE_ORDER_ITEMS.discordTicketState.in(TICKET_PENDING, TICKET_FAILED)
                                .or(MARKETPLACE_ORDER_ITEMS.discordTicketState.eq(TICKET_CREATING)
                                        .and(MARKETPLACE_ORDER_ITEMS.discordTicketUpdatedAt.lt(stale))))
                .execute());
        return changed == 1;
    }

    public void markOpen(UUID lineId, String channelId, String messageId) {
        database.query(q -> q.update(MARKETPLACE_ORDER_ITEMS)
                .set(MARKETPLACE_ORDER_ITEMS.discordTicketState, TICKET_OPEN)
                .set(MARKETPLACE_ORDER_ITEMS.discordChannelId, channelId)
                .set(MARKETPLACE_ORDER_ITEMS.discordMessageId, messageId)
                .set(MARKETPLACE_ORDER_ITEMS.discordTicketUpdatedAt, now())
                .where(MARKETPLACE_ORDER_ITEMS.id.eq(uuid(lineId)))
                .execute());
    }

    public void markFailed(UUID lineId) {
        database.query(q -> q.update(MARKETPLACE_ORDER_ITEMS)
                .set(MARKETPLACE_ORDER_ITEMS.discordTicketState, TICKET_FAILED)
                .set(MARKETPLACE_ORDER_ITEMS.discordTicketUpdatedAt, now())
                .where(MARKETPLACE_ORDER_ITEMS.id.eq(uuid(lineId)))
                .execute());
    }

    public void markClosed(UUID lineId) {
        database.query(q -> q.update(MARKETPLACE_ORDER_ITEMS)
                .set(MARKETPLACE_ORDER_ITEMS.discordTicketState, TICKET_CLOSED)
                .set(MARKETPLACE_ORDER_ITEMS.discordTicketUpdatedAt, now())
                .where(MARKETPLACE_ORDER_ITEMS.id.eq(uuid(lineId)))
                .execute());
    }

    private com.querydsl.core.types.Expression<?>[] ticketColumns() {
        return new com.querydsl.core.types.Expression<?>[]{
                MARKETPLACE_ORDER_ITEMS.id, MARKETPLACE_ORDER_ITEMS.orderId,
                MARKETPLACE_ORDERS.buyerMemberId, MARKETPLACE_ORDER_ITEMS.sellerMemberId,
                buyer.discordUserId, seller.discordUserId, buyer.username, seller.username,
                MARKETPLACE_ORDER_ITEMS.itemName, MARKETPLACE_ORDER_ITEMS.quantity,
                MARKETPLACE_ORDER_ITEMS.lineTotal, MARKETPLACE_ORDER_ITEMS.status,
                MARKETPLACE_ORDER_ITEMS.discordTicketState, MARKETPLACE_ORDER_ITEMS.discordChannelId,
                MARKETPLACE_ORDER_ITEMS.discordMessageId
        };
    }

    private MarketplaceTicket map(Tuple row) {
        Integer quantity = row.get(MARKETPLACE_ORDER_ITEMS.quantity);
        Long total = row.get(MARKETPLACE_ORDER_ITEMS.lineTotal);
        return new MarketplaceTicket(
                UUID.fromString(row.get(MARKETPLACE_ORDER_ITEMS.id)),
                UUID.fromString(row.get(MARKETPLACE_ORDER_ITEMS.orderId)),
                UUID.fromString(row.get(MARKETPLACE_ORDERS.buyerMemberId)),
                UUID.fromString(row.get(MARKETPLACE_ORDER_ITEMS.sellerMemberId)),
                row.get(buyer.discordUserId), row.get(seller.discordUserId),
                row.get(buyer.username), row.get(seller.username),
                row.get(MARKETPLACE_ORDER_ITEMS.itemName), quantity == null ? 0 : quantity,
                total == null ? 0L : total, row.get(MARKETPLACE_ORDER_ITEMS.status),
                row.get(MARKETPLACE_ORDER_ITEMS.discordTicketState),
                row.get(MARKETPLACE_ORDER_ITEMS.discordChannelId),
                row.get(MARKETPLACE_ORDER_ITEMS.discordMessageId)
        );
    }
}
