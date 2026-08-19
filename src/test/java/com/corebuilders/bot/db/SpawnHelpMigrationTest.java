package com.corebuilders.bot.db;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class SpawnHelpMigrationTest {
    @Test
    void migrationDefinesPersistentLifecycleAndSingleActiveTicketGuard() throws Exception {
        try (var input = getClass().getResourceAsStream("/db/migration/V12__spawn_help_tickets.sql")) {
            assertNotNull(input);
            String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(sql.contains("CREATE TABLE spawn_help_tickets"));
            assertTrue(sql.contains("active_guard VARCHAR(32) NULL"));
            assertTrue(sql.contains("UNIQUE KEY uq_spawn_help_active_guard"));
            assertTrue(sql.contains("status VARCHAR(32) NOT NULL DEFAULT 'OPEN'"));
            assertTrue(sql.contains("helper_discord_id"));
            assertTrue(sql.contains("denial_reason"));
            assertTrue(sql.contains("channel_id"));
            assertTrue(sql.contains("control_message_id"));
            assertTrue(sql.contains("closed_at"));
        }
    }
}
