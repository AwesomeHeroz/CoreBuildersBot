package com.corebuilders.bot.db;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class MarketplaceMigrationTest {
    @Test
    void securityMigrationDefinesVersioningEscrowAndSessionInvalidationState() throws IOException {
        try (var input = getClass().getResourceAsStream("/db/migration/V8__marketplace_security_hardening.sql")) {
            assertNotNull(input);
            String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(sql.contains("security_version BIGINT"));
            assertTrue(sql.contains("version BIGINT"));
            assertTrue(sql.contains("funds_released BOOLEAN"));
            assertTrue(sql.contains("buyer_confirmed_at"));
            assertTrue(sql.contains("disputed_at"));
            assertTrue(sql.contains("resolved_at"));
            assertTrue(sql.contains("resolution VARCHAR"));
            assertTrue(sql.contains("resolution_note"));
            assertTrue(sql.contains("UPDATE marketplace_order_items SET funds_released = TRUE"));
            assertTrue(sql.contains("idx_marketplace_line_dispute_queue"));
        }
    }

    @Test
    void discordTicketMigrationAddsLifecycleAndCancellationState() throws IOException {
        try (var input = getClass().getResourceAsStream("/db/migration/V11__marketplace_discord_tickets.sql")) {
            assertNotNull(input);
            String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(sql.contains("seller_confirmed_at"));
            assertTrue(sql.contains("cancelled_at"));
            assertTrue(sql.contains("cancelled_by"));
            assertTrue(sql.contains("discord_ticket_state"));
            assertTrue(sql.contains("discord_channel_id"));
            assertTrue(sql.contains("discord_message_id"));
            assertTrue(sql.contains("idx_marketplace_ticket_queue"));
        }
    }

    @Test
    void economyTerminologyMigrationUpdatesAchievementDescriptions() throws IOException {
        try (var input = getClass().getResourceAsStream("/db/migration/V10__rename_economy_terms.sql")) {
            assertNotNull(input);
            String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(sql.contains("Building points"));
            assertTrue(sql.contains("Infrastructure points"));
            assertTrue(sql.contains("lifetime points"));
            assertTrue(sql.contains("WHERE code = 'CORE_LEGEND'"));
        }
    }

    @Test
    void discordBotLoginMigrationStoresOnlyHashedOneTimeSecrets() throws IOException {
        try (var input = getClass().getResourceAsStream("/db/migration/V9__discord_bot_web_login.sql")) {
            assertNotNull(input);
            String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(sql.contains("discord_web_login_challenges"));
            assertTrue(sql.contains("browser_token_hash CHAR(64)"));
            assertTrue(sql.contains("verification_code_hash CHAR(64)"));
            assertTrue(sql.contains("consumed_at TIMESTAMP(6)"));
            assertTrue(sql.contains("FOREIGN KEY (member_id) REFERENCES members(id)"));
        }
    }
}
