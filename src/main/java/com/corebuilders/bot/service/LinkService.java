package com.corebuilders.bot.service;

import com.corebuilders.bot.db.QueryDslDatabase;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static com.corebuilders.bot.db.DbValues.*;
import static com.corebuilders.bot.db.Schema.LINK_CODES;
import static com.corebuilders.bot.db.Schema.MEMBERS;

public final class LinkService {
    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private final SecureRandom random = new SecureRandom();
    private final QueryDslDatabase database;

    public LinkService(QueryDslDatabase database) {
        this.database = database;
    }

    public LinkCode createCode(String discordUserId, int expiryMinutes) {
        return database.inTransaction(() -> {
            database.query(q -> q.delete(LINK_CODES)
                    .where(LINK_CODES.expiresAt.loe(now())
                            .or(LINK_CODES.discordUserId.eq(discordUserId)))
                    .execute());

            Long members = database.query(q -> q.select(MEMBERS.id.count())
                    .from(MEMBERS)
                    .where(MEMBERS.discordUserId.eq(discordUserId))
                    .fetchOne());
            if (members == null || members == 0) {
                throw new IllegalArgumentException("Your Core Builders member profile does not exist yet.");
            }

            for (int attempt = 0; attempt < 10; attempt++) {
                String code = randomCode(8);
                Instant expiresAt = Instant.now().plus(Math.max(1, expiryMinutes), ChronoUnit.MINUTES);
                try {
                    long inserted = database.query(q -> q.insert(LINK_CODES)
                            .set(LINK_CODES.code, code)
                            .set(LINK_CODES.discordUserId, discordUserId)
                            .set(LINK_CODES.expiresAt, time(expiresAt))
                            .set(LINK_CODES.createdAt, now())
                            .execute());
                    if (inserted == 1) return new LinkCode(code, expiresAt);
                } catch (RuntimeException error) {
                    if (!database.isDuplicateKey(error)) throw error;
                }
            }
            throw new IllegalStateException("Could not generate a unique link code. Try again.");
        });
    }

    public String consumeCode(String rawCode, UUID minecraftUuid, String minecraftName) {
        return database.inTransaction(() -> {
            String code = normalizeCode(rawCode);
            LinkRow link = database.query(q -> {
                var tuple = q.select(LINK_CODES.code, LINK_CODES.discordUserId, LINK_CODES.expiresAt)
                        .from(LINK_CODES)
                        .where(LINK_CODES.code.eq(code))
                        .forUpdate()
                        .fetchOne();
                if (tuple == null) {
                    throw new IllegalArgumentException("Invalid link code. Generate a new code with /link in Discord.");
                }
                return new LinkRow(tuple.get(LINK_CODES.code), tuple.get(LINK_CODES.discordUserId),
                        instant(tuple.get(LINK_CODES.expiresAt)));
            });

            if (!link.expiresAt().isAfter(Instant.now())) {
                database.query(q -> q.delete(LINK_CODES).where(LINK_CODES.code.eq(code)).execute());
                throw new IllegalArgumentException("That link code expired. Generate a new code with /link in Discord.");
            }

            String uuidText = uuid(minecraftUuid);
            Optional<String> uuidOwner = database.query(q -> Optional.ofNullable(q.select(MEMBERS.discordUserId)
                    .from(MEMBERS)
                    .where(MEMBERS.minecraftUuid.eq(uuidText))
                    .fetchOne()));
            if (uuidOwner.isPresent() && !uuidOwner.get().equals(link.discordUserId())) {
                throw new IllegalStateException("This Minecraft account is already linked to another Core Builders profile.");
            }

            Optional<String> existingUuid = database.query(q -> Optional.ofNullable(q.select(MEMBERS.minecraftUuid)
                    .from(MEMBERS)
                    .where(MEMBERS.discordUserId.eq(link.discordUserId()), MEMBERS.minecraftUuid.isNotNull())
                    .fetchOne()));
            if (existingUuid.isPresent() && !existingUuid.get().equals(uuidText)) {
                throw new IllegalStateException("That Discord profile is already linked to another Minecraft account. Unlink it first.");
            }

            database.query(q -> q.update(MEMBERS)
                    .set(MEMBERS.minecraftUuid, uuidText)
                    .set(MEMBERS.minecraftName, safeName(minecraftName))
                    .set(MEMBERS.securityVersion, MEMBERS.securityVersion.add(1L))
                    .set(MEMBERS.updatedAt, now())
                    .where(MEMBERS.discordUserId.eq(link.discordUserId()))
                    .execute());
            database.query(q -> q.delete(LINK_CODES)
                    .where(LINK_CODES.discordUserId.eq(link.discordUserId()))
                    .execute());
            return link.discordUserId();
        });
    }

    public Optional<String> findDiscordId(UUID minecraftUuid) {
        return database.query(q -> Optional.ofNullable(q.select(MEMBERS.discordUserId)
                .from(MEMBERS)
                .where(MEMBERS.minecraftUuid.eq(uuid(minecraftUuid)))
                .fetchOne()));
    }

    public Optional<String> findMinecraftName(String discordUserId) {
        return database.query(q -> Optional.ofNullable(q.select(MEMBERS.minecraftName)
                .from(MEMBERS)
                .where(MEMBERS.discordUserId.eq(discordUserId))
                .fetchOne()));
    }

    public boolean unlinkMinecraft(UUID minecraftUuid) {
        return database.inTransaction(() -> database.query(q -> q.update(MEMBERS)
                .setNull(MEMBERS.minecraftUuid)
                .setNull(MEMBERS.minecraftName)
                .set(MEMBERS.securityVersion, MEMBERS.securityVersion.add(1L))
                .set(MEMBERS.updatedAt, now())
                .where(MEMBERS.minecraftUuid.eq(uuid(minecraftUuid)))
                .execute()) > 0);
    }

    public void updateMinecraftName(UUID minecraftUuid, String minecraftName) {
        database.inTransaction(() -> database.query(q -> q.update(MEMBERS)
                .set(MEMBERS.minecraftName, safeName(minecraftName))
                .set(MEMBERS.updatedAt, now())
                .where(MEMBERS.minecraftUuid.eq(uuid(minecraftUuid)))
                .execute()));
    }

    private String randomCode(int length) {
        StringBuilder value = new StringBuilder(length);
        for (int i = 0; i < length; i++) value.append(ALPHABET[random.nextInt(ALPHABET.length)]);
        return value.toString();
    }

    private static String normalizeCode(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("A link code is required.");
        String code = value.trim().toUpperCase(java.util.Locale.ROOT);
        if (code.length() != 8) throw new IllegalArgumentException("The link code must contain 8 characters.");
        for (int i = 0; i < code.length(); i++) {
            boolean accepted = false;
            for (char allowed : ALPHABET) {
                if (code.charAt(i) == allowed) { accepted = true; break; }
            }
            if (!accepted) throw new IllegalArgumentException("The link code contains an invalid character.");
        }
        return code;
    }

    private static String safeName(String value) {
        if (value == null || value.isBlank()) return "Unknown";
        return value.length() <= 100 ? value : value.substring(0, 100);
    }

    public record LinkCode(String code, Instant expiresAt) {}
    private record LinkRow(String code, String discordUserId, Instant expiresAt) {}
}
