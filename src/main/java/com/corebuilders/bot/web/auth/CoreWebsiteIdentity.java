package com.corebuilders.bot.web.auth;

import com.corebuilders.bot.db.QueryDslDatabase;
import com.corebuilders.bot.model.Models.Member;
import com.corebuilders.bot.service.LedgerService;
import com.corebuilders.bot.service.MemberService;
import com.querydsl.core.Tuple;

import java.util.Objects;
import java.util.UUID;

import static com.corebuilders.bot.db.DbValues.now;
import static com.corebuilders.bot.db.DbValues.uuid;
import static com.corebuilders.bot.db.Schema.MEMBERS;

/**
 * Website identity adapter. Minecraft owns authentication; Discord is an
 * optional linked identity used for marketplace delivery and notifications.
 */
public final class CoreWebsiteIdentity implements WebsiteIdentity {
    private final QueryDslDatabase database;
    private final MemberService legacyMembers;
    private final LedgerService ledger;

    public CoreWebsiteIdentity(QueryDslDatabase database, LedgerService ledger) {
        this.database = Objects.requireNonNull(database, "database");
        this.legacyMembers = null;
        this.ledger = Objects.requireNonNull(ledger, "ledger");
    }

    /**
     * Retained for source compatibility with older tests and integrations.
     */
    @Deprecated
    public CoreWebsiteIdentity(MemberService members, LedgerService ledger) {
        this.database = null;
        this.legacyMembers = Objects.requireNonNull(members, "members");
        this.ledger = Objects.requireNonNull(ledger, "ledger");
    }

    @Override
    public SessionPrincipal ensureProfile(DiscordIdentity identity) {
        Objects.requireNonNull(identity, "identity");
        if (database != null) {
            String memberId = database.query(q -> q.select(MEMBERS.id)
                    .from(MEMBERS)
                    .where(MEMBERS.discordUserId.eq(identity.id()))
                    .fetchOne());
            if (memberId == null) {
                throw new IllegalStateException(
                        "Log in through Minecraft first, then link this Discord account."
                );
            }
            return requireProfile(uuid(memberId));
        }

        Member member = legacyMembers.ensureMember(identity.id(), identity.displayName());
        if (!member.active()) {
            throw new IllegalStateException("Your Core Builders profile is inactive.");
        }
        return new SessionPrincipal(member.id(), member.discordUserId(), member.username(), identity.avatarUrl());
    }

    @Override
    public SessionPrincipal requireProfile(UUID memberId) {
        requireDatabase();
        Tuple member = database.query(q -> q.select(
                        MEMBERS.id,
                        MEMBERS.discordUserId,
                        MEMBERS.username,
                        MEMBERS.active,
                        MEMBERS.minecraftName,
                        MEMBERS.discordAvatarUrl)
                .from(MEMBERS)
                .where(MEMBERS.id.eq(uuid(memberId)))
                .fetchOne());
        if (member == null) {
            throw new IllegalStateException("Your Core Builders profile no longer exists.");
        }
        if (!Boolean.TRUE.equals(member.get(MEMBERS.active))) {
            throw new IllegalStateException("Your Core Builders profile is inactive.");
        }
        String minecraftName = member.get(MEMBERS.minecraftName);
        String username = minecraftName == null || minecraftName.isBlank()
                ? member.get(MEMBERS.username)
                : minecraftName;
        return new SessionPrincipal(
                uuid(member.get(MEMBERS.id)),
                member.get(MEMBERS.discordUserId),
                username,
                member.get(MEMBERS.discordAvatarUrl)
        );
    }

    @Override
    public SessionPrincipal linkDiscord(UUID memberId, DiscordIdentity identity) {
        requireDatabase();
        Objects.requireNonNull(memberId, "memberId");
        Objects.requireNonNull(identity, "identity");

        return database.inTransaction(() -> {
            Tuple current = database.query(q -> q.select(
                            MEMBERS.id,
                            MEMBERS.discordUserId,
                            MEMBERS.active,
                            MEMBERS.minecraftUuid,
                            MEMBERS.minecraftName,
                            MEMBERS.minecraftLoginProvisional)
                    .from(MEMBERS)
                    .where(MEMBERS.id.eq(uuid(memberId)))
                    .forUpdate()
                    .fetchOne());
            if (current == null) {
                throw new IllegalStateException("Your Core Builders profile no longer exists.");
            }
            if (!Boolean.TRUE.equals(current.get(MEMBERS.active))) {
                throw new IllegalStateException("Your Core Builders profile is inactive.");
            }

            String existingDiscord = current.get(MEMBERS.discordUserId);
            if (existingDiscord != null && !existingDiscord.equals(identity.id())) {
                throw new IllegalStateException("This Core Builders profile is already linked to another Discord account.");
            }

            Tuple legacyMember = database.query(q -> q.select(
                            MEMBERS.id,
                            MEMBERS.active,
                            MEMBERS.minecraftUuid)
                    .from(MEMBERS)
                    .where(MEMBERS.discordUserId.eq(identity.id()), MEMBERS.id.ne(uuid(memberId)))
                    .forUpdate()
                    .fetchOne());
            if (legacyMember != null) {
                if (!Boolean.TRUE.equals(current.get(MEMBERS.minecraftLoginProvisional))) {
                    throw new IllegalStateException(
                            "That Discord account belongs to another established profile. Contact an administrator to merge profiles."
                    );
                }
                if (!Boolean.TRUE.equals(legacyMember.get(MEMBERS.active))) {
                    throw new IllegalStateException("That Discord account belongs to an inactive Core Builders profile.");
                }
                String minecraftUuid = current.get(MEMBERS.minecraftUuid);
                String legacyMinecraftUuid = legacyMember.get(MEMBERS.minecraftUuid);
                if (minecraftUuid == null || minecraftUuid.isBlank()) {
                    throw new IllegalStateException("The current profile is missing its verified Minecraft account.");
                }
                if (legacyMinecraftUuid != null && !legacyMinecraftUuid.equals(minecraftUuid)) {
                    throw new IllegalStateException("That Discord account is already linked to another Minecraft account.");
                }

                String legacyMemberId = legacyMember.get(MEMBERS.id);
                database.query(q -> q.update(MEMBERS)
                        .setNull(MEMBERS.minecraftUuid)
                        .setNull(MEMBERS.minecraftName)
                        .set(MEMBERS.active, false)
                        .set(MEMBERS.minecraftLoginProvisional, false)
                        .set(MEMBERS.updatedAt, now())
                        .where(MEMBERS.id.eq(uuid(memberId)))
                        .execute());
                database.query(q -> q.update(MEMBERS)
                        .set(MEMBERS.minecraftUuid, minecraftUuid)
                        .set(MEMBERS.minecraftName, current.get(MEMBERS.minecraftName))
                        .set(MEMBERS.discordUsername, safe(identity.displayName(), 100))
                        .set(MEMBERS.discordAvatarUrl, safe(identity.avatarUrl(), 1000))
                        .set(MEMBERS.minecraftLoginProvisional, false)
                        .set(MEMBERS.updatedAt, now())
                        .where(MEMBERS.id.eq(legacyMemberId))
                        .execute());
                return requireProfile(uuid(legacyMemberId));
            }

            database.query(q -> q.update(MEMBERS)
                    .set(MEMBERS.discordUserId, identity.id())
                    .set(MEMBERS.discordUsername, safe(identity.displayName(), 100))
                    .set(MEMBERS.discordAvatarUrl, safe(identity.avatarUrl(), 1000))
                    .set(MEMBERS.minecraftLoginProvisional, false)
                    .set(MEMBERS.updatedAt, now())
                    .where(MEMBERS.id.eq(uuid(memberId)))
                    .execute());
            return requireProfile(memberId);
        });
    }

    @Override
    public long contributionPointBalance(UUID memberId) {
        return ledger.creditBalance(memberId);
    }

    private void requireDatabase() {
        if (database == null) {
            throw new UnsupportedOperationException("This identity adapter was created in legacy compatibility mode.");
        }
    }

    private static String safe(String value, int maximum) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.length() <= maximum ? trimmed : trimmed.substring(0, maximum);
    }
}
