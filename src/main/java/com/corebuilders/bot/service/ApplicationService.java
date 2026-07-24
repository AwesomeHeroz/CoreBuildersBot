package com.corebuilders.bot.service;

import com.corebuilders.bot.db.QueryDslDatabase;
import com.corebuilders.bot.model.Domain.ApplicationStatus;
import com.corebuilders.bot.model.Models.ApplicationAnswer;
import com.corebuilders.bot.model.Models.ApplicationRecord;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.querydsl.core.Tuple;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.corebuilders.bot.db.DbValues.instant;
import static com.corebuilders.bot.db.DbValues.now;
import static com.corebuilders.bot.db.Schema.APPLICATIONS;

/**
 * QueryDSL-backed persistence for Discord membership applications.
 *
 * The service deliberately contains no Discord/JDA code. This keeps state changes
 * reusable and makes application decisions transactional independently of how an
 * interaction was triggered.
 */
public final class ApplicationService {
    private static final TypeReference<List<ApplicationAnswer>> ANSWERS_TYPE = new TypeReference<>() {};

    private final QueryDslDatabase database;
    private final ObjectMapper objectMapper;
    private final AuditService audit;
    private final boolean enforceSinglePending;

    public ApplicationService(
            QueryDslDatabase database,
            ObjectMapper objectMapper,
            AuditService audit,
            boolean enforceSinglePending
    ) {
        this.database = database;
        this.objectMapper = objectMapper;
        this.audit = audit;
        this.enforceSinglePending = enforceSinglePending;
    }

    public ApplicationRecord create(
            UUID id,
            String discordUserId,
            String username,
            List<ApplicationAnswer> answers
    ) {
        return database.inTransaction(() -> {
            Instant createdAt = Instant.now();
            String answersJson = serializeAnswers(answers);

            try {
                database.query(q -> {
                    var insert = q.insert(APPLICATIONS)
                            .set(APPLICATIONS.id, id.toString())
                            .set(APPLICATIONS.discordUserId, discordUserId)
                            .set(APPLICATIONS.username, username)
                            .set(APPLICATIONS.status, ApplicationStatus.PENDING.name())
                            .set(APPLICATIONS.answersJson, answersJson)
                            .set(APPLICATIONS.createdAt, com.corebuilders.bot.db.DbValues.time(createdAt));
                    if (enforceSinglePending) insert.set(APPLICATIONS.pendingGuard, discordUserId);
                    else insert.setNull(APPLICATIONS.pendingGuard);
                    return insert.execute();
                });
            } catch (RuntimeException error) {
                if (enforceSinglePending && database.isDuplicateKey(error)) {
                    throw new IllegalStateException("You already have a pending application.", error);
                }
                throw error;
            }

            audit.log(discordUserId, "APPLICATION_SUBMITTED", discordUserId,
                    "APPLICATION", id.toString(), "Membership application submitted");
            return get(id);
        });
    }

    public Optional<ApplicationRecord> pendingForUser(String discordUserId) {
        return database.query(q -> Optional.ofNullable(q.select(applicationColumns())
                        .from(APPLICATIONS)
                        .where(
                                APPLICATIONS.discordUserId.eq(discordUserId)
                                        .and(APPLICATIONS.status.eq(ApplicationStatus.PENDING.name()))
                        )
                        .orderBy(APPLICATIONS.createdAt.desc())
                        .limit(1)
                        .fetchOne())
                .map(this::mapApplication));
    }

    public Optional<ApplicationRecord> latestForUser(String discordUserId) {
        return database.query(q -> Optional.ofNullable(q.select(applicationColumns())
                        .from(APPLICATIONS)
                        .where(APPLICATIONS.discordUserId.eq(discordUserId))
                        .orderBy(APPLICATIONS.createdAt.desc())
                        .limit(1)
                        .fetchOne())
                .map(this::mapApplication));
    }

    public ApplicationRecord get(UUID id) {
        return database.query(q -> {
            Tuple row = q.select(applicationColumns())
                    .from(APPLICATIONS)
                    .where(APPLICATIONS.id.eq(id.toString()))
                    .fetchOne();
            if (row == null) {
                throw new IllegalArgumentException("Application not found: " + id);
            }
            return mapApplication(row);
        });
    }

    public ApplicationRecord setPendingMessage(UUID id, String channelId, String messageId) {
        database.query(q -> q.update(APPLICATIONS)
                .set(APPLICATIONS.pendingChannelId, channelId)
                .set(APPLICATIONS.pendingMessageId, messageId)
                .where(APPLICATIONS.id.eq(id.toString()))
                .execute());
        return get(id);
    }

    public ApplicationRecord setAnswers(UUID id, List<ApplicationAnswer> answers) {
        database.query(q -> q.update(APPLICATIONS)
                .set(APPLICATIONS.answersJson, serializeAnswers(answers))
                .where(APPLICATIONS.id.eq(id.toString()))
                .execute());
        return get(id);
    }

    public ApplicationRecord setTicketChannel(UUID id, String channelId, String actorDiscordId) {
        return database.inTransaction(() -> {
            ApplicationRecord current = lockPending(id);
            if (current.ticketChannelId() != null && !current.ticketChannelId().isBlank()) {
                throw new IllegalStateException("This application already has a discussion ticket.");
            }
            database.query(q -> q.update(APPLICATIONS)
                    .set(APPLICATIONS.ticketChannelId, channelId)
                    .where(APPLICATIONS.id.eq(id.toString()))
                    .execute());
            audit.log(actorDiscordId, "APPLICATION_TICKET_CREATED", current.discordUserId(),
                    "APPLICATION", id.toString(), "Discussion ticket channel: " + channelId);
            return get(id);
        });
    }

    public ApplicationRecord approve(UUID id, String reviewerDiscordId, String reason) {
        return decide(id, ApplicationStatus.ACCEPTED, reviewerDiscordId, reason);
    }

    public ApplicationRecord reject(UUID id, String reviewerDiscordId, String reason) {
        return decide(id, ApplicationStatus.REJECTED, reviewerDiscordId, reason);
    }

    private ApplicationRecord decide(
            UUID id,
            ApplicationStatus status,
            String reviewerDiscordId,
            String reason
    ) {
        return database.inTransaction(() -> {
            ApplicationRecord current = lockPending(id);
            database.query(q -> q.update(APPLICATIONS)
                    .set(APPLICATIONS.status, status.name())
                    .setNull(APPLICATIONS.pendingGuard)
                    .set(APPLICATIONS.reviewerDiscordId, reviewerDiscordId)
                    .set(APPLICATIONS.reviewReason, trim(reason, 1000))
                    .set(APPLICATIONS.reviewedAt, now())
                    .where(APPLICATIONS.id.eq(id.toString()))
                    .execute());

            audit.log(reviewerDiscordId, "APPLICATION_" + status.name(), current.discordUserId(),
                    "APPLICATION", id.toString(), trim(reason, 1000));
            return get(id);
        });
    }

    private ApplicationRecord lockPending(UUID id) {
        Tuple row = database.query(q -> q.select(applicationColumns())
                .from(APPLICATIONS)
                .where(APPLICATIONS.id.eq(id.toString()))
                .forUpdate()
                .fetchOne());
        if (row == null) {
            throw new IllegalArgumentException("Application not found: " + id);
        }
        ApplicationRecord application = mapApplication(row);
        if (application.status() != ApplicationStatus.PENDING) {
            throw new IllegalStateException(
                    "Application " + id + " has already been " + application.status().name().toLowerCase() + "."
            );
        }
        return application;
    }

    private com.querydsl.core.types.Expression<?>[] applicationColumns() {
        return new com.querydsl.core.types.Expression<?>[] {
                APPLICATIONS.id,
                APPLICATIONS.discordUserId,
                APPLICATIONS.username,
                APPLICATIONS.status,
                APPLICATIONS.answersJson,
                APPLICATIONS.pendingChannelId,
                APPLICATIONS.pendingMessageId,
                APPLICATIONS.ticketChannelId,
                APPLICATIONS.reviewerDiscordId,
                APPLICATIONS.reviewReason,
                APPLICATIONS.createdAt,
                APPLICATIONS.reviewedAt
        };
    }

    private ApplicationRecord mapApplication(Tuple row) {
        return new ApplicationRecord(
                UUID.fromString(row.get(APPLICATIONS.id)),
                row.get(APPLICATIONS.discordUserId),
                row.get(APPLICATIONS.username),
                ApplicationStatus.valueOf(row.get(APPLICATIONS.status)),
                deserializeAnswers(row.get(APPLICATIONS.answersJson)),
                row.get(APPLICATIONS.pendingChannelId),
                row.get(APPLICATIONS.pendingMessageId),
                row.get(APPLICATIONS.ticketChannelId),
                row.get(APPLICATIONS.reviewerDiscordId),
                row.get(APPLICATIONS.reviewReason),
                instant(row.get(APPLICATIONS.createdAt)),
                instant(row.get(APPLICATIONS.reviewedAt))
        );
    }

    private String serializeAnswers(List<ApplicationAnswer> answers) {
        try {
            return objectMapper.writeValueAsString(answers == null ? List.of() : answers);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Could not serialize application answers.", error);
        }
    }

    private List<ApplicationAnswer> deserializeAnswers(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, ANSWERS_TYPE);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Could not deserialize stored application answers.", error);
        }
    }

    private static String trim(String value, int max) {
        if (value == null) return "";
        String cleaned = value.trim();
        return cleaned.length() <= max ? cleaned : cleaned.substring(0, max);
    }
}
