package com.corebuilders.bot.service;

import com.corebuilders.bot.db.QueryDslDatabase;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.querydsl.core.Tuple;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.corebuilders.bot.db.DbValues.instant;
import static com.corebuilders.bot.db.DbValues.now;
import static com.corebuilders.bot.db.Schema.BUILD_CHALLENGE_SCORES;
import static com.corebuilders.bot.db.Schema.BUILD_CHALLENGE_SUBMISSIONS;
import static com.corebuilders.bot.db.Schema.BUILD_CHALLENGE_WINNERS;

/** QueryDSL persistence for build-challenge submissions, judge scores, and winners. */
public final class BuildChallengeService {
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};

    public record Submission(UUID id, String discordUserId, String discordUsername, String ign,
                             String coordinates, List<String> screenshotUrls, String channelId,
                             String messageId, Instant createdAt) {}
    public record Score(UUID submissionId, String judgeDiscordId, int score, String notes,
                        Instant createdAt, Instant updatedAt) {}
    public record Standing(Submission submission, double averageScore, int judgeCount) {}
    public record Winner(int place, UUID submissionId, String winnerDiscordId, String claimCode,
                         String claimChannelId, String assignedByDiscordId, Instant assignedAt) {}

    private final QueryDslDatabase database;
    private final ObjectMapper mapper;

    public BuildChallengeService(QueryDslDatabase database, ObjectMapper mapper) {
        this.database = database;
        this.mapper = mapper;
    }

    public Submission create(UUID id, String userId, String username, String ign, String coordinates,
                             List<String> screenshotUrls, boolean preventDuplicate) {
        if (preventDuplicate && latestForUser(userId).isPresent()) {
            throw new IllegalStateException("You already submitted a build challenge entry.");
        }
        try {
            database.query(q -> {
                var insert = q.insert(BUILD_CHALLENGE_SUBMISSIONS)
                        .set(BUILD_CHALLENGE_SUBMISSIONS.id, id.toString())
                        .set(BUILD_CHALLENGE_SUBMISSIONS.discordUserId, userId)
                        .set(BUILD_CHALLENGE_SUBMISSIONS.discordUsername, trim(username, 100))
                        .set(BUILD_CHALLENGE_SUBMISSIONS.ign, trim(ign, 100))
                        .set(BUILD_CHALLENGE_SUBMISSIONS.coordinates, trim(coordinates, 255))
                        .set(BUILD_CHALLENGE_SUBMISSIONS.screenshotsJson, serialize(screenshotUrls))
                        .set(BUILD_CHALLENGE_SUBMISSIONS.createdAt, now());
                if (preventDuplicate) insert.set(BUILD_CHALLENGE_SUBMISSIONS.submissionGuard, userId);
                else insert.setNull(BUILD_CHALLENGE_SUBMISSIONS.submissionGuard);
                return insert.execute();
            });
        } catch (RuntimeException error) {
            if (preventDuplicate && database.isDuplicateKey(error)) {
                throw new IllegalStateException("You already submitted a build challenge entry.", error);
            }
            throw error;
        }
        return get(id);
    }

    public Submission setMessage(UUID id, String channelId, String messageId) {
        database.query(q -> q.update(BUILD_CHALLENGE_SUBMISSIONS)
                .set(BUILD_CHALLENGE_SUBMISSIONS.submissionChannelId, channelId)
                .set(BUILD_CHALLENGE_SUBMISSIONS.submissionMessageId, messageId)
                .where(BUILD_CHALLENGE_SUBMISSIONS.id.eq(id.toString()))
                .execute());
        return get(id);
    }

    public Optional<Submission> latestForUser(String userId) {
        return database.query(q -> Optional.ofNullable(q.select(submissionColumns())
                .from(BUILD_CHALLENGE_SUBMISSIONS)
                .where(BUILD_CHALLENGE_SUBMISSIONS.discordUserId.eq(userId))
                .orderBy(BUILD_CHALLENGE_SUBMISSIONS.createdAt.desc()).limit(1).fetchOne()).map(this::mapSubmission));
    }

    public Submission get(UUID id) {
        Tuple row = database.query(q -> q.select(submissionColumns()).from(BUILD_CHALLENGE_SUBMISSIONS)
                .where(BUILD_CHALLENGE_SUBMISSIONS.id.eq(id.toString())).fetchOne());
        if (row == null) throw new IllegalArgumentException("Build challenge submission not found: " + id);
        return mapSubmission(row);
    }

    public void score(UUID submissionId, String judgeId, int value, String notes) {
        if (value < 0 || value > 100) throw new IllegalArgumentException("Score must be from 0 to 100.");
        get(submissionId);
        Integer exists = database.query(q -> q.selectOne().from(BUILD_CHALLENGE_SCORES)
                .where(BUILD_CHALLENGE_SCORES.submissionId.eq(submissionId.toString())
                        .and(BUILD_CHALLENGE_SCORES.judgeDiscordId.eq(judgeId))).fetchFirst());
        if (exists == null) {
            database.query(q -> q.insert(BUILD_CHALLENGE_SCORES)
                    .set(BUILD_CHALLENGE_SCORES.submissionId, submissionId.toString())
                    .set(BUILD_CHALLENGE_SCORES.judgeDiscordId, judgeId)
                    .set(BUILD_CHALLENGE_SCORES.score, value)
                    .set(BUILD_CHALLENGE_SCORES.notes, trim(notes, 1000))
                    .set(BUILD_CHALLENGE_SCORES.createdAt, now())
                    .set(BUILD_CHALLENGE_SCORES.updatedAt, now()).execute());
        } else {
            database.query(q -> q.update(BUILD_CHALLENGE_SCORES)
                    .set(BUILD_CHALLENGE_SCORES.score, value)
                    .set(BUILD_CHALLENGE_SCORES.notes, trim(notes, 1000))
                    .set(BUILD_CHALLENGE_SCORES.updatedAt, now())
                    .where(BUILD_CHALLENGE_SCORES.submissionId.eq(submissionId.toString())
                            .and(BUILD_CHALLENGE_SCORES.judgeDiscordId.eq(judgeId))).execute());
        }
    }

    public List<Standing> standings() {
        List<Submission> submissions = database.query(q -> q.select(submissionColumns())
                .from(BUILD_CHALLENGE_SUBMISSIONS).orderBy(BUILD_CHALLENGE_SUBMISSIONS.createdAt.asc()).fetch())
                .stream().map(this::mapSubmission).toList();
        List<Tuple> scores = database.query(q -> q.select(BUILD_CHALLENGE_SCORES.submissionId, BUILD_CHALLENGE_SCORES.score)
                .from(BUILD_CHALLENGE_SCORES).fetch());
        List<Standing> out = new ArrayList<>();
        for (Submission submission : submissions) {
            int count = 0;
            int total = 0;
            for (Tuple row : scores) {
                if (submission.id().toString().equals(row.get(BUILD_CHALLENGE_SCORES.submissionId))) {
                    Integer score = row.get(BUILD_CHALLENGE_SCORES.score);
                    if (score != null) { total += score; count++; }
                }
            }
            if (count > 0) out.add(new Standing(submission, total / (double) count, count));
        }
        out.sort(Comparator.comparingDouble(Standing::averageScore).reversed()
                .thenComparing(s -> s.submission().createdAt()));
        return List.copyOf(out);
    }

    public Winner assignWinner(int place, Submission submission, String code, String channelId, String actorId) {
        if (place < 1 || place > 3) throw new IllegalArgumentException("Winner place must be 1, 2, or 3.");
        try {
            database.query(q -> q.insert(BUILD_CHALLENGE_WINNERS)
                    .set(BUILD_CHALLENGE_WINNERS.placeNo, place)
                    .set(BUILD_CHALLENGE_WINNERS.submissionId, submission.id().toString())
                    .set(BUILD_CHALLENGE_WINNERS.winnerDiscordId, submission.discordUserId())
                    .set(BUILD_CHALLENGE_WINNERS.claimCode, code)
                    .set(BUILD_CHALLENGE_WINNERS.claimChannelId, channelId)
                    .set(BUILD_CHALLENGE_WINNERS.assignedByDiscordId, actorId)
                    .set(BUILD_CHALLENGE_WINNERS.assignedAt, now()).execute());
        } catch (RuntimeException error) {
            if (database.isDuplicateKey(error)) {
                throw new IllegalStateException("That place or submission has already been assigned as a winner.", error);
            }
            throw error;
        }
        return winner(place).orElseThrow();
    }

    public Optional<Winner> winner(int place) {
        return database.query(q -> Optional.ofNullable(q.select(
                        BUILD_CHALLENGE_WINNERS.placeNo, BUILD_CHALLENGE_WINNERS.submissionId,
                        BUILD_CHALLENGE_WINNERS.winnerDiscordId, BUILD_CHALLENGE_WINNERS.claimCode,
                        BUILD_CHALLENGE_WINNERS.claimChannelId, BUILD_CHALLENGE_WINNERS.assignedByDiscordId,
                        BUILD_CHALLENGE_WINNERS.assignedAt)
                .from(BUILD_CHALLENGE_WINNERS).where(BUILD_CHALLENGE_WINNERS.placeNo.eq(place)).fetchOne())
                .map(r -> new Winner(r.get(BUILD_CHALLENGE_WINNERS.placeNo),
                        UUID.fromString(r.get(BUILD_CHALLENGE_WINNERS.submissionId)),
                        r.get(BUILD_CHALLENGE_WINNERS.winnerDiscordId), r.get(BUILD_CHALLENGE_WINNERS.claimCode),
                        r.get(BUILD_CHALLENGE_WINNERS.claimChannelId), r.get(BUILD_CHALLENGE_WINNERS.assignedByDiscordId),
                        instant(r.get(BUILD_CHALLENGE_WINNERS.assignedAt)))));
    }

    private com.querydsl.core.types.Expression<?>[] submissionColumns() {
        return new com.querydsl.core.types.Expression<?>[]{BUILD_CHALLENGE_SUBMISSIONS.id,
                BUILD_CHALLENGE_SUBMISSIONS.discordUserId, BUILD_CHALLENGE_SUBMISSIONS.discordUsername,
                BUILD_CHALLENGE_SUBMISSIONS.ign, BUILD_CHALLENGE_SUBMISSIONS.coordinates,
                BUILD_CHALLENGE_SUBMISSIONS.screenshotsJson, BUILD_CHALLENGE_SUBMISSIONS.submissionChannelId,
                BUILD_CHALLENGE_SUBMISSIONS.submissionMessageId, BUILD_CHALLENGE_SUBMISSIONS.createdAt};
    }

    private Submission mapSubmission(Tuple row) {
        return new Submission(UUID.fromString(row.get(BUILD_CHALLENGE_SUBMISSIONS.id)),
                row.get(BUILD_CHALLENGE_SUBMISSIONS.discordUserId), row.get(BUILD_CHALLENGE_SUBMISSIONS.discordUsername),
                row.get(BUILD_CHALLENGE_SUBMISSIONS.ign), row.get(BUILD_CHALLENGE_SUBMISSIONS.coordinates),
                deserialize(row.get(BUILD_CHALLENGE_SUBMISSIONS.screenshotsJson)),
                row.get(BUILD_CHALLENGE_SUBMISSIONS.submissionChannelId), row.get(BUILD_CHALLENGE_SUBMISSIONS.submissionMessageId),
                instant(row.get(BUILD_CHALLENGE_SUBMISSIONS.createdAt)));
    }

    private String serialize(List<String> values) {
        try { return mapper.writeValueAsString(values == null ? List.of() : values); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Could not store screenshot URLs.", e); }
    }
    private List<String> deserialize(String json) {
        try { return json == null || json.isBlank() ? List.of() : mapper.readValue(json, STRING_LIST); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Could not read screenshot URLs.", e); }
    }
    private static String trim(String value, int max) { String v = value == null ? "" : value.trim(); return v.length() <= max ? v : v.substring(0, max); }
}
