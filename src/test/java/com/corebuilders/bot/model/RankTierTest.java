package com.corebuilders.bot.model;

import org.junit.jupiter.api.Test;

import static com.corebuilders.bot.model.Domain.RankTier;
import static org.junit.jupiter.api.Assertions.assertEquals;

class RankTierTest {
    @Test
    void resolvesRanksAtBoundaries() {
        assertEquals(RankTier.RECRUIT, RankTier.fromXp(0));
        assertEquals(RankTier.MEMBER, RankTier.fromXp(500));
        assertEquals(RankTier.CONTRIBUTOR, RankTier.fromXp(1_500));
        assertEquals(RankTier.VETERAN, RankTier.fromXp(4_000));
        assertEquals(RankTier.ELITE, RankTier.fromXp(8_000));
        assertEquals(RankTier.NOBLE, RankTier.fromXp(15_000));
        assertEquals(RankTier.CORE_LEGEND, RankTier.fromXp(30_000));
    }
}
