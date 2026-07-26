package com.corebuilders.bot.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RankCatalogTest {
    @Test
    void sortsRanksAndResolvesBoundaries() {
        RankDefinition recruit = new RankDefinition("recruit", "Recruit", 0, null);
        RankDefinition member = new RankDefinition("member", "Member", 500, null);
        RankDefinition veteran = new RankDefinition("veteran", "Veteran", 4_000, null);
        RankCatalog catalog = new RankCatalog(List.of(veteran, recruit, member), 250);

        assertEquals(List.of(recruit, member, veteran), catalog.ranks());
        assertEquals(recruit, catalog.rankForXp(-1));
        assertEquals(recruit, catalog.rankForXp(499));
        assertEquals(member, catalog.rankForXp(500));
        assertEquals(veteran, catalog.rankForXp(4_000));
        assertEquals(member, catalog.next(recruit));
        assertNull(catalog.next(veteran));
        assertEquals(16, catalog.levelForXp(4_000));
    }

    @Test
    void defaultsPreserveOriginalProgression() {
        RankCatalog catalog = RankCatalog.defaults();

        assertEquals("RECRUIT", catalog.rankForXp(0).code());
        assertEquals("MEMBER", catalog.rankForXp(500).code());
        assertEquals("CONTRIBUTOR", catalog.rankForXp(1_500).code());
        assertEquals("VETERAN", catalog.rankForXp(4_000).code());
        assertEquals("ELITE", catalog.rankForXp(8_000).code());
        assertEquals("NOBLE", catalog.rankForXp(15_000).code());
        assertEquals("CORE_LEGEND", catalog.rankForXp(30_000).code());
    }

    @Test
    void rejectsInvalidCatalogs() {
        assertThrows(IllegalArgumentException.class, () -> new RankCatalog(List.of(), 500));
        assertThrows(IllegalArgumentException.class, () -> new RankCatalog(
                List.of(new RankDefinition("MEMBER", "Member", 500, null)), 500
        ));
        assertThrows(IllegalArgumentException.class, () -> new RankCatalog(List.of(
                new RankDefinition("RECRUIT", "Recruit", 0, null),
                new RankDefinition("RECRUIT", "Member", 500, null)
        ), 500));
        assertThrows(IllegalArgumentException.class, () -> new RankCatalog(List.of(
                new RankDefinition("RECRUIT", "Recruit", 0, null),
                new RankDefinition("MEMBER", "recruit", 500, null)
        ), 500));
        assertThrows(IllegalArgumentException.class, () -> new RankCatalog(List.of(
                new RankDefinition("RECRUIT", "Recruit", 0, null),
                new RankDefinition("MEMBER", "Member", 0, null)
        ), 500));
        assertThrows(IllegalArgumentException.class, () -> new RankCatalog(
                List.of(new RankDefinition("RECRUIT", "Recruit", 0, null)), 0
        ));
    }

    @Test
    void validatesDiscordRoleIds() {
        assertThrows(IllegalArgumentException.class,
                () -> new RankDefinition("RECRUIT", "Recruit", 0, "not-a-role"));
        assertDoesNotThrow(
                () -> new RankDefinition("RECRUIT", "Recruit", 0, "123456789012345678")
        );
    }
}
