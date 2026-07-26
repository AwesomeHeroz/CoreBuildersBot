package com.corebuilders.bot.service;

import com.corebuilders.bot.db.QueryDslDatabase;
import com.corebuilders.bot.model.Domain.SourceType;
import com.corebuilders.bot.model.MarketplaceModels.*;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.Expression;
import com.querydsl.core.types.OrderSpecifier;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import static com.corebuilders.bot.db.DbValues.instant;
import static com.corebuilders.bot.db.DbValues.now;
import static com.corebuilders.bot.db.DbValues.uuid;
import static com.corebuilders.bot.db.Schema.*;
import static com.corebuilders.bot.service.MarketplaceException.*;
import static com.corebuilders.bot.service.MarketplaceValidation.*;

/**
 * Transactional player-to-player marketplace backed by the existing contribution-points ledger.
 *
 * One shop and one persistent cart are allowed per member. Checkout locks the buyer and every
 * product row in deterministic order, validates the complete cart, writes the order, transfers
 * points, decrements stock, and clears the cart in one database transaction.
 */
public final class MarketplaceService implements MarketplaceOperations {
    private static final String ORDER_PAID = "PAID";
    private static final String ORDER_COMPLETED = "COMPLETED";
    private static final String LINE_PENDING = "PENDING_DELIVERY";
    private static final String LINE_DELIVERED = "DELIVERED";
    private static final int MAX_CART_LINES = 100;

    private final QueryDslDatabase database;
    private final LedgerService ledger;
    private final AuditService audit;

    public MarketplaceService(QueryDslDatabase database, LedgerService ledger, AuditService audit) {
        this.database = Objects.requireNonNull(database, "database");
        this.ledger = Objects.requireNonNull(ledger, "ledger");
        this.audit = Objects.requireNonNull(audit, "audit");
    }

    @Override
    public ItemPage searchItems(ItemSearch request) {
        ItemSearch search = request == null
                ? new ItemSearch(null, null, ItemSort.NEWEST, SortDirection.DESC, 1, 20)
                : request;
        BooleanBuilder filters = publicItemFilters(search.text(), search.category());
        long total = database.query(q -> value(q.select(MARKETPLACE_ITEMS.id.count())
                .from(MARKETPLACE_ITEMS)
                .join(MARKETPLACE_SHOPS).on(MARKETPLACE_SHOPS.id.eq(MARKETPLACE_ITEMS.shopId))
                .where(filters)
                .fetchOne()));

        List<MarketplaceItem> items = database.query(q -> q.select(itemColumns())
                .from(MARKETPLACE_ITEMS)
                .join(MARKETPLACE_SHOPS).on(MARKETPLACE_SHOPS.id.eq(MARKETPLACE_ITEMS.shopId))
                .join(MEMBERS).on(MEMBERS.id.eq(MARKETPLACE_SHOPS.ownerMemberId))
                .where(publicItemFilters(search.text(), search.category()))
                .orderBy(orderFor(search), MARKETPLACE_ITEMS.id.asc())
                .offset((long) (search.page() - 1) * search.pageSize())
                .limit(search.pageSize())
                .fetch()
                .stream()
                .map(MarketplaceService::mapItem)
                .toList());
        return new ItemPage(items, search.page(), search.pageSize(), total);
    }

    @Override
    public Optional<MarketplaceItem> findItem(UUID itemId) {
        Objects.requireNonNull(itemId, "itemId");
        return database.query(q -> Optional.ofNullable(q.select(itemColumns())
                        .from(MARKETPLACE_ITEMS)
                        .join(MARKETPLACE_SHOPS).on(MARKETPLACE_SHOPS.id.eq(MARKETPLACE_ITEMS.shopId))
                        .join(MEMBERS).on(MEMBERS.id.eq(MARKETPLACE_SHOPS.ownerMemberId))
                        .where(MARKETPLACE_ITEMS.id.eq(uuid(itemId)),
                                MARKETPLACE_ITEMS.active.isTrue(), MARKETPLACE_SHOPS.active.isTrue())
                        .fetchOne())
                .map(MarketplaceService::mapItem));
    }

    @Override
    public List<String> categories() {
        return database.query(q -> q.select(MARKETPLACE_ITEMS.category)
                .distinct()
                .from(MARKETPLACE_ITEMS)
                .join(MARKETPLACE_SHOPS).on(MARKETPLACE_SHOPS.id.eq(MARKETPLACE_ITEMS.shopId))
                .where(MARKETPLACE_ITEMS.active.isTrue(), MARKETPLACE_SHOPS.active.isTrue())
                .orderBy(MARKETPLACE_ITEMS.category.asc())
                .fetch());
    }

    @Override
    public Optional<PlayerShop> findShop(UUID shopId) {
        Objects.requireNonNull(shopId, "shopId");
        return database.query(q -> Optional.ofNullable(q.select(shopColumns())
                        .from(MARKETPLACE_SHOPS)
                        .join(MEMBERS).on(MEMBERS.id.eq(MARKETPLACE_SHOPS.ownerMemberId))
                        .where(MARKETPLACE_SHOPS.id.eq(uuid(shopId)), MARKETPLACE_SHOPS.active.isTrue())
                        .fetchOne())
                .map(MarketplaceService::mapShop));
    }

    @Override
    public List<MarketplaceItem> shopItems(UUID shopId) {
        Objects.requireNonNull(shopId, "shopId");
        return database.query(q -> q.select(itemColumns())
                .from(MARKETPLACE_ITEMS)
                .join(MARKETPLACE_SHOPS).on(MARKETPLACE_SHOPS.id.eq(MARKETPLACE_ITEMS.shopId))
                .join(MEMBERS).on(MEMBERS.id.eq(MARKETPLACE_SHOPS.ownerMemberId))
                .where(MARKETPLACE_SHOPS.id.eq(uuid(shopId)), MARKETPLACE_SHOPS.active.isTrue(),
                        MARKETPLACE_ITEMS.active.isTrue())
                .orderBy(MARKETPLACE_ITEMS.name.asc())
                .fetch()
                .stream()
                .map(MarketplaceService::mapItem)
                .toList());
    }

    @Override
    public Optional<PlayerShop> findShopByOwner(UUID ownerMemberId) {
        Objects.requireNonNull(ownerMemberId, "ownerMemberId");
        return database.query(q -> Optional.ofNullable(q.select(shopColumns())
                        .from(MARKETPLACE_SHOPS)
                        .join(MEMBERS).on(MEMBERS.id.eq(MARKETPLACE_SHOPS.ownerMemberId))
                        .where(MARKETPLACE_SHOPS.ownerMemberId.eq(uuid(ownerMemberId)))
                        .fetchOne())
                .map(MarketplaceService::mapShop));
    }

    @Override
    public PlayerShop createShop(UUID ownerMemberId, ShopInput rawInput) {
        Objects.requireNonNull(ownerMemberId, "ownerMemberId");
        ShopInput input = shop(rawInput);
        return database.inTransaction(() -> {
            ledger.lockMember(ownerMemberId);
            if (findShopByOwner(ownerMemberId).isPresent()) {
                throw conflict("Each player can create only one shop.");
            }
            UUID shopId = UUID.randomUUID();
            try {
                database.query(q -> q.insert(MARKETPLACE_SHOPS)
                        .set(MARKETPLACE_SHOPS.id, uuid(shopId))
                        .set(MARKETPLACE_SHOPS.ownerMemberId, uuid(ownerMemberId))
                        .set(MARKETPLACE_SHOPS.name, input.name())
                        .set(MARKETPLACE_SHOPS.description, input.description())
                        .set(MARKETPLACE_SHOPS.active, true)
                        .set(MARKETPLACE_SHOPS.createdAt, now())
                        .set(MARKETPLACE_SHOPS.updatedAt, now())
                        .execute());
            } catch (RuntimeException error) {
                if (database.isDuplicateKey(error)) throw conflict("Each player can create only one shop.");
                throw error;
            }
            String actor = discordForMember(ownerMemberId);
            audit.log(actor, "MARKETPLACE_SHOP_CREATED", actor, "MARKETPLACE_SHOP", shopId.toString(), input.name());
            return requireShop(shopId);
        });
    }

    @Override
    public PlayerShop updateShop(UUID ownerMemberId, ShopInput rawInput) {
        Objects.requireNonNull(ownerMemberId, "ownerMemberId");
        ShopInput input = shop(rawInput);
        return database.inTransaction(() -> {
            PlayerShop existing = requireOwnerShop(ownerMemberId);
            database.query(q -> q.update(MARKETPLACE_SHOPS)
                    .set(MARKETPLACE_SHOPS.name, input.name())
                    .set(MARKETPLACE_SHOPS.description, input.description())
                    .set(MARKETPLACE_SHOPS.updatedAt, now())
                    .where(MARKETPLACE_SHOPS.id.eq(uuid(existing.id())))
                    .execute());
            audit.log(existing.ownerDiscordId(), "MARKETPLACE_SHOP_UPDATED", existing.ownerDiscordId(),
                    "MARKETPLACE_SHOP", existing.id().toString(), input.name());
            return requireShop(existing.id());
        });
    }

    @Override
    public List<MarketplaceItem> ownerItems(UUID ownerMemberId) {
        Objects.requireNonNull(ownerMemberId, "ownerMemberId");
        return database.query(q -> q.select(itemColumns())
                .from(MARKETPLACE_ITEMS)
                .join(MARKETPLACE_SHOPS).on(MARKETPLACE_SHOPS.id.eq(MARKETPLACE_ITEMS.shopId))
                .join(MEMBERS).on(MEMBERS.id.eq(MARKETPLACE_SHOPS.ownerMemberId))
                .where(MARKETPLACE_SHOPS.ownerMemberId.eq(uuid(ownerMemberId)))
                .orderBy(MARKETPLACE_ITEMS.updatedAt.desc(), MARKETPLACE_ITEMS.name.asc())
                .fetch()
                .stream()
                .map(MarketplaceService::mapItem)
                .toList());
    }

    @Override
    public MarketplaceItem createItem(UUID ownerMemberId, ItemInput rawInput) {
        Objects.requireNonNull(ownerMemberId, "ownerMemberId");
        ItemInput input = item(rawInput);
        return database.inTransaction(() -> {
            PlayerShop shop = requireOwnerShop(ownerMemberId);
            UUID itemId = UUID.randomUUID();
            database.query(q -> {
                var insert = q.insert(MARKETPLACE_ITEMS)
                        .set(MARKETPLACE_ITEMS.id, uuid(itemId))
                        .set(MARKETPLACE_ITEMS.shopId, uuid(shop.id()))
                        .set(MARKETPLACE_ITEMS.name, input.name())
                        .set(MARKETPLACE_ITEMS.description, input.description())
                        .set(MARKETPLACE_ITEMS.stock, input.stock())
                        .set(MARKETPLACE_ITEMS.price, input.price())
                        .set(MARKETPLACE_ITEMS.category, input.category())
                        .set(MARKETPLACE_ITEMS.active, input.active())
                        .set(MARKETPLACE_ITEMS.createdAt, now())
                        .set(MARKETPLACE_ITEMS.updatedAt, now());
                if (input.imageUrl() == null) insert.setNull(MARKETPLACE_ITEMS.imageUrl);
                else insert.set(MARKETPLACE_ITEMS.imageUrl, input.imageUrl());
                return insert.execute();
            });
            audit.log(shop.ownerDiscordId(), "MARKETPLACE_ITEM_CREATED", shop.ownerDiscordId(),
                    "MARKETPLACE_ITEM", itemId.toString(), input.name());
            return requireItem(itemId);
        });
    }

    @Override
    public MarketplaceItem updateItem(UUID ownerMemberId, UUID itemId, ItemInput rawInput) {
        Objects.requireNonNull(ownerMemberId, "ownerMemberId");
        Objects.requireNonNull(itemId, "itemId");
        ItemInput input = item(rawInput);
        return database.inTransaction(() -> {
            MarketplaceItem existing = lockOwnedItem(ownerMemberId, itemId);
            database.query(q -> {
                var update = q.update(MARKETPLACE_ITEMS)
                        .set(MARKETPLACE_ITEMS.name, input.name())
                        .set(MARKETPLACE_ITEMS.description, input.description())
                        .set(MARKETPLACE_ITEMS.stock, input.stock())
                        .set(MARKETPLACE_ITEMS.price, input.price())
                        .set(MARKETPLACE_ITEMS.category, input.category())
                        .set(MARKETPLACE_ITEMS.active, input.active())
                        .set(MARKETPLACE_ITEMS.updatedAt, now());
                if (input.imageUrl() == null) update.setNull(MARKETPLACE_ITEMS.imageUrl);
                else update.set(MARKETPLACE_ITEMS.imageUrl, input.imageUrl());
                return update.where(MARKETPLACE_ITEMS.id.eq(uuid(itemId))).execute();
            });
            audit.log(existing.sellerDiscordId(), "MARKETPLACE_ITEM_UPDATED", existing.sellerDiscordId(),
                    "MARKETPLACE_ITEM", itemId.toString(), input.name());
            return requireItem(itemId);
        });
    }

    @Override
    public void deactivateItem(UUID ownerMemberId, UUID itemId) {
        Objects.requireNonNull(ownerMemberId, "ownerMemberId");
        Objects.requireNonNull(itemId, "itemId");
        database.inTransaction(() -> {
            MarketplaceItem existing = lockOwnedItem(ownerMemberId, itemId);
            database.query(q -> q.update(MARKETPLACE_ITEMS)
                    .set(MARKETPLACE_ITEMS.active, false)
                    .set(MARKETPLACE_ITEMS.updatedAt, now())
                    .where(MARKETPLACE_ITEMS.id.eq(uuid(itemId)))
                    .execute());
            database.query(q -> q.delete(MARKETPLACE_CART_ITEMS)
                    .where(MARKETPLACE_CART_ITEMS.itemId.eq(uuid(itemId)))
                    .execute());
            audit.log(existing.sellerDiscordId(), "MARKETPLACE_ITEM_DEACTIVATED", existing.sellerDiscordId(),
                    "MARKETPLACE_ITEM", itemId.toString(), existing.name());
        });
    }

    @Override
    public MarketplaceCart cart(UUID memberId) {
        Objects.requireNonNull(memberId, "memberId");
        UUID cartId = database.inTransaction(() -> ensureCart(memberId));
        return loadCart(cartId, memberId);
    }

    @Override
    public MarketplaceCart setCartQuantity(UUID memberId, UUID itemId, int rawQuantity) {
        Objects.requireNonNull(memberId, "memberId");
        Objects.requireNonNull(itemId, "itemId");
        int quantity = quantity(rawQuantity);
        return database.inTransaction(() -> {
            UUID cartId = ensureCart(memberId);
            lockCart(cartId);
            MarketplaceItem product = lockAvailableItem(itemId);
            if (product.sellerMemberId().equals(memberId)) {
                throw forbidden("You cannot buy items from your own shop.");
            }
            if (product.stock() < quantity) {
                throw new MarketplaceException(MarketplaceException.Code.OUT_OF_STOCK,
                        "Only " + product.stock() + " item(s) are currently in stock.");
            }
            Integer existing = database.query(q -> q.select(MARKETPLACE_CART_ITEMS.quantity)
                    .from(MARKETPLACE_CART_ITEMS)
                    .where(MARKETPLACE_CART_ITEMS.cartId.eq(uuid(cartId)),
                            MARKETPLACE_CART_ITEMS.itemId.eq(uuid(itemId)))
                    .fetchOne());
            if (existing == null) {
                long cartLines = value(database.query(q -> q.select(MARKETPLACE_CART_ITEMS.itemId.count())
                        .from(MARKETPLACE_CART_ITEMS)
                        .where(MARKETPLACE_CART_ITEMS.cartId.eq(uuid(cartId)))
                        .fetchOne()));
                if (cartLines >= MAX_CART_LINES) {
                    throw conflict("A cart can contain at most " + MAX_CART_LINES + " different listings.");
                }
                database.query(q -> q.insert(MARKETPLACE_CART_ITEMS)
                        .set(MARKETPLACE_CART_ITEMS.cartId, uuid(cartId))
                        .set(MARKETPLACE_CART_ITEMS.itemId, uuid(itemId))
                        .set(MARKETPLACE_CART_ITEMS.quantity, quantity)
                        .set(MARKETPLACE_CART_ITEMS.createdAt, now())
                        .set(MARKETPLACE_CART_ITEMS.updatedAt, now())
                        .execute());
            } else {
                database.query(q -> q.update(MARKETPLACE_CART_ITEMS)
                        .set(MARKETPLACE_CART_ITEMS.quantity, quantity)
                        .set(MARKETPLACE_CART_ITEMS.updatedAt, now())
                        .where(MARKETPLACE_CART_ITEMS.cartId.eq(uuid(cartId)),
                                MARKETPLACE_CART_ITEMS.itemId.eq(uuid(itemId)))
                        .execute());
            }
            touchCart(cartId);
            return loadCart(cartId, memberId);
        });
    }

    @Override
    public MarketplaceCart removeCartItem(UUID memberId, UUID itemId) {
        Objects.requireNonNull(memberId, "memberId");
        Objects.requireNonNull(itemId, "itemId");
        return database.inTransaction(() -> {
            UUID cartId = ensureCart(memberId);
            lockCart(cartId);
            database.query(q -> q.delete(MARKETPLACE_CART_ITEMS)
                    .where(MARKETPLACE_CART_ITEMS.cartId.eq(uuid(cartId)),
                            MARKETPLACE_CART_ITEMS.itemId.eq(uuid(itemId)))
                    .execute());
            touchCart(cartId);
            return loadCart(cartId, memberId);
        });
    }

    @Override
    public MarketplaceOrder checkout(UUID buyerMemberId, String actorDiscordId) {
        Objects.requireNonNull(buyerMemberId, "buyerMemberId");
        if (actorDiscordId == null || actorDiscordId.isBlank()) throw validation("Buyer Discord ID is required.");
        return database.inTransaction(() -> {
            UUID cartId = ensureCart(buyerMemberId);
            lockCart(cartId);

            List<Tuple> cartRows = database.query(q -> q.select(
                            MARKETPLACE_CART_ITEMS.itemId, MARKETPLACE_CART_ITEMS.quantity)
                    .from(MARKETPLACE_CART_ITEMS)
                    .where(MARKETPLACE_CART_ITEMS.cartId.eq(uuid(cartId)))
                    .fetch());
            if (cartRows.isEmpty()) throw conflict("Your cart is empty.");
            if (cartRows.size() > MAX_CART_LINES) {
                throw conflict("A cart can contain at most " + MAX_CART_LINES + " different listings.");
            }

            List<CartSelection> selections = cartRows.stream()
                    .map(row -> new CartSelection(
                            UUID.fromString(row.get(MARKETPLACE_CART_ITEMS.itemId)),
                            row.get(MARKETPLACE_CART_ITEMS.quantity) == null ? 0 : row.get(MARKETPLACE_CART_ITEMS.quantity)))
                    .sorted(Comparator.comparing(selection -> selection.itemId().toString()))
                    .toList();

            List<LockedPurchase> purchases = new ArrayList<>();
            long total = 0L;
            for (CartSelection selection : selections) {
                int selectedQuantity = quantity(selection.quantity());
                MarketplaceItem product = lockAvailableItem(selection.itemId());
                if (product.sellerMemberId().equals(buyerMemberId)) {
                    throw forbidden("Your cart contains an item from your own shop.");
                }
                if (product.stock() < selectedQuantity) {
                    throw new MarketplaceException(MarketplaceException.Code.OUT_OF_STOCK,
                            product.name() + " has only " + product.stock() + " item(s) left.");
                }
                long lineTotal = multiply(product.price(), selectedQuantity);
                total = add(total, lineTotal);
                purchases.add(new LockedPurchase(product, selectedQuantity, lineTotal));
            }

            Set<UUID> participants = new TreeSet<>();
            participants.add(buyerMemberId);
            purchases.forEach(purchase -> participants.add(purchase.item().sellerMemberId()));
            participants.forEach(ledger::lockMember);

            long balance = ledger.creditBalance(buyerMemberId);
            if (balance < total) {
                throw new MarketplaceException(MarketplaceException.Code.INSUFFICIENT_FUNDS,
                        "You need " + total + " contribution points but have " + balance + ".");
            }

            long finalTotal = total;
            UUID orderId = UUID.randomUUID();
            database.query(q -> q.insert(MARKETPLACE_ORDERS)
                    .set(MARKETPLACE_ORDERS.id, uuid(orderId))
                    .set(MARKETPLACE_ORDERS.buyerMemberId, uuid(buyerMemberId))
                    .set(MARKETPLACE_ORDERS.totalPrice, finalTotal)
                    .set(MARKETPLACE_ORDERS.status, ORDER_PAID)
                    .set(MARKETPLACE_ORDERS.createdAt, now())
                    .execute());

            ledger.addCredits(buyerMemberId, -finalTotal, SourceType.MARKETPLACE_PURCHASE,
                    orderId, "Player marketplace order", actorDiscordId);

            Map<UUID, Long> sellerTotals = new LinkedHashMap<>();
            for (LockedPurchase purchase : purchases) {
                MarketplaceItem product = purchase.item();
                UUID lineId = UUID.randomUUID();
                database.query(q -> {
                    var insert = q.insert(MARKETPLACE_ORDER_ITEMS)
                            .set(MARKETPLACE_ORDER_ITEMS.id, uuid(lineId))
                            .set(MARKETPLACE_ORDER_ITEMS.orderId, uuid(orderId))
                            .set(MARKETPLACE_ORDER_ITEMS.itemId, uuid(product.id()))
                            .set(MARKETPLACE_ORDER_ITEMS.shopId, uuid(product.shopId()))
                            .set(MARKETPLACE_ORDER_ITEMS.sellerMemberId, uuid(product.sellerMemberId()))
                            .set(MARKETPLACE_ORDER_ITEMS.shopName, product.shopName())
                            .set(MARKETPLACE_ORDER_ITEMS.itemName, product.name())
                            .set(MARKETPLACE_ORDER_ITEMS.category, product.category())
                            .set(MARKETPLACE_ORDER_ITEMS.quantity, purchase.quantity())
                            .set(MARKETPLACE_ORDER_ITEMS.unitPrice, product.price())
                            .set(MARKETPLACE_ORDER_ITEMS.lineTotal, purchase.lineTotal())
                            .set(MARKETPLACE_ORDER_ITEMS.status, LINE_PENDING)
                            .set(MARKETPLACE_ORDER_ITEMS.createdAt, now());
                    if (product.imageUrl() == null) insert.setNull(MARKETPLACE_ORDER_ITEMS.imageUrl);
                    else insert.set(MARKETPLACE_ORDER_ITEMS.imageUrl, product.imageUrl());
                    return insert.execute();
                });
                database.query(q -> q.update(MARKETPLACE_ITEMS)
                        .set(MARKETPLACE_ITEMS.stock, MARKETPLACE_ITEMS.stock.subtract(purchase.quantity()))
                        .set(MARKETPLACE_ITEMS.updatedAt, now())
                        .where(MARKETPLACE_ITEMS.id.eq(uuid(product.id())))
                        .execute());
                sellerTotals.merge(product.sellerMemberId(), purchase.lineTotal(), MarketplaceService::add);
            }

            sellerTotals.forEach((sellerId, amount) -> ledger.addCredits(
                    sellerId, amount, SourceType.MARKETPLACE_SALE, orderId,
                    "Player marketplace sale", actorDiscordId));

            database.query(q -> q.delete(MARKETPLACE_CART_ITEMS)
                    .where(MARKETPLACE_CART_ITEMS.cartId.eq(uuid(cartId)))
                    .execute());
            touchCart(cartId);
            audit.log(actorDiscordId, "MARKETPLACE_CHECKOUT", actorDiscordId,
                    "MARKETPLACE_ORDER", orderId.toString(), purchases.size() + " line(s), " + finalTotal + " points");
            return requireOrder(orderId);
        });
    }

    @Override
    public List<MarketplaceOrder> purchases(UUID buyerMemberId, int rawLimit) {
        Objects.requireNonNull(buyerMemberId, "buyerMemberId");
        int safeLimit = limit(rawLimit);
        List<String> ids = database.query(q -> q.select(MARKETPLACE_ORDERS.id)
                .from(MARKETPLACE_ORDERS)
                .where(MARKETPLACE_ORDERS.buyerMemberId.eq(uuid(buyerMemberId)))
                .orderBy(MARKETPLACE_ORDERS.createdAt.desc())
                .limit(safeLimit)
                .fetch());
        return ids.stream().map(UUID::fromString).map(this::requireOrder).toList();
    }

    @Override
    public List<MarketplaceOrderLine> sales(UUID sellerMemberId, int rawLimit) {
        Objects.requireNonNull(sellerMemberId, "sellerMemberId");
        return database.query(q -> q.select(orderLineColumns())
                .from(MARKETPLACE_ORDER_ITEMS)
                .join(MARKETPLACE_ORDERS).on(MARKETPLACE_ORDERS.id.eq(MARKETPLACE_ORDER_ITEMS.orderId))
                .join(MEMBERS).on(MEMBERS.id.eq(MARKETPLACE_ORDERS.buyerMemberId))
                .where(MARKETPLACE_ORDER_ITEMS.sellerMemberId.eq(uuid(sellerMemberId)))
                .orderBy(MARKETPLACE_ORDER_ITEMS.createdAt.desc())
                .limit(limit(rawLimit))
                .fetch()
                .stream()
                .map(MarketplaceService::mapOrderLine)
                .toList());
    }

    @Override
    public MarketplaceOrderLine markDelivered(UUID sellerMemberId, UUID lineId) {
        Objects.requireNonNull(sellerMemberId, "sellerMemberId");
        Objects.requireNonNull(lineId, "lineId");
        return database.inTransaction(() -> {
            String orderValue = database.query(q -> q.select(MARKETPLACE_ORDER_ITEMS.orderId)
                    .from(MARKETPLACE_ORDER_ITEMS)
                    .where(MARKETPLACE_ORDER_ITEMS.id.eq(uuid(lineId)))
                    .fetchOne());
            if (orderValue == null) throw notFound("Sale line not found.");
            UUID orderId = UUID.fromString(orderValue);

            String lockedOrder = database.query(q -> q.select(MARKETPLACE_ORDERS.id)
                    .from(MARKETPLACE_ORDERS)
                    .where(MARKETPLACE_ORDERS.id.eq(uuid(orderId)))
                    .forUpdate()
                    .fetchOne());
            if (lockedOrder == null) throw notFound("Marketplace order not found.");

            Tuple lockedLine = database.query(q -> q.select(orderLineColumns())
                    .from(MARKETPLACE_ORDER_ITEMS)
                    .join(MARKETPLACE_ORDERS).on(MARKETPLACE_ORDERS.id.eq(MARKETPLACE_ORDER_ITEMS.orderId))
                    .join(MEMBERS).on(MEMBERS.id.eq(MARKETPLACE_ORDERS.buyerMemberId))
                    .where(MARKETPLACE_ORDER_ITEMS.id.eq(uuid(lineId)))
                    .forUpdate()
                    .fetchOne());
            if (lockedLine == null) throw notFound("Sale line not found.");
            MarketplaceOrderLine line = mapOrderLine(lockedLine);
            if (!line.sellerMemberId().equals(sellerMemberId)) {
                throw forbidden("You can mark only your own sales as delivered.");
            }
            if (!LINE_PENDING.equals(line.status())) {
                throw conflict("This sale is already marked as delivered.");
            }
            database.query(q -> q.update(MARKETPLACE_ORDER_ITEMS)
                    .set(MARKETPLACE_ORDER_ITEMS.status, LINE_DELIVERED)
                    .set(MARKETPLACE_ORDER_ITEMS.deliveredAt, now())
                    .where(MARKETPLACE_ORDER_ITEMS.id.eq(uuid(lineId)))
                    .execute());

            List<String> pendingLineIds = database.query(q -> q.select(MARKETPLACE_ORDER_ITEMS.id)
                    .from(MARKETPLACE_ORDER_ITEMS)
                    .where(MARKETPLACE_ORDER_ITEMS.orderId.eq(uuid(orderId)),
                            MARKETPLACE_ORDER_ITEMS.status.eq(LINE_PENDING))
                    .forUpdate()
                    .fetch());
            if (pendingLineIds.isEmpty()) {
                database.query(q -> q.update(MARKETPLACE_ORDERS)
                        .set(MARKETPLACE_ORDERS.status, ORDER_COMPLETED)
                        .set(MARKETPLACE_ORDERS.completedAt, now())
                        .where(MARKETPLACE_ORDERS.id.eq(uuid(orderId)))
                        .execute());
            }
            String actor = discordForMember(sellerMemberId);
            audit.log(actor, "MARKETPLACE_SALE_DELIVERED", discordForMember(line.buyerMemberId()),
                    "MARKETPLACE_ORDER_ITEM", lineId.toString(), line.itemName());
            return requireOrderLine(lineId);
        });
    }

    private UUID ensureCart(UUID memberId) {
        String existing = database.query(q -> q.select(MARKETPLACE_CARTS.id)
                .from(MARKETPLACE_CARTS)
                .where(MARKETPLACE_CARTS.memberId.eq(uuid(memberId)))
                .fetchOne());
        if (existing != null) return UUID.fromString(existing);
        UUID id = UUID.randomUUID();
        try {
            database.query(q -> q.insert(MARKETPLACE_CARTS)
                    .set(MARKETPLACE_CARTS.id, uuid(id))
                    .set(MARKETPLACE_CARTS.memberId, uuid(memberId))
                    .set(MARKETPLACE_CARTS.createdAt, now())
                    .set(MARKETPLACE_CARTS.updatedAt, now())
                    .execute());
            return id;
        } catch (RuntimeException error) {
            if (!database.isDuplicateKey(error)) throw error;
            String concurrent = database.query(q -> q.select(MARKETPLACE_CARTS.id)
                    .from(MARKETPLACE_CARTS)
                    .where(MARKETPLACE_CARTS.memberId.eq(uuid(memberId)))
                    .fetchOne());
            if (concurrent == null) throw error;
            return UUID.fromString(concurrent);
        }
    }

    private void lockCart(UUID cartId) {
        String found = database.query(q -> q.select(MARKETPLACE_CARTS.id)
                .from(MARKETPLACE_CARTS)
                .where(MARKETPLACE_CARTS.id.eq(uuid(cartId)))
                .forUpdate()
                .fetchOne());
        if (found == null) throw notFound("Cart not found.");
    }

    private void touchCart(UUID cartId) {
        database.query(q -> q.update(MARKETPLACE_CARTS)
                .set(MARKETPLACE_CARTS.updatedAt, now())
                .where(MARKETPLACE_CARTS.id.eq(uuid(cartId)))
                .execute());
    }

    private MarketplaceCart loadCart(UUID cartId, UUID memberId) {
        Instant updated = database.query(q -> instant(q.select(MARKETPLACE_CARTS.updatedAt)
                .from(MARKETPLACE_CARTS)
                .where(MARKETPLACE_CARTS.id.eq(uuid(cartId)), MARKETPLACE_CARTS.memberId.eq(uuid(memberId)))
                .fetchOne()));
        if (updated == null) throw notFound("Cart not found.");
        List<Tuple> rows = database.query(q -> q.select(itemColumnsWithQuantity())
                .from(MARKETPLACE_CART_ITEMS)
                .join(MARKETPLACE_ITEMS).on(MARKETPLACE_ITEMS.id.eq(MARKETPLACE_CART_ITEMS.itemId))
                .join(MARKETPLACE_SHOPS).on(MARKETPLACE_SHOPS.id.eq(MARKETPLACE_ITEMS.shopId))
                .join(MEMBERS).on(MEMBERS.id.eq(MARKETPLACE_SHOPS.ownerMemberId))
                .where(MARKETPLACE_CART_ITEMS.cartId.eq(uuid(cartId)))
                .orderBy(MARKETPLACE_CART_ITEMS.createdAt.asc())
                .fetch());
        List<CartLine> lines = new ArrayList<>();
        long total = 0L;
        int itemCount = 0;
        for (Tuple row : rows) {
            MarketplaceItem product = mapItem(row);
            int selected = row.get(MARKETPLACE_CART_ITEMS.quantity) == null ? 0 : row.get(MARKETPLACE_CART_ITEMS.quantity);
            long lineTotal = multiply(product.price(), selected);
            total = add(total, lineTotal);
            itemCount = Math.addExact(itemCount, selected);
            lines.add(new CartLine(product, selected, lineTotal));
        }
        return new MarketplaceCart(cartId, memberId, lines, total, itemCount, updated);
    }

    private PlayerShop requireOwnerShop(UUID ownerMemberId) {
        return findShopByOwner(ownerMemberId).orElseThrow(() -> notFound("Create your shop before listing items."));
    }

    private PlayerShop requireShop(UUID shopId) {
        return database.query(q -> Optional.ofNullable(q.select(shopColumns())
                        .from(MARKETPLACE_SHOPS)
                        .join(MEMBERS).on(MEMBERS.id.eq(MARKETPLACE_SHOPS.ownerMemberId))
                        .where(MARKETPLACE_SHOPS.id.eq(uuid(shopId)))
                        .fetchOne())
                .map(MarketplaceService::mapShop)
                .orElseThrow(() -> notFound("Shop not found.")));
    }

    private MarketplaceItem requireItem(UUID itemId) {
        return loadItem(itemId).orElseThrow(() -> notFound("Marketplace item not found."));
    }

    private Optional<MarketplaceItem> loadItem(UUID itemId) {
        return database.query(q -> Optional.ofNullable(q.select(itemColumns())
                        .from(MARKETPLACE_ITEMS)
                        .join(MARKETPLACE_SHOPS).on(MARKETPLACE_SHOPS.id.eq(MARKETPLACE_ITEMS.shopId))
                        .join(MEMBERS).on(MEMBERS.id.eq(MARKETPLACE_SHOPS.ownerMemberId))
                        .where(MARKETPLACE_ITEMS.id.eq(uuid(itemId)))
                        .fetchOne())
                .map(MarketplaceService::mapItem));
    }

    private MarketplaceItem lockOwnedItem(UUID ownerMemberId, UUID itemId) {
        String locked = database.query(q -> q.select(MARKETPLACE_ITEMS.id)
                .from(MARKETPLACE_ITEMS)
                .join(MARKETPLACE_SHOPS).on(MARKETPLACE_SHOPS.id.eq(MARKETPLACE_ITEMS.shopId))
                .where(MARKETPLACE_ITEMS.id.eq(uuid(itemId)),
                        MARKETPLACE_SHOPS.ownerMemberId.eq(uuid(ownerMemberId)))
                .forUpdate()
                .fetchOne());
        if (locked == null) throw notFound("Marketplace item not found in your shop.");
        return requireItem(itemId);
    }

    private MarketplaceItem lockAvailableItem(UUID itemId) {
        String locked = database.query(q -> q.select(MARKETPLACE_ITEMS.id)
                .from(MARKETPLACE_ITEMS)
                .join(MARKETPLACE_SHOPS).on(MARKETPLACE_SHOPS.id.eq(MARKETPLACE_ITEMS.shopId))
                .where(MARKETPLACE_ITEMS.id.eq(uuid(itemId)),
                        MARKETPLACE_ITEMS.active.isTrue(), MARKETPLACE_SHOPS.active.isTrue())
                .forUpdate()
                .fetchOne());
        if (locked == null) throw notFound("Marketplace item is unavailable.");
        MarketplaceItem item = requireItem(itemId);
        if (item.stock() <= 0) {
            throw new MarketplaceException(MarketplaceException.Code.OUT_OF_STOCK, "This item is out of stock.");
        }
        return item;
    }

    private MarketplaceOrder requireOrder(UUID orderId) {
        Tuple order = database.query(q -> q.select(MARKETPLACE_ORDERS.id, MARKETPLACE_ORDERS.buyerMemberId,
                        MARKETPLACE_ORDERS.totalPrice, MARKETPLACE_ORDERS.status,
                        MARKETPLACE_ORDERS.createdAt, MARKETPLACE_ORDERS.completedAt)
                .from(MARKETPLACE_ORDERS)
                .where(MARKETPLACE_ORDERS.id.eq(uuid(orderId)))
                .fetchOne());
        if (order == null) throw notFound("Marketplace order not found.");
        List<MarketplaceOrderLine> lines = database.query(q -> q.select(orderLineColumns())
                .from(MARKETPLACE_ORDER_ITEMS)
                .join(MARKETPLACE_ORDERS).on(MARKETPLACE_ORDERS.id.eq(MARKETPLACE_ORDER_ITEMS.orderId))
                .join(MEMBERS).on(MEMBERS.id.eq(MARKETPLACE_ORDERS.buyerMemberId))
                .where(MARKETPLACE_ORDER_ITEMS.orderId.eq(uuid(orderId)))
                .orderBy(MARKETPLACE_ORDER_ITEMS.createdAt.asc())
                .fetch()
                .stream()
                .map(MarketplaceService::mapOrderLine)
                .toList());
        return new MarketplaceOrder(
                UUID.fromString(order.get(MARKETPLACE_ORDERS.id)),
                UUID.fromString(order.get(MARKETPLACE_ORDERS.buyerMemberId)),
                value(order.get(MARKETPLACE_ORDERS.totalPrice)),
                order.get(MARKETPLACE_ORDERS.status),
                lines,
                instant(order.get(MARKETPLACE_ORDERS.createdAt)),
                instant(order.get(MARKETPLACE_ORDERS.completedAt))
        );
    }

    private MarketplaceOrderLine requireOrderLine(UUID lineId) {
        return database.query(q -> Optional.ofNullable(q.select(orderLineColumns())
                        .from(MARKETPLACE_ORDER_ITEMS)
                        .join(MARKETPLACE_ORDERS).on(MARKETPLACE_ORDERS.id.eq(MARKETPLACE_ORDER_ITEMS.orderId))
                        .join(MEMBERS).on(MEMBERS.id.eq(MARKETPLACE_ORDERS.buyerMemberId))
                        .where(MARKETPLACE_ORDER_ITEMS.id.eq(uuid(lineId)))
                        .fetchOne())
                .map(MarketplaceService::mapOrderLine)
                .orElseThrow(() -> notFound("Sale line not found.")));
    }

    private String discordForMember(UUID memberId) {
        String discordId = database.query(q -> q.select(MEMBERS.discordUserId)
                .from(MEMBERS)
                .where(MEMBERS.id.eq(uuid(memberId)))
                .fetchOne());
        if (discordId == null) throw notFound("Member profile not found.");
        return discordId;
    }

    private static BooleanBuilder publicItemFilters(String text, String category) {
        BooleanBuilder filters = new BooleanBuilder()
                .and(MARKETPLACE_ITEMS.active.isTrue())
                .and(MARKETPLACE_SHOPS.active.isTrue());
        if (text != null && !text.isBlank()) {
            String query = text.trim();
            filters.and(MARKETPLACE_ITEMS.name.containsIgnoreCase(query)
                    .or(MARKETPLACE_ITEMS.description.containsIgnoreCase(query)));
        }
        if (category != null && !category.isBlank()) {
            filters.and(MARKETPLACE_ITEMS.category.equalsIgnoreCase(category.trim()));
        }
        return filters;
    }

    private static OrderSpecifier<?> orderFor(ItemSearch search) {
        boolean ascending = search.direction() == SortDirection.ASC;
        return switch (search.sort()) {
            case PRICE -> ascending ? MARKETPLACE_ITEMS.price.asc() : MARKETPLACE_ITEMS.price.desc();
            case NAME -> ascending ? MARKETPLACE_ITEMS.name.asc() : MARKETPLACE_ITEMS.name.desc();
            case STOCK -> ascending ? MARKETPLACE_ITEMS.stock.asc() : MARKETPLACE_ITEMS.stock.desc();
            case NEWEST -> ascending ? MARKETPLACE_ITEMS.createdAt.asc() : MARKETPLACE_ITEMS.createdAt.desc();
        };
    }

    private static Expression<?>[] shopColumns() {
        return new Expression<?>[]{MARKETPLACE_SHOPS.id, MARKETPLACE_SHOPS.ownerMemberId,
                MEMBERS.discordUserId, MEMBERS.username, MARKETPLACE_SHOPS.name,
                MARKETPLACE_SHOPS.description, MARKETPLACE_SHOPS.active,
                MARKETPLACE_SHOPS.createdAt, MARKETPLACE_SHOPS.updatedAt};
    }

    private static PlayerShop mapShop(Tuple row) {
        return new PlayerShop(
                UUID.fromString(row.get(MARKETPLACE_SHOPS.id)),
                UUID.fromString(row.get(MARKETPLACE_SHOPS.ownerMemberId)),
                row.get(MEMBERS.discordUserId),
                row.get(MEMBERS.username),
                row.get(MARKETPLACE_SHOPS.name),
                row.get(MARKETPLACE_SHOPS.description),
                Boolean.TRUE.equals(row.get(MARKETPLACE_SHOPS.active)),
                instant(row.get(MARKETPLACE_SHOPS.createdAt)),
                instant(row.get(MARKETPLACE_SHOPS.updatedAt))
        );
    }

    private static Expression<?>[] itemColumns() {
        return new Expression<?>[]{MARKETPLACE_ITEMS.id, MARKETPLACE_ITEMS.shopId,
                MARKETPLACE_SHOPS.name, MARKETPLACE_SHOPS.ownerMemberId,
                MEMBERS.discordUserId, MEMBERS.username, MARKETPLACE_ITEMS.name,
                MARKETPLACE_ITEMS.description, MARKETPLACE_ITEMS.imageUrl,
                MARKETPLACE_ITEMS.stock, MARKETPLACE_ITEMS.price, MARKETPLACE_ITEMS.category,
                MARKETPLACE_ITEMS.active, MARKETPLACE_ITEMS.createdAt, MARKETPLACE_ITEMS.updatedAt};
    }

    private static Expression<?>[] itemColumnsWithQuantity() {
        Expression<?>[] base = itemColumns();
        Expression<?>[] extended = new Expression<?>[base.length + 1];
        System.arraycopy(base, 0, extended, 0, base.length);
        extended[base.length] = MARKETPLACE_CART_ITEMS.quantity;
        return extended;
    }

    private static MarketplaceItem mapItem(Tuple row) {
        Integer stock = row.get(MARKETPLACE_ITEMS.stock);
        return new MarketplaceItem(
                UUID.fromString(row.get(MARKETPLACE_ITEMS.id)),
                UUID.fromString(row.get(MARKETPLACE_ITEMS.shopId)),
                row.get(MARKETPLACE_SHOPS.name),
                UUID.fromString(row.get(MARKETPLACE_SHOPS.ownerMemberId)),
                row.get(MEMBERS.discordUserId),
                row.get(MEMBERS.username),
                row.get(MARKETPLACE_ITEMS.name),
                row.get(MARKETPLACE_ITEMS.description),
                row.get(MARKETPLACE_ITEMS.imageUrl),
                stock == null ? 0 : stock,
                value(row.get(MARKETPLACE_ITEMS.price)),
                row.get(MARKETPLACE_ITEMS.category),
                Boolean.TRUE.equals(row.get(MARKETPLACE_ITEMS.active)),
                instant(row.get(MARKETPLACE_ITEMS.createdAt)),
                instant(row.get(MARKETPLACE_ITEMS.updatedAt))
        );
    }

    private static Expression<?>[] orderLineColumns() {
        return new Expression<?>[]{MARKETPLACE_ORDER_ITEMS.id, MARKETPLACE_ORDER_ITEMS.orderId,
                MARKETPLACE_ORDER_ITEMS.itemId, MARKETPLACE_ORDER_ITEMS.shopId,
                MARKETPLACE_ORDER_ITEMS.sellerMemberId, MARKETPLACE_ORDERS.buyerMemberId,
                MEMBERS.username, MARKETPLACE_ORDER_ITEMS.shopName, MARKETPLACE_ORDER_ITEMS.itemName,
                MARKETPLACE_ORDER_ITEMS.imageUrl, MARKETPLACE_ORDER_ITEMS.category,
                MARKETPLACE_ORDER_ITEMS.quantity, MARKETPLACE_ORDER_ITEMS.unitPrice,
                MARKETPLACE_ORDER_ITEMS.lineTotal, MARKETPLACE_ORDER_ITEMS.status,
                MARKETPLACE_ORDER_ITEMS.createdAt, MARKETPLACE_ORDER_ITEMS.deliveredAt};
    }

    private static MarketplaceOrderLine mapOrderLine(Tuple row) {
        return new MarketplaceOrderLine(
                UUID.fromString(row.get(MARKETPLACE_ORDER_ITEMS.id)),
                UUID.fromString(row.get(MARKETPLACE_ORDER_ITEMS.orderId)),
                UUID.fromString(row.get(MARKETPLACE_ORDER_ITEMS.itemId)),
                UUID.fromString(row.get(MARKETPLACE_ORDER_ITEMS.shopId)),
                UUID.fromString(row.get(MARKETPLACE_ORDER_ITEMS.sellerMemberId)),
                UUID.fromString(row.get(MARKETPLACE_ORDERS.buyerMemberId)),
                row.get(MEMBERS.username),
                row.get(MARKETPLACE_ORDER_ITEMS.shopName),
                row.get(MARKETPLACE_ORDER_ITEMS.itemName),
                row.get(MARKETPLACE_ORDER_ITEMS.imageUrl),
                row.get(MARKETPLACE_ORDER_ITEMS.category),
                row.get(MARKETPLACE_ORDER_ITEMS.quantity) == null ? 0 : row.get(MARKETPLACE_ORDER_ITEMS.quantity),
                value(row.get(MARKETPLACE_ORDER_ITEMS.unitPrice)),
                value(row.get(MARKETPLACE_ORDER_ITEMS.lineTotal)),
                row.get(MARKETPLACE_ORDER_ITEMS.status),
                instant(row.get(MARKETPLACE_ORDER_ITEMS.createdAt)),
                instant(row.get(MARKETPLACE_ORDER_ITEMS.deliveredAt))
        );
    }

    private static long value(Long value) {
        return value == null ? 0L : value;
    }

    private static long multiply(long price, int quantity) {
        try {
            return Math.multiplyExact(price, (long) quantity);
        } catch (ArithmeticException error) {
            throw validation("Cart total is too large.");
        }
    }

    private static long add(long left, long right) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException error) {
            throw validation("Cart total is too large.");
        }
    }

    private record CartSelection(UUID itemId, int quantity) {}
    private record LockedPurchase(MarketplaceItem item, int quantity, long lineTotal) {}
}
