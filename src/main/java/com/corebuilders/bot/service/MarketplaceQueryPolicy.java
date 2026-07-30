package com.corebuilders.bot.service;

import com.corebuilders.bot.model.MarketplaceModels.ItemSearch;
import com.corebuilders.bot.model.MarketplaceModels.SortDirection;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.OrderSpecifier;

import static com.corebuilders.bot.db.Schema.*;

/** Reusable visibility, search, and ordering rules for marketplace catalog queries. */
final class MarketplaceQueryPolicy {
    private MarketplaceQueryPolicy() {}

    static BooleanBuilder publicItemFilters(String text, String category) {
        BooleanBuilder filters = new BooleanBuilder()
                .and(MARKETPLACE_ITEMS.active.isTrue())
                .and(MARKETPLACE_SHOPS.active.isTrue())
                .and(MEMBERS.active.isTrue())
                .and(MEMBERS.minecraftLoginProvisional.isFalse())
                .and(MEMBERS.reputation.isNotNull())
                .and(MEMBERS.reputation.ne(""))
                .and(MEMBERS.reputation.ne("UNVERIFIED"))
                .and(MEMBERS.primaryRole.isNotNull())
                .and(MEMBERS.primaryRole.ne(""));
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

    static OrderSpecifier<?> orderFor(ItemSearch search) {
        boolean ascending = search.direction() == SortDirection.ASC;
        return switch (search.sort()) {
            case PRICE -> ascending ? MARKETPLACE_ITEMS.price.asc() : MARKETPLACE_ITEMS.price.desc();
            case NAME -> ascending ? MARKETPLACE_ITEMS.name.asc() : MARKETPLACE_ITEMS.name.desc();
            case STOCK -> ascending ? MARKETPLACE_ITEMS.stock.asc() : MARKETPLACE_ITEMS.stock.desc();
            case NEWEST -> ascending ? MARKETPLACE_ITEMS.createdAt.asc() : MARKETPLACE_ITEMS.createdAt.desc();
        };
    }
}
