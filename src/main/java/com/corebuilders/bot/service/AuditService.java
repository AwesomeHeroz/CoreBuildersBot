package com.corebuilders.bot.service;

import com.corebuilders.bot.db.QueryDslDatabase;
import com.corebuilders.bot.model.Models.AuditEntry;

import java.util.List;
import java.util.UUID;

import static com.corebuilders.bot.db.DbMappers.audit;
import static com.corebuilders.bot.db.DbMappers.auditColumns;
import static com.corebuilders.bot.db.DbValues.now;
import static com.corebuilders.bot.db.Schema.AUDIT_LOGS;

public final class AuditService {
    private final QueryDslDatabase database;

    public AuditService(QueryDslDatabase database) {
        this.database = database;
    }

    public void log(String actorDiscordId, String action, String targetDiscordId,
                    String entityType, String entityId, String details) {
        database.query(q -> q.insert(AUDIT_LOGS)
                .set(AUDIT_LOGS.id, UUID.randomUUID().toString())
                .set(AUDIT_LOGS.actorDiscordId, actorDiscordId)
                .set(AUDIT_LOGS.action, action)
                .set(AUDIT_LOGS.targetDiscordId, targetDiscordId)
                .set(AUDIT_LOGS.entityType, entityType)
                .set(AUDIT_LOGS.entityId, entityId)
                .set(AUDIT_LOGS.details, trim(details, 2000))
                .set(AUDIT_LOGS.createdAt, now())
                .execute());
    }

    public List<AuditEntry> recent(String targetDiscordId, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 50));
        return database.query(q -> {
            var query = q.select(auditColumns())
                    .from(AUDIT_LOGS);
            if (targetDiscordId != null) {
                query.where(AUDIT_LOGS.targetDiscordId.eq(targetDiscordId));
            }
            return query.orderBy(AUDIT_LOGS.createdAt.desc())
                    .limit(safeLimit)
                    .fetch()
                    .stream()
                    .map(com.corebuilders.bot.db.DbMappers::audit)
                    .toList();
        });
    }

    private static String trim(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max);
    }
}
