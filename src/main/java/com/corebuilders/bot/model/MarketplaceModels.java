package com.corebuilders.bot.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Domain records shared by the player marketplace service and HTTP adapter. */
public final class MarketplaceModels {
    private MarketplaceModels() {}

    public enum ItemSort {
        PRICE,
        NAME,
        STOCK,
        NEWEST;

        public static ItemSort parse(String value) {
            if (value == null || value.isBlank()) return NEWEST;
            try {
                return valueOf(value.trim().toUpperCase());
            } catch (IllegalArgumentException error) {
                throw new IllegalArgumentException("Unknown item sort: " + value);
            }
        }
    }

    public enum SortDirection {
        ASC,
        DESC;

        public static SortDirection parse(String value) {
            if (value == null || value.isBlank()) return DESC;
            try {
                return valueOf(value.trim().toUpperCase());
            } catch (IllegalArgumentException error) {
                throw new IllegalArgumentException("Unknown sort direction: " + value);
            }
        }
    }

    public record ShopInput(String name, String description) {}

    public record ItemInput(
            String name,
            String description,
            String imageUrl,
            int stock,
            long price,
            String category,
            boolean active
    ) {}

    public record ItemSearch(
            String text,
            String category,
            ItemSort sort,
            SortDirection direction,
            int page,
            int pageSize
    ) {
        public ItemSearch {
            sort = sort == null ? ItemSort.NEWEST : sort;
            direction = direction == null ? SortDirection.DESC : direction;
            page = Math.max(1, page);
            pageSize = Math.max(1, Math.min(50, pageSize));
        }
    }

    public record PlayerShop(
            UUID id,
            UUID ownerMemberId,
            String ownerDiscordId,
            String ownerUsername,
            String name,
            String description,
            boolean active,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public record MarketplaceItem(
            UUID id,
            UUID shopId,
            String shopName,
            UUID sellerMemberId,
            String sellerDiscordId,
            String sellerUsername,
            String name,
            String description,
            String imageUrl,
            int stock,
            long price,
            String category,
            boolean active,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public record ItemPage(
            List<MarketplaceItem> items,
            int page,
            int pageSize,
            long total
    ) {
        public ItemPage {
            items = items == null ? List.of() : List.copyOf(items);
        }
    }

    public record CartLine(MarketplaceItem item, int quantity, long lineTotal) {}

    public record MarketplaceCart(
            UUID id,
            UUID memberId,
            List<CartLine> items,
            long total,
            int itemCount,
            Instant updatedAt
    ) {
        public MarketplaceCart {
            items = items == null ? List.of() : List.copyOf(items);
        }
    }

    public record MarketplaceOrderLine(
            UUID id,
            UUID orderId,
            UUID itemId,
            UUID shopId,
            UUID sellerMemberId,
            UUID buyerMemberId,
            String buyerUsername,
            String shopName,
            String itemName,
            String imageUrl,
            String category,
            int quantity,
            long unitPrice,
            long lineTotal,
            String status,
            Instant createdAt,
            Instant deliveredAt
    ) {}

    public record MarketplaceOrder(
            UUID id,
            UUID buyerMemberId,
            long totalPrice,
            String status,
            List<MarketplaceOrderLine> lines,
            Instant createdAt,
            Instant completedAt
    ) {
        public MarketplaceOrder {
            lines = lines == null ? List.of() : List.copyOf(lines);
        }
    }
}
