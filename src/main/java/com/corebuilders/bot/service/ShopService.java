package com.corebuilders.bot.service;

import com.corebuilders.bot.db.QueryDslDatabase;
import com.corebuilders.bot.model.Domain.OrderStatus;
import com.corebuilders.bot.model.Domain.SourceType;
import com.corebuilders.bot.model.Models.Member;
import com.corebuilders.bot.model.Models.ShopItem;
import com.corebuilders.bot.model.Models.ShopOrder;
import com.corebuilders.bot.model.ShopCatalog;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static com.corebuilders.bot.db.DbMappers.shopItemColumns;
import static com.corebuilders.bot.db.DbMappers.shopOrderColumns;
import static com.corebuilders.bot.db.DbValues.now;
import static com.corebuilders.bot.db.DbValues.uuid;
import static com.corebuilders.bot.db.Schema.*;

public final class ShopService {
    private final QueryDslDatabase database;
    private final LedgerService ledger;
    private final AuditService audit;

    public ShopService(QueryDslDatabase database, LedgerService ledger, AuditService audit) {
        this.database = Objects.requireNonNull(database, "database");
        this.ledger = Objects.requireNonNull(ledger, "ledger");
        this.audit = Objects.requireNonNull(audit, "audit");
    }

    /** Synchronizes configured catalog metadata without deleting historical order rows. */
    public CatalogSyncResult synchronizeCatalog(ShopCatalog catalog) {
        Objects.requireNonNull(catalog, "catalog");
        return database.inTransaction(() -> {
            Set<String> existingCodes = new HashSet<>(database.query(q -> q.select(SHOP_ITEMS.code)
                    .from(SHOP_ITEMS).fetch()));
            int inserted = 0;
            int updated = 0;
            for (ShopItem configured : catalog.items()) {
                if (!existingCodes.contains(configured.code())) {
                    database.query(q -> {
                        var statement = q.insert(SHOP_ITEMS)
                                .set(SHOP_ITEMS.code, configured.code())
                                .set(SHOP_ITEMS.name, configured.name())
                                .set(SHOP_ITEMS.description, configured.description())
                                .set(SHOP_ITEMS.price, configured.price())
                                .set(SHOP_ITEMS.active, configured.active());
                        if (configured.stock() == null) statement.setNull(SHOP_ITEMS.stock);
                        else statement.set(SHOP_ITEMS.stock, configured.stock());
                        return statement.execute();
                    });
                    inserted++;
                } else {
                    database.query(q -> {
                        var statement = q.update(SHOP_ITEMS)
                                .set(SHOP_ITEMS.name, configured.name())
                                .set(SHOP_ITEMS.description, configured.description())
                                .set(SHOP_ITEMS.price, configured.price())
                                .set(SHOP_ITEMS.active, configured.active());
                        if (catalog.syncStockOnStartup()) {
                            if (configured.stock() == null) statement.setNull(SHOP_ITEMS.stock);
                            else statement.set(SHOP_ITEMS.stock, configured.stock());
                        }
                        return statement.where(SHOP_ITEMS.code.eq(configured.code())).execute();
                    });
                    updated++;
                }
            }
            long disabled = 0;
            if (catalog.disableUnlistedItems()) {
                List<String> configuredCodes = catalog.items().stream().map(ShopItem::code).toList();
                disabled = database.query(q -> {
                    var update = q.update(SHOP_ITEMS).set(SHOP_ITEMS.active, false)
                            .where(SHOP_ITEMS.active.isTrue());
                    if (!configuredCodes.isEmpty()) update.where(SHOP_ITEMS.code.notIn(configuredCodes));
                    return update.execute();
                });
            }
            return new CatalogSyncResult(inserted, updated, (int) disabled);
        });
    }

    public List<ShopItem> activeItems() {
        return database.query(q -> q.select(shopItemColumns())
                .from(SHOP_ITEMS)
                .where(SHOP_ITEMS.active.isTrue())
                .orderBy(SHOP_ITEMS.price.asc(), SHOP_ITEMS.name.asc())
                .fetch()
                .stream()
                .map(com.corebuilders.bot.db.DbMappers::shopItem)
                .toList());
    }

    public ShopOrder buy(Member member, String itemCode) {
        Objects.requireNonNull(member, "member");
        String normalizedCode = normalizeItemCode(itemCode);
        return database.inTransaction(() -> {
            ledger.lockMember(member.id());
            ShopItem item = lockItem(normalizedCode);

            if (item.stock() != null && item.stock() <= 0) {
                throw new IllegalStateException("That item is out of stock.");
            }
            long balance = ledger.creditBalance(member.id());
            if (balance < item.price()) {
                throw new IllegalStateException("Insufficient Core Credits. You need "
                        + item.price() + " CC but have " + balance + " CC.");
            }

            UUID orderId = UUID.randomUUID();
            ledger.addCredits(member.id(), -item.price(), SourceType.SHOP_PURCHASE,
                    orderId, "Shop purchase: " + item.name(), member.discordUserId());

            database.query(q -> q.insert(SHOP_ORDERS)
                    .set(SHOP_ORDERS.id, uuid(orderId))
                    .set(SHOP_ORDERS.memberId, uuid(member.id()))
                    .set(SHOP_ORDERS.itemCode, item.code())
                    .set(SHOP_ORDERS.price, item.price())
                    .set(SHOP_ORDERS.status, OrderStatus.PENDING.name())
                    .set(SHOP_ORDERS.createdAt, now())
                    .execute());

            if (item.stock() != null) {
                database.query(q -> q.update(SHOP_ITEMS)
                        .set(SHOP_ITEMS.stock, SHOP_ITEMS.stock.subtract(1))
                        .where(SHOP_ITEMS.code.eq(item.code()))
                        .execute());
            }
            audit.log(member.discordUserId(), "SHOP_PURCHASE", member.discordUserId(),
                    "SHOP_ORDER", orderId.toString(), item.name() + " for " + item.price() + " CC");
            return getOrder(orderId);
        });
    }

    public List<ShopOrder> orders(OrderStatus status, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 25));
        return database.query(q -> {
            var query = q.select(shopOrderColumns())
                    .from(SHOP_ORDERS)
                    .join(MEMBERS).on(MEMBERS.id.eq(SHOP_ORDERS.memberId))
                    .join(SHOP_ITEMS).on(SHOP_ITEMS.code.eq(SHOP_ORDERS.itemCode));
            if (status != null) {
                query.where(SHOP_ORDERS.status.eq(status.name()));
                query.orderBy(SHOP_ORDERS.createdAt.asc());
            } else {
                query.orderBy(SHOP_ORDERS.createdAt.desc());
            }
            return query.limit(safeLimit)
                    .fetch()
                    .stream()
                    .map(com.corebuilders.bot.db.DbMappers::shopOrder)
                    .toList();
        });
    }

    public List<ShopOrder> memberOrders(UUID memberId, int limit) {
        return database.query(q -> q.select(shopOrderColumns())
                .from(SHOP_ORDERS)
                .join(MEMBERS).on(MEMBERS.id.eq(SHOP_ORDERS.memberId))
                .join(SHOP_ITEMS).on(SHOP_ITEMS.code.eq(SHOP_ORDERS.itemCode))
                .where(SHOP_ORDERS.memberId.eq(uuid(memberId)))
                .orderBy(SHOP_ORDERS.createdAt.desc())
                .limit(Math.max(1, Math.min(limit, 25)))
                .fetch()
                .stream()
                .map(com.corebuilders.bot.db.DbMappers::shopOrder)
                .toList());
    }

    public ShopOrder complete(UUID orderId, String actorDiscordId, String note) {
        return database.inTransaction(() -> {
            ShopOrder order = lockOrder(orderId);
            if (order.status() != OrderStatus.PENDING) {
                throw new IllegalStateException("Only pending orders can be completed.");
            }
            database.query(q -> {
                var update = q.update(SHOP_ORDERS)
                        .set(SHOP_ORDERS.status, OrderStatus.COMPLETED.name())
                        .set(SHOP_ORDERS.completedByDiscordId, actorDiscordId)
                        .set(SHOP_ORDERS.completedAt, now());
                String safeNote = nullableLimit(note, 1000);
                if (safeNote == null) update.setNull(SHOP_ORDERS.fulfillmentNote);
                else update.set(SHOP_ORDERS.fulfillmentNote, safeNote);
                return update.where(SHOP_ORDERS.id.eq(uuid(orderId))).execute();
            });
            audit.log(actorDiscordId, "ORDER_COMPLETED", order.discordUserId(),
                    "SHOP_ORDER", orderId.toString(), nullToEmpty(note));
            return getOrder(orderId);
        });
    }

    public ShopOrder cancelAndRefund(UUID orderId, String actorDiscordId, String reason) {
        return database.inTransaction(() -> {
            ShopOrder order = lockOrder(orderId);
            if (order.status() != OrderStatus.PENDING) {
                throw new IllegalStateException("Only pending orders can be cancelled.");
            }
            ledger.addCredits(order.memberId(), order.price(), SourceType.REVERSAL,
                    orderId, "Refund for cancelled order: " + order.itemName(), actorDiscordId);
            database.query(q -> {
                var update = q.update(SHOP_ORDERS)
                        .set(SHOP_ORDERS.status, OrderStatus.REFUNDED.name())
                        .set(SHOP_ORDERS.completedByDiscordId, actorDiscordId)
                        .set(SHOP_ORDERS.completedAt, now());
                String safeReason = nullableLimit(reason, 1000);
                if (safeReason == null) update.setNull(SHOP_ORDERS.fulfillmentNote);
                else update.set(SHOP_ORDERS.fulfillmentNote, safeReason);
                return update.where(SHOP_ORDERS.id.eq(uuid(orderId))).execute();
            });
            database.query(q -> q.update(SHOP_ITEMS)
                    .set(SHOP_ITEMS.stock, SHOP_ITEMS.stock.add(1))
                    .where(SHOP_ITEMS.code.eq(order.itemCode()), SHOP_ITEMS.stock.isNotNull())
                    .execute());
            audit.log(actorDiscordId, "ORDER_REFUNDED", order.discordUserId(),
                    "SHOP_ORDER", orderId.toString(), reason);
            return getOrder(orderId);
        });
    }

    private ShopItem lockItem(String code) {
        String locked = database.query(q -> q.select(SHOP_ITEMS.code)
                .from(SHOP_ITEMS)
                .where(SHOP_ITEMS.code.eq(code), SHOP_ITEMS.active.isTrue())
                .forUpdate()
                .fetchOne());
        if (locked == null) throw new IllegalArgumentException("Shop item not found.");
        return database.query(q -> Optional.ofNullable(q.select(shopItemColumns())
                        .from(SHOP_ITEMS)
                        .where(SHOP_ITEMS.code.eq(code))
                        .fetchOne())
                .map(com.corebuilders.bot.db.DbMappers::shopItem)
                .orElseThrow(() -> new IllegalArgumentException("Shop item not found.")));
    }

    private ShopOrder getOrder(UUID id) {
        return database.query(q -> Optional.ofNullable(q.select(shopOrderColumns())
                        .from(SHOP_ORDERS)
                        .join(MEMBERS).on(MEMBERS.id.eq(SHOP_ORDERS.memberId))
                        .join(SHOP_ITEMS).on(SHOP_ITEMS.code.eq(SHOP_ORDERS.itemCode))
                        .where(SHOP_ORDERS.id.eq(uuid(id)))
                        .fetchOne())
                .map(com.corebuilders.bot.db.DbMappers::shopOrder)
                .orElseThrow(() -> new IllegalArgumentException("Order not found.")));
    }

    private ShopOrder lockOrder(UUID id) {
        String locked = database.query(q -> q.select(SHOP_ORDERS.id)
                .from(SHOP_ORDERS)
                .where(SHOP_ORDERS.id.eq(uuid(id)))
                .forUpdate()
                .fetchOne());
        if (locked == null) throw new IllegalArgumentException("Order not found.");
        return getOrder(id);
    }

    private static String normalizeItemCode(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Shop item code is required.");
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private static String nullableLimit(String value, int max) {
        if (value == null || value.isBlank()) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    public record CatalogSyncResult(int inserted, int updated, int disabled) {}
}
