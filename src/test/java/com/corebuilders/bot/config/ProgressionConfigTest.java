package com.corebuilders.bot.config;

import com.corebuilders.bot.model.RankCatalog;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProgressionConfigTest {
    @Test
    void loadsConfiguredRanksAndLevelSize() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString("""
                progression:
                  xp-per-level: 200
                  ranks:
                    - code: recruit
                      name: Recruit
                      minimum-xp: 0
                    - code: builder
                      name: Builder
                      minimum-xp: 800
                      discord-role-id: "123456789012345678"
                """);

        RankCatalog catalog = ProgressionConfig.from(config);

        assertEquals(200, catalog.xpPerLevel());
        assertEquals("RECRUIT", catalog.rankForXp(799).code());
        assertEquals("BUILDER", catalog.rankForXp(800).code());
        assertEquals("123456789012345678", catalog.rankForXp(800).discordRoleId());
    }

    @Test
    void missingRanksUseBackwardCompatibleDefaults() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("progression.xp-per-level", 1000);

        RankCatalog catalog = ProgressionConfig.from(config);

        assertEquals("CORE_LEGEND", catalog.rankForXp(30_000).code());
        assertEquals(30, catalog.levelForXp(30_000));
    }

    @Test
    void rejectsMissingRequiredFields() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString("""
                progression:
                  ranks:
                    - name: Recruit
                      minimum-xp: 0
                """);

        assertThrows(IllegalArgumentException.class, () -> ProgressionConfig.from(config));
    }
}
