package com.corebuilders.bot.db;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class SpawnHelpHistoryMigrationTest {
    @Test
    void migrationAddsIndexesForRequesterHistoryAndCompletedHelperLeaderboard() throws Exception {
        try (var input = getClass().getResourceAsStream(
                "/db/migration/V14__spawn_help_history_and_leaderboard_indexes.sql")) {
            assertNotNull(input);
            String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(sql.contains("idx_spawn_help_ign_created"));
            assertTrue(sql.contains("ingame_name, created_at"));
            assertTrue(sql.contains("idx_spawn_help_helper_completed"));
            assertTrue(sql.contains("status, helper_discord_id, closed_at"));
        }
    }
}
