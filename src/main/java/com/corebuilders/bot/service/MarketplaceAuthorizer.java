package com.corebuilders.bot.service;

import com.corebuilders.bot.db.QueryDslDatabase;
import com.querydsl.core.Tuple;

import java.util.Objects;
import java.util.UUID;

import static com.corebuilders.bot.db.DbValues.uuid;
import static com.corebuilders.bot.db.Schema.MEMBERS;
import static com.corebuilders.bot.service.MarketplaceException.forbidden;

/** Central marketplace authorization policy, deliberately separate from login authentication. */
public final class MarketplaceAuthorizer {
    private final QueryDslDatabase database;

    public MarketplaceAuthorizer(QueryDslDatabase database) {
        this.database = Objects.requireNonNull(database, "database");
    }

    public void requireAuthorized(UUID memberId) {
        require(memberId, false);
    }

    /** Must be called inside a transaction before a security-sensitive mutation. */
    public void requireAuthorizedForUpdate(UUID memberId) {
        require(memberId, true);
    }

    private void require(UUID memberId, boolean lock) {
        Objects.requireNonNull(memberId, "memberId");
        Tuple row = database.query(q -> {
            var query = q.select(
                            MEMBERS.active,
                            MEMBERS.discordUserId,
                            MEMBERS.minecraftUuid,
                            MEMBERS.minecraftLoginProvisional,
                            MEMBERS.reputation,
                            MEMBERS.primaryRole)
                    .from(MEMBERS)
                    .where(MEMBERS.id.eq(uuid(memberId)));
            if (lock) query.forUpdate();
            return query.fetchOne();
        });
        if (row == null || !Boolean.TRUE.equals(row.get(MEMBERS.active))) {
            throw forbidden("Your Core Builders profile is inactive or missing.");
        }
        if (blank(row.get(MEMBERS.discordUserId))) {
            throw forbidden("Link Discord before using marketplace account features.");
        }
        if (blank(row.get(MEMBERS.minecraftUuid))) {
            throw forbidden("A verified Minecraft account is required.");
        }
        if (Boolean.TRUE.equals(row.get(MEMBERS.minecraftLoginProvisional))) {
            throw forbidden("Your marketplace membership is awaiting staff approval.");
        }
        String reputation = row.get(MEMBERS.reputation);
        if (blank(reputation) || "UNVERIFIED".equals(reputation) || blank(row.get(MEMBERS.primaryRole))) {
            throw forbidden("An approved reputation and member role are required for marketplace access.");
        }
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
}
