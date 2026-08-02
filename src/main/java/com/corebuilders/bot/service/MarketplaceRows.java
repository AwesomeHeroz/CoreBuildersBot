package com.corebuilders.bot.service;

import com.corebuilders.bot.model.MarketplaceModels.MarketplaceItem;
import com.corebuilders.bot.model.MarketplaceModels.MarketplaceOrderLine;
import com.corebuilders.bot.model.MarketplaceModels.PlayerShop;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.Expression;

import java.util.UUID;

import static com.corebuilders.bot.db.DbValues.instant;
import static com.corebuilders.bot.db.Schema.*;

/** QueryDSL projections and row-to-domain mapping for marketplace persistence. */
final class MarketplaceRows {
    private MarketplaceRows() {}

    static Expression<?>[] shopColumns() {
        return new Expression<?>[]{MARKETPLACE_SHOPS.id, MARKETPLACE_SHOPS.ownerMemberId,
                MEMBERS.discordUserId, MEMBERS.username, MARKETPLACE_SHOPS.name,
                MARKETPLACE_SHOPS.description, MARKETPLACE_SHOPS.active,
                MARKETPLACE_SHOPS.createdAt, MARKETPLACE_SHOPS.updatedAt};
    }

    static PlayerShop mapShop(Tuple row) {
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

    static Expression<?>[] itemColumns() {
        return new Expression<?>[]{MARKETPLACE_ITEMS.id, MARKETPLACE_ITEMS.shopId,
                MARKETPLACE_SHOPS.name, MARKETPLACE_SHOPS.ownerMemberId,
                MEMBERS.discordUserId, MEMBERS.username, MARKETPLACE_ITEMS.name,
                MARKETPLACE_ITEMS.description, MARKETPLACE_ITEMS.imageUrl,
                MARKETPLACE_ITEMS.stock, MARKETPLACE_ITEMS.price, MARKETPLACE_ITEMS.category,
                MARKETPLACE_ITEMS.active, MARKETPLACE_ITEMS.version,
                MARKETPLACE_ITEMS.createdAt, MARKETPLACE_ITEMS.updatedAt};
    }

    static Expression<?>[] itemColumnsWithQuantity() {
        Expression<?>[] base = itemColumns();
        Expression<?>[] extended = new Expression<?>[base.length + 1];
        System.arraycopy(base, 0, extended, 0, base.length);
        extended[base.length] = MARKETPLACE_CART_ITEMS.quantity;
        return extended;
    }

    static MarketplaceItem mapItem(Tuple row) {
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
                number(row.get(MARKETPLACE_ITEMS.price)),
                row.get(MARKETPLACE_ITEMS.category),
                Boolean.TRUE.equals(row.get(MARKETPLACE_ITEMS.active)),
                number(row.get(MARKETPLACE_ITEMS.version)),
                instant(row.get(MARKETPLACE_ITEMS.createdAt)),
                instant(row.get(MARKETPLACE_ITEMS.updatedAt))
        );
    }

    static Expression<?>[] orderLineColumns() {
        return new Expression<?>[]{MARKETPLACE_ORDER_ITEMS.id, MARKETPLACE_ORDER_ITEMS.orderId,
                MARKETPLACE_ORDER_ITEMS.itemId, MARKETPLACE_ORDER_ITEMS.shopId,
                MARKETPLACE_ORDER_ITEMS.sellerMemberId, MARKETPLACE_ORDERS.buyerMemberId,
                MEMBERS.username, MARKETPLACE_ORDER_ITEMS.shopName, MARKETPLACE_ORDER_ITEMS.itemName,
                MARKETPLACE_ORDER_ITEMS.imageUrl, MARKETPLACE_ORDER_ITEMS.category,
                MARKETPLACE_ORDER_ITEMS.quantity, MARKETPLACE_ORDER_ITEMS.unitPrice,
                MARKETPLACE_ORDER_ITEMS.lineTotal, MARKETPLACE_ORDER_ITEMS.status,
                MARKETPLACE_ORDER_ITEMS.fundsReleased, MARKETPLACE_ORDER_ITEMS.createdAt,
                MARKETPLACE_ORDER_ITEMS.deliveredAt, MARKETPLACE_ORDER_ITEMS.sellerConfirmedAt,
                MARKETPLACE_ORDER_ITEMS.cancelledAt, MARKETPLACE_ORDER_ITEMS.cancelledBy,
                MARKETPLACE_ORDER_ITEMS.disputedAt, MARKETPLACE_ORDER_ITEMS.disputeReason,
                MARKETPLACE_ORDER_ITEMS.resolvedAt, MARKETPLACE_ORDER_ITEMS.resolution,
                MARKETPLACE_ORDER_ITEMS.resolutionNote};
    }

    static MarketplaceOrderLine mapOrderLine(Tuple row) {
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
                number(row.get(MARKETPLACE_ORDER_ITEMS.unitPrice)),
                number(row.get(MARKETPLACE_ORDER_ITEMS.lineTotal)),
                row.get(MARKETPLACE_ORDER_ITEMS.status),
                Boolean.TRUE.equals(row.get(MARKETPLACE_ORDER_ITEMS.fundsReleased)),
                instant(row.get(MARKETPLACE_ORDER_ITEMS.createdAt)),
                instant(row.get(MARKETPLACE_ORDER_ITEMS.deliveredAt)),
                instant(row.get(MARKETPLACE_ORDER_ITEMS.sellerConfirmedAt)),
                instant(row.get(MARKETPLACE_ORDER_ITEMS.cancelledAt)),
                row.get(MARKETPLACE_ORDER_ITEMS.cancelledBy),
                instant(row.get(MARKETPLACE_ORDER_ITEMS.disputedAt)),
                row.get(MARKETPLACE_ORDER_ITEMS.disputeReason),
                instant(row.get(MARKETPLACE_ORDER_ITEMS.resolvedAt)),
                row.get(MARKETPLACE_ORDER_ITEMS.resolution),
                row.get(MARKETPLACE_ORDER_ITEMS.resolutionNote)
        );
    }

    static long number(Long value) {
        return value == null ? 0L : value;
    }
}
