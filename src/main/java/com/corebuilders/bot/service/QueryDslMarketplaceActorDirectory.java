package com.corebuilders.bot.service;

import com.corebuilders.bot.db.QueryDslDatabase;
import com.querydsl.core.Tuple;

import java.util.Objects;
import java.util.UUID;

import static com.corebuilders.bot.db.DbValues.uuid;
import static com.corebuilders.bot.db.Schema.MEMBERS;
import static com.corebuilders.bot.service.MarketplaceException.notFound;

/** QueryDSL adapter for marketplace audit identity lookup. */
public final class QueryDslMarketplaceActorDirectory implements MarketplaceActorDirectory {
    private final QueryDslDatabase database;

    public QueryDslMarketplaceActorDirectory(QueryDslDatabase database) {
        this.database = Objects.requireNonNull(database, "database");
    }

    @Override
    public String discordIdFor(UUID memberId) {
        Objects.requireNonNull(memberId, "memberId");
        Tuple member = database.query(q -> q.select(MEMBERS.id, MEMBERS.discordUserId)
                .from(MEMBERS)
                .where(MEMBERS.id.eq(uuid(memberId)))
                .fetchOne());
        if (member == null) throw notFound("Member profile not found.");
        String discordId = member.get(MEMBERS.discordUserId);
        return discordId == null || discordId.isBlank() ? "SYSTEM" : discordId;
    }
}
