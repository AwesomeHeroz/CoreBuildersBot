package com.corebuilders.bot.db;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class ApplicationReviewMigrationTest {
    @Test
    void migrationStoresFirstLevelReviewerWithoutOverwritingFinalDecisionFields() throws Exception {
        try (var input = getClass().getResourceAsStream("/db/migration/V13__two_level_application_review.sql")) {
            assertNotNull(input);
            String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(sql.contains("first_reviewer_discord_id"));
            assertTrue(sql.contains("first_reviewed_at"));
            assertTrue(sql.contains("ALTER TABLE applications"));
        }
    }
}
