package com.corebuilders.bot.persistence;

import com.corebuilders.bot.db.QueryDslDatabase;
import com.corebuilders.bot.model.Domain.SpawnHelpStatus;
import com.corebuilders.bot.model.Models.SpawnHelpTicket;
import com.corebuilders.bot.service.SpawnHelpTicketRepository;
import com.querydsl.core.Tuple;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static com.corebuilders.bot.db.DbValues.instant;
import static com.corebuilders.bot.db.DbValues.now;
import static com.corebuilders.bot.db.DbValues.time;
import static com.corebuilders.bot.db.Schema.SPAWN_HELP_TICKETS;

/** QueryDSL implementation of the spawn-help repository with transaction-safe state transitions. */
public final class QueryDslSpawnHelpTicketRepository implements SpawnHelpTicketRepository {
    private final QueryDslDatabase database;

    public QueryDslSpawnHelpTicketRepository(QueryDslDatabase database) {
        this.database = Objects.requireNonNull(database, "database");
    }

    @Override
    public SpawnHelpTicket createOpen(SpawnHelpTicket ticket) {
        Objects.requireNonNull(ticket, "ticket");
        try {
            database.query(q -> q.insert(SPAWN_HELP_TICKETS)
                    .set(SPAWN_HELP_TICKETS.id, ticket.id().toString())
                    .set(SPAWN_HELP_TICKETS.discordUserId, ticket.discordUserId())
                    .set(SPAWN_HELP_TICKETS.discordUsername, ticket.discordUsername())
                    .set(SPAWN_HELP_TICKETS.activeGuard, ticket.discordUserId())
                    .set(SPAWN_HELP_TICKETS.serverId, ticket.serverId())
                    .set(SPAWN_HELP_TICKETS.serverName, ticket.serverName())
                    .set(SPAWN_HELP_TICKETS.inGameName, ticket.inGameName())
                    .set(SPAWN_HELP_TICKETS.status, SpawnHelpStatus.OPEN.name())
                    .set(SPAWN_HELP_TICKETS.createdAt, time(ticket.createdAt()))
                    .set(SPAWN_HELP_TICKETS.updatedAt, time(ticket.updatedAt()))
                    .execute());
        } catch (RuntimeException error) {
            if (database.isDuplicateKey(error)) {
                throw new IllegalStateException("You already have an active spawn-help ticket. Complete or close it before creating another one.", error);
            }
            throw error;
        }
        return find(ticket.id()).orElseThrow();
    }

    @Override
    public Optional<SpawnHelpTicket> find(UUID id) {
        Objects.requireNonNull(id, "id");
        return database.query(q -> Optional.ofNullable(q.select(columns())
                .from(SPAWN_HELP_TICKETS)
                .where(SPAWN_HELP_TICKETS.id.eq(id.toString()))
                .fetchOne()).map(this::map));
    }

    @Override
    public Optional<SpawnHelpTicket> activeForUser(String discordUserId) {
        if (discordUserId == null || discordUserId.isBlank()) return Optional.empty();
        return database.query(q -> Optional.ofNullable(q.select(columns())
                .from(SPAWN_HELP_TICKETS)
                .where(SPAWN_HELP_TICKETS.activeGuard.eq(discordUserId))
                .limit(1)
                .fetchOne()).map(this::map));
    }

    @Override
    public SpawnHelpTicket setChannel(UUID id, String channelId, String controlMessageId) {
        return database.inTransaction(() -> {
            SpawnHelpTicket current = lock(id);
            if (current.status() != SpawnHelpStatus.OPEN) {
                throw new IllegalStateException("Cannot attach a Discord channel to a closed spawn-help ticket.");
            }
            if (current.channelId() != null && !current.channelId().isBlank()) {
                if (current.channelId().equals(channelId)) return current;
                throw new IllegalStateException("This spawn-help ticket already has a Discord channel.");
            }
            database.query(q -> q.update(SPAWN_HELP_TICKETS)
                    .set(SPAWN_HELP_TICKETS.channelId, channelId)
                    .set(SPAWN_HELP_TICKETS.controlMessageId, controlMessageId)
                    .set(SPAWN_HELP_TICKETS.updatedAt, now())
                    .where(SPAWN_HELP_TICKETS.id.eq(id.toString()))
                    .execute());
            return require(id);
        });
    }

    @Override
    public SpawnHelpTicket claim(UUID id, String helperDiscordId, String helperUsername) {
        return database.inTransaction(() -> {
            SpawnHelpTicket current = lock(id);
            if (current.status() != SpawnHelpStatus.OPEN) {
                throw new IllegalStateException("This spawn-help ticket was already picked up or closed.");
            }
            database.query(q -> q.update(SPAWN_HELP_TICKETS)
                    .set(SPAWN_HELP_TICKETS.status, SpawnHelpStatus.ON_HELP.name())
                    .set(SPAWN_HELP_TICKETS.helperDiscordId, helperDiscordId)
                    .set(SPAWN_HELP_TICKETS.helperUsername, helperUsername)
                    .set(SPAWN_HELP_TICKETS.updatedAt, now())
                    .where(SPAWN_HELP_TICKETS.id.eq(id.toString()))
                    .execute());
            return require(id);
        });
    }

    @Override
    public SpawnHelpTicket complete(UUID id, String helperDiscordId) {
        return database.inTransaction(() -> {
            SpawnHelpTicket current = lock(id);
            if (current.status() != SpawnHelpStatus.ON_HELP) {
                throw new IllegalStateException("Only a ticket currently ON HELP can be completed.");
            }
            if (!helperDiscordId.equals(current.helperDiscordId())) {
                throw new SecurityException("Only the spawn helper who picked up this ticket can complete it.");
            }
            var closed = now();
            database.query(q -> q.update(SPAWN_HELP_TICKETS)
                    .set(SPAWN_HELP_TICKETS.status, SpawnHelpStatus.COMPLETED.name())
                    .setNull(SPAWN_HELP_TICKETS.activeGuard)
                    .set(SPAWN_HELP_TICKETS.updatedAt, closed)
                    .set(SPAWN_HELP_TICKETS.closedAt, closed)
                    .where(SPAWN_HELP_TICKETS.id.eq(id.toString()))
                    .execute());
            return require(id);
        });
    }

    @Override
    public SpawnHelpTicket deny(UUID id, String helperDiscordId, String helperUsername, String reason) {
        return database.inTransaction(() -> {
            SpawnHelpTicket current = lock(id);
            if (current.status() == SpawnHelpStatus.COMPLETED || current.status() == SpawnHelpStatus.DENIED) {
                throw new IllegalStateException("This spawn-help ticket is already closed.");
            }
            if (current.status() == SpawnHelpStatus.ON_HELP
                    && current.helperDiscordId() != null
                    && !current.helperDiscordId().equals(helperDiscordId)) {
                throw new SecurityException("Only the spawn helper who picked up this ticket can deny it now.");
            }
            var closed = now();
            database.query(q -> q.update(SPAWN_HELP_TICKETS)
                    .set(SPAWN_HELP_TICKETS.status, SpawnHelpStatus.DENIED.name())
                    .set(SPAWN_HELP_TICKETS.helperDiscordId, helperDiscordId)
                    .set(SPAWN_HELP_TICKETS.helperUsername, helperUsername)
                    .set(SPAWN_HELP_TICKETS.denialReason, reason)
                    .setNull(SPAWN_HELP_TICKETS.activeGuard)
                    .set(SPAWN_HELP_TICKETS.updatedAt, closed)
                    .set(SPAWN_HELP_TICKETS.closedAt, closed)
                    .where(SPAWN_HELP_TICKETS.id.eq(id.toString()))
                    .execute());
            return require(id);
        });
    }

    @Override
    public void abortUninitialized(UUID id, String discordUserId) {
        database.query(q -> q.delete(SPAWN_HELP_TICKETS)
                .where(SPAWN_HELP_TICKETS.id.eq(id.toString()),
                        SPAWN_HELP_TICKETS.discordUserId.eq(discordUserId),
                        SPAWN_HELP_TICKETS.status.eq(SpawnHelpStatus.OPEN.name()),
                        SPAWN_HELP_TICKETS.channelId.isNull(),
                        SPAWN_HELP_TICKETS.helperDiscordId.isNull())
                .execute());
    }

    private SpawnHelpTicket lock(UUID id) {
        Tuple row = database.query(q -> q.select(columns())
                .from(SPAWN_HELP_TICKETS)
                .where(SPAWN_HELP_TICKETS.id.eq(id.toString()))
                .forUpdate()
                .fetchOne());
        if (row == null) throw new IllegalArgumentException("Spawn-help ticket not found: " + id);
        return map(row);
    }

    private SpawnHelpTicket require(UUID id) {
        return find(id).orElseThrow(() -> new IllegalArgumentException("Spawn-help ticket not found: " + id));
    }

    private com.querydsl.core.types.Expression<?>[] columns() {
        return new com.querydsl.core.types.Expression<?>[] {
                SPAWN_HELP_TICKETS.id,
                SPAWN_HELP_TICKETS.discordUserId,
                SPAWN_HELP_TICKETS.discordUsername,
                SPAWN_HELP_TICKETS.serverId,
                SPAWN_HELP_TICKETS.serverName,
                SPAWN_HELP_TICKETS.inGameName,
                SPAWN_HELP_TICKETS.status,
                SPAWN_HELP_TICKETS.helperDiscordId,
                SPAWN_HELP_TICKETS.helperUsername,
                SPAWN_HELP_TICKETS.denialReason,
                SPAWN_HELP_TICKETS.channelId,
                SPAWN_HELP_TICKETS.controlMessageId,
                SPAWN_HELP_TICKETS.createdAt,
                SPAWN_HELP_TICKETS.updatedAt,
                SPAWN_HELP_TICKETS.closedAt
        };
    }

    private SpawnHelpTicket map(Tuple row) {
        return new SpawnHelpTicket(
                UUID.fromString(row.get(SPAWN_HELP_TICKETS.id)),
                row.get(SPAWN_HELP_TICKETS.discordUserId),
                row.get(SPAWN_HELP_TICKETS.discordUsername),
                row.get(SPAWN_HELP_TICKETS.serverId),
                row.get(SPAWN_HELP_TICKETS.serverName),
                row.get(SPAWN_HELP_TICKETS.inGameName),
                SpawnHelpStatus.valueOf(row.get(SPAWN_HELP_TICKETS.status)),
                row.get(SPAWN_HELP_TICKETS.helperDiscordId),
                row.get(SPAWN_HELP_TICKETS.helperUsername),
                row.get(SPAWN_HELP_TICKETS.denialReason),
                row.get(SPAWN_HELP_TICKETS.channelId),
                row.get(SPAWN_HELP_TICKETS.controlMessageId),
                instant(row.get(SPAWN_HELP_TICKETS.createdAt)),
                instant(row.get(SPAWN_HELP_TICKETS.updatedAt)),
                instant(row.get(SPAWN_HELP_TICKETS.closedAt))
        );
    }
}
