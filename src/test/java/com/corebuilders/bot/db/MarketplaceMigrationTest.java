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
}
