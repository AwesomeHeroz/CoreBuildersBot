package com.corebuilders.bot.service;

import com.corebuilders.bot.db.QueryDslDatabase;
import com.corebuilders.bot.model.Domain.ContributionCategory;
import com.corebuilders.bot.model.Domain.ProjectStatus;
import com.corebuilders.bot.model.Domain.SourceType;
import com.corebuilders.bot.model.Domain.TaskStatus;
import com.corebuilders.bot.model.Models.Member;
import com.corebuilders.bot.model.Models.Project;
import com.corebuilders.bot.model.Models.ProjectTask;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.corebuilders.bot.db.DbMappers.*;
import static com.corebuilders.bot.db.DbValues.now;
import static com.corebuilders.bot.db.DbValues.uuid;
import static com.corebuilders.bot.db.Schema.*;

public final class ProjectService {
    private final QueryDslDatabase database;
    private final LedgerService ledger;
    private final AuditService audit;

    public ProjectService(QueryDslDatabase database, LedgerService ledger, AuditService audit) {
        this.database = database;
        this.ledger = ledger;
        this.audit = audit;
    }

    public Project create(String name, String description, String leadDiscordId, String actorDiscordId) {
        return database.inTransaction(() -> {
            UUID id = UUID.randomUUID();
            database.query(q -> q.insert(PROJECTS)
                    .set(PROJECTS.id, uuid(id))
                    .set(PROJECTS.name, limit(name, 200))
                    .set(PROJECTS.description, limit(description, 1500))
                    .set(PROJECTS.status, ProjectStatus.OPEN.name())
                    .set(PROJECTS.leadDiscordId, leadDiscordId)
                    .set(PROJECTS.createdByDiscordId, actorDiscordId)
                    .set(PROJECTS.createdAt, now())
                    .execute());
            audit.log(actorDiscordId, "PROJECT_CREATED", leadDiscordId, "PROJECT", id.toString(), name);
            return get(id);
        });
    }

    public Project get(UUID id) {
        return database.query(q -> Optional.ofNullable(q.select(projectColumns())
                        .from(PROJECTS)
                        .where(PROJECTS.id.eq(uuid(id)))
                        .fetchOne())
                .map(com.corebuilders.bot.db.DbMappers::project)
                .orElseThrow(() -> new IllegalArgumentException("Project not found.")));
    }

    public List<Project> listActive(int limit) {
        return database.query(q -> q.select(projectColumns())
                .from(PROJECTS)
                .where(PROJECTS.status.in(ProjectStatus.OPEN.name(), ProjectStatus.IN_PROGRESS.name()))
                .orderBy(PROJECTS.createdAt.desc())
                .limit(Math.max(1, Math.min(limit, 25)))
                .fetch()
                .stream()
                .map(com.corebuilders.bot.db.DbMappers::project)
                .toList());
    }

    public void join(UUID projectId, Member member) {
        database.inTransaction(() -> {
            Project project = lock(projectId);
            if (project.status() == ProjectStatus.COMPLETED || project.status() == ProjectStatus.CANCELLED) {
                throw new IllegalStateException("This project is closed.");
            }
            Long existing = database.query(q -> q.select(PROJECT_MEMBERS.memberId.count())
                    .from(PROJECT_MEMBERS)
                    .where(
                            PROJECT_MEMBERS.projectId.eq(uuid(projectId)),
                            PROJECT_MEMBERS.memberId.eq(uuid(member.id()))
                    )
                    .fetchOne());
            if (existing != null && existing > 0) return;

            database.query(q -> q.insert(PROJECT_MEMBERS)
                    .set(PROJECT_MEMBERS.projectId, uuid(projectId))
                    .set(PROJECT_MEMBERS.memberId, uuid(member.id()))
                    .set(PROJECT_MEMBERS.joinedAt, now())
                    .execute());
            database.query(q -> q.update(PROJECTS)
                    .set(PROJECTS.status, ProjectStatus.IN_PROGRESS.name())
                    .where(PROJECTS.id.eq(uuid(projectId)), PROJECTS.status.eq(ProjectStatus.OPEN.name()))
                    .execute());
            audit.log(member.discordUserId(), "PROJECT_JOINED", member.discordUserId(),
                    "PROJECT", projectId.toString(), project.name());
        });
    }

    public void leave(UUID projectId, Member member) {
        database.inTransaction(() -> {
            database.query(q -> q.delete(PROJECT_MEMBERS)
                    .where(PROJECT_MEMBERS.projectId.eq(uuid(projectId)),
                            PROJECT_MEMBERS.memberId.eq(uuid(member.id())))
                    .execute());
            audit.log(member.discordUserId(), "PROJECT_LEFT", member.discordUserId(),
                    "PROJECT", projectId.toString(), "Member left project.");
        });
    }

    public ProjectTask addTask(UUID projectId, String title, Member assignee,
                               long rewardCxp, long rewardCredits, String actorDiscordId) {
        return database.inTransaction(() -> {
            get(projectId);
            if (rewardCxp < 0 || rewardCredits < 0) {
                throw new IllegalArgumentException("Task rewards cannot be negative.");
            }
            UUID id = UUID.randomUUID();
            database.query(q -> {
                var insert = q.insert(PROJECT_TASKS)
                        .set(PROJECT_TASKS.id, uuid(id))
                        .set(PROJECT_TASKS.projectId, uuid(projectId))
                        .set(PROJECT_TASKS.title, limit(title, 300))
                        .set(PROJECT_TASKS.status, TaskStatus.OPEN.name())
                        .set(PROJECT_TASKS.rewardCxp, rewardCxp)
                        .set(PROJECT_TASKS.rewardCredits, rewardCredits)
                        .set(PROJECT_TASKS.createdAt, now());
                if (assignee == null) insert.setNull(PROJECT_TASKS.assignedMemberId);
                else insert.set(PROJECT_TASKS.assignedMemberId, uuid(assignee.id()));
                return insert.execute();
            });
            audit.log(actorDiscordId, "PROJECT_TASK_CREATED",
                    assignee == null ? null : assignee.discordUserId(),
                    "PROJECT_TASK", id.toString(), title);
            return getTask(projectId, id);
        });
    }

    public List<ProjectTask> tasks(UUID projectId) {
        return database.query(q -> q.select(projectTaskColumns())
                .from(PROJECT_TASKS)
                .leftJoin(MEMBERS).on(MEMBERS.id.eq(PROJECT_TASKS.assignedMemberId))
                .where(PROJECT_TASKS.projectId.eq(uuid(projectId)))
                .orderBy(PROJECT_TASKS.createdAt.asc())
                .fetch()
                .stream()
                .map(com.corebuilders.bot.db.DbMappers::projectTask)
                .toList());
    }

    public int memberCount(UUID projectId) {
        Long count = database.query(q -> q.select(PROJECT_MEMBERS.memberId.count())
                .from(PROJECT_MEMBERS)
                .where(PROJECT_MEMBERS.projectId.eq(uuid(projectId)))
                .fetchOne());
        return count == null ? 0 : Math.toIntExact(count);
    }

    public Member completeTask(UUID projectId, UUID taskId, Member actorMember, boolean staffOverride) {
        return database.inTransaction(() -> {
            ProjectTask task = lockTask(projectId, taskId);
            if (task.status() != TaskStatus.OPEN) {
                throw new IllegalStateException("This task is already closed.");
            }
            if (!staffOverride && task.assignedMemberId() != null && !task.assignedMemberId().equals(actorMember.id())) {
                throw new SecurityException("Only the assigned member or staff can complete this task.");
            }

            UUID recipientId = task.assignedMemberId() != null ? task.assignedMemberId() : actorMember.id();
            Member recipient = memberById(recipientId);

            database.query(q -> q.update(PROJECT_TASKS)
                    .set(PROJECT_TASKS.status, TaskStatus.COMPLETED.name())
                    .set(PROJECT_TASKS.completedByMemberId, uuid(actorMember.id()))
                    .set(PROJECT_TASKS.completedAt, now())
                    .where(PROJECT_TASKS.id.eq(uuid(taskId)))
                    .execute());

            if (task.rewardCxp() > 0) {
                ledger.addXp(recipient.id(), task.rewardCxp(), ContributionCategory.BUILDING,
                        SourceType.PROJECT_TASK, taskId, "Completed project task: " + task.title(),
                        actorMember.discordUserId());
            }
            if (task.rewardCredits() > 0) {
                ledger.addCredits(recipient.id(), task.rewardCredits(), SourceType.PROJECT_TASK,
                        taskId, "Completed project task: " + task.title(), actorMember.discordUserId());
            }
            audit.log(actorMember.discordUserId(), "PROJECT_TASK_COMPLETED", recipient.discordUserId(),
                    "PROJECT_TASK", taskId.toString(), task.title());
            return recipient;
        });
    }

    public Project complete(UUID projectId, String actorDiscordId) {
        return database.inTransaction(() -> {
            Project project = get(projectId);
            if (project.status() == ProjectStatus.COMPLETED) {
                throw new IllegalStateException("Project already completed.");
            }
            database.query(q -> q.update(PROJECTS)
                    .set(PROJECTS.status, ProjectStatus.COMPLETED.name())
                    .set(PROJECTS.completedAt, now())
                    .where(PROJECTS.id.eq(uuid(projectId)))
                    .execute());
            audit.log(actorDiscordId, "PROJECT_COMPLETED", project.leadDiscordId(),
                    "PROJECT", projectId.toString(), project.name());
            return get(projectId);
        });
    }

    private ProjectTask getTask(UUID projectId, UUID taskId) {
        return database.query(q -> Optional.ofNullable(q.select(projectTaskColumns())
                        .from(PROJECT_TASKS)
                        .leftJoin(MEMBERS).on(MEMBERS.id.eq(PROJECT_TASKS.assignedMemberId))
                        .where(PROJECT_TASKS.projectId.eq(uuid(projectId)), PROJECT_TASKS.id.eq(uuid(taskId)))
                        .fetchOne())
                .map(com.corebuilders.bot.db.DbMappers::projectTask)
                .orElseThrow(() -> new IllegalArgumentException("Project task not found.")));
    }

    private ProjectTask lockTask(UUID projectId, UUID taskId) {
        String locked = database.query(q -> q.select(PROJECT_TASKS.id)
                .from(PROJECT_TASKS)
                .where(PROJECT_TASKS.projectId.eq(uuid(projectId)), PROJECT_TASKS.id.eq(uuid(taskId)))
                .forUpdate()
                .fetchOne());
        if (locked == null) throw new IllegalArgumentException("Project task not found.");
        return getTask(projectId, taskId);
    }

    private Member memberById(UUID memberId) {
        return database.query(q -> Optional.ofNullable(q.select(memberColumns())
                        .from(MEMBERS)
                        .where(MEMBERS.id.eq(uuid(memberId)))
                        .fetchOne())
                .map(com.corebuilders.bot.db.DbMappers::member)
                .orElseThrow(() -> new IllegalArgumentException("Member profile not found.")));
    }

    private Project lock(UUID id) {
        String locked = database.query(q -> q.select(PROJECTS.id)
                .from(PROJECTS)
                .where(PROJECTS.id.eq(uuid(id)))
                .forUpdate()
                .fetchOne());
        if (locked == null) throw new IllegalArgumentException("Project not found.");
        return get(id);
    }

    private static String limit(String value, int max) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("A value is required.");
        return value.length() <= max ? value : value.substring(0, max);
    }
}
