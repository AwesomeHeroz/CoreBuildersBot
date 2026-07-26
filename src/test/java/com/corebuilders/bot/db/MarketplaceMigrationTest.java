package com.corebuilders.bot.db;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class MarketplaceMigrationTest {
    @Test
    void migrationDefinesOneShopPerMemberAndTransactionalOrderTables() throws IOException {
        try (var input = getClass().getResourceAsStream("/db/migration/V6__player_marketplace.sql")) {
            assertNotNull(input);
            String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(sql.contains("owner_member_id CHAR(36) NOT NULL UNIQUE"));
            assertTrue(sql.contains("CREATE TABLE marketplace_cart_items"));
            assertTrue(sql.contains("CREATE TABLE marketplace_orders"));
            assertTrue(sql.contains("CREATE TABLE marketplace_order_items"));
            assertTrue(sql.contains("idx_marketplace_item_public"));
            assertTrue(sql.contains("idx_marketplace_sales"));
        }
    }
}
