package com.corebuilders.bot.service;

import com.corebuilders.bot.db.QueryDslDatabase;
import com.corebuilders.bot.model.Domain.ContributionCategory;
import com.corebuilders.bot.model.Domain.MissionStatus;
import com.corebuilders.bot.model.Domain.SourceType;
import com.corebuilders.bot.model.Models.Member;
import com.corebuilders.bot.model.Models.Mission;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.corebuilders.bot.db.DbMappers.memberColumns;
import static com.corebuilders.bot.db.DbMappers.missionColumns;
import static com.corebuilders.bot.db.DbValues.*;
import static com.corebuilders.bot.db.Schema.*;

public final class MissionService {
    private final QueryDslDatabase database;
    private final LedgerService ledger;
    private final AuditService audit;

    public MissionService(QueryDslDatabase database, LedgerService ledger, AuditService audit) {
        this.database = database;
        this.ledger = ledger;
        this.audit = audit;
    }

    public Mission create(String name, String description, long rewardCxp, long rewardCredits,
                          int maxSlots, Instant deadline, String actorDiscordId) {
        return database.inTransaction(() -> {
            if (rewardCxp < 0 || rewardCredits < 0) throw new IllegalArgumentException("Rewards cannot be negative.");
            if (maxSlots < 0) throw new IllegalArgumentException("Max slots cannot be negative.");
            UUID id = UUID.randomUUID();
            database.query(q -> {
                var insert = q.insert(MISSIONS)
                        .set(MISSIONS.id, uuid(id))
                        .set(MISSIONS.name, limit(name, 200))
                        .set(MISSIONS.description, limit(description, 1500))
                        .set(MISSIONS.status, MissionStatus.OPEN.name())
                        .set(MISSIONS.rewardCxp, rewardCxp)
                        .set(MISSIONS.rewardCredits, rewardCredits)
                        .set(MISSIONS.maxSlots, maxSlots)
                        .set(MISSIONS.createdByDiscordId, actorDiscordId)
                        .set(MISSIONS.createdAt, now());
                if (deadline == null) insert.setNull(MISSIONS.deadline); else insert.set(MISSIONS.deadline, time(deadline));
                return insert.execute();
            });
            audit.log(actorDiscordId, "MISSION_CREATED", null, "MISSION", id.toString(), name);
            return get(id);
        });
    }

    public Mission get(UUID id) {
        return database.query(q -> Optional.ofNullable(q.select(missionColumns())
                        .from(MISSIONS)
                        .where(MISSIONS.id.eq(uuid(id)))
                        .fetchOne())
                .map(com.corebuilders.bot.db.DbMappers::mission)
                .orElseThrow(() -> new IllegalArgumentException("Mission not found.")));
    }

    public List<Mission> listActive(int limit) {
        return database.query(q -> q.select(missionColumns())
                .from(MISSIONS)
                .where(MISSIONS.status.in(MissionStatus.OPEN.name(), MissionStatus.IN_PROGRESS.name()))
                .orderBy(MISSIONS.createdAt.desc())
                .limit(Math.max(1, Math.min(limit, 25)))
                .fetch()
                .stream()
                .map(com.corebuilders.bot.db.DbMappers::mission)
                .toList());
    }

    public int memberCount(UUID missionId) {
        Long count = database.query(q -> q.select(MISSION_MEMBERS.memberId.count())
                .from(MISSION_MEMBERS)
                .where(MISSION_MEMBERS.missionId.eq(uuid(missionId)))
                .fetchOne());
        return count == null ? 0 : Math.toIntExact(count);
    }

    public void join(UUID missionId, Member member) {
        database.inTransaction(() -> {
            Mission mission = lock(missionId);
            if (mission.status() == MissionStatus.COMPLETED || mission.status() == MissionStatus.CANCELLED) {
                throw new IllegalStateException("This mission is closed.");
            }
            if (mission.deadline() != null && mission.deadline().isBefore(Instant.now())) {
                throw new IllegalStateException("This mission's deadline has passed.");
            }
            Long existing = database.query(q -> q.select(MISSION_MEMBERS.memberId.count())
                    .from(MISSION_MEMBERS)
                    .where(
                            MISSION_MEMBERS.missionId.eq(uuid(missionId)),
                            MISSION_MEMBERS.memberId.eq(uuid(member.id()))
                    )
                    .fetchOne());
            if (existing != null && existing > 0) return;

            int count = memberCount(missionId);
            if (mission.maxSlots() > 0 && count >= mission.maxSlots()) {
                throw new IllegalStateException("This mission is full.");
            }
            database.query(q -> q.insert(MISSION_MEMBERS)
                    .set(MISSION_MEMBERS.missionId, uuid(missionId))
                    .set(MISSION_MEMBERS.memberId, uuid(member.id()))
                    .set(MISSION_MEMBERS.joinedAt, now())
                    .execute());
            database.query(q -> q.update(MISSIONS)
                    .set(MISSIONS.status, MissionStatus.IN_PROGRESS.name())
                    .where(MISSIONS.id.eq(uuid(missionId)), MISSIONS.status.eq(MissionStatus.OPEN.name()))
                    .execute());
            audit.log(member.discordUserId(), "MISSION_JOINED", member.discordUserId(),
                    "MISSION", missionId.toString(), mission.name());
        });
    }

    public List<Member> complete(UUID missionId, String actorDiscordId) {
        return database.inTransaction(() -> {
            Mission mission = lock(missionId);
            if (mission.status() == MissionStatus.COMPLETED) {
                throw new IllegalStateException("Mission already completed.");
            }
            List<Member> participants = participants(missionId);
            if (participants.isEmpty()) {
                throw new IllegalStateException("Mission has no participants.");
            }

            database.query(q -> q.update(MISSIONS)
                    .set(MISSIONS.status, MissionStatus.COMPLETED.name())
                    .set(MISSIONS.completedAt, now())
                    .where(MISSIONS.id.eq(uuid(missionId)))
                    .execute());

            for (Member member : participants) {
                if (mission.rewardCxp() > 0) {
                    ledger.addXp(member.id(), mission.rewardCxp(), ContributionCategory.SPECIAL_OPERATIONS,
                            SourceType.MISSION, missionId, "Mission completed: " + mission.name(), actorDiscordId);
                }
                if (mission.rewardCredits() > 0) {
                    ledger.addCredits(member.id(), mission.rewardCredits(), SourceType.MISSION,
                            missionId, "Mission completed: " + mission.name(), actorDiscordId);
                }
            }
            audit.log(actorDiscordId, "MISSION_COMPLETED", null, "MISSION", missionId.toString(),
                    mission.name() + " rewarded " + participants.size() + " participants.");
            return participants;
        });
    }

    public List<Member> participants(UUID missionId) {
        return database.query(q -> q.select(memberColumns())
                .from(MISSION_MEMBERS)
                .join(MEMBERS).on(MEMBERS.id.eq(MISSION_MEMBERS.memberId))
                .where(MISSION_MEMBERS.missionId.eq(uuid(missionId)))
                .orderBy(MISSION_MEMBERS.joinedAt.asc())
                .fetch()
                .stream()
                .map(com.corebuilders.bot.db.DbMappers::member)
                .toList());
    }

    private Mission lock(UUID id) {
        String locked = database.query(q -> q.select(MISSIONS.id)
                .from(MISSIONS)
                .where(MISSIONS.id.eq(uuid(id)))
                .forUpdate()
                .fetchOne());
        if (locked == null) throw new IllegalArgumentException("Mission not found.");
        return get(id);
    }

    private static String limit(String value, int max) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("A value is required.");
        return value.length() <= max ? value : value.substring(0, max);
    }
}
