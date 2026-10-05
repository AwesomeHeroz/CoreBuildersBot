package com.corebuilders.bot.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ApplicationConfigTest {
    @Test
    void loadsSeparateRecruiterAndLeaderReviewRoles() throws Exception {
        ApplicationConfig config = new ApplicationConfig(enabledConfig(false));

        assertEquals(Set.of("111111111111111111"), config.getRecruiterRoleIds());
        assertEquals(Set.of("111111111111111144"), config.getLeaderRoleIds());
        assertEquals(Set.of("111111111111111111", "111111111111111144"), config.getAllReviewRoleIds());
        assertTrue(config.getFirstLevelAcceptedMessage().contains("leader"));
    }

    @Test
    void supportsLegacyReviewerRolesOnlyAsRecruiterFallback() throws Exception {
        YamlConfiguration yaml = enabledConfig(true);
        ApplicationConfig config = new ApplicationConfig(yaml);

        assertEquals(Set.of("111111111111111111"), config.getRecruiterRoleIds());
        assertEquals(Set.of("111111111111111144"), config.getLeaderRoleIds());
    }

    @Test
    void rejectsMissingLeaderRoleBecauseFinalApprovalMustBeLeaderOnly() throws Exception {
        YamlConfiguration yaml = enabledConfig(false);
        yaml.set("discord.permissions.leadership-role-ids", null);

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> new ApplicationConfig(yaml));
        assertTrue(error.getMessage().contains("leadership-role-ids"));
    }

    @Test
    void rejectsSameRoleIdAtBothReviewLevels() throws Exception {
        YamlConfiguration yaml = enabledConfig(false);
        yaml.set("discord.permissions.leadership-role-ids", java.util.List.of("111111111111111111"));

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> new ApplicationConfig(yaml));
        assertTrue(error.getMessage().contains("must be different"));
    }

    private static YamlConfiguration enabledConfig(boolean legacyRecruiterKey) throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("""
                applications:
                  enabled: true
                  channels:
                    pending: pending
                    accepted: accepted
                    rejected: denied
                  tickets:
                    category: application-tickets
                  leader-role-ids:
                    - "222222222222222222"
                  approval:
                    role-id: "333333333333333333"
                  questions:
                    - id: minecraft_name
                      type: text
                      label: Minecraft name
                      required: true
                """);
        String recruiterPath = legacyRecruiterKey
                ? "applications.reviewer-role-ids"
                : "applications.recruiter-role-ids";
        yaml.set(recruiterPath, java.util.List.of("111111111111111111"));
        yaml.set("discord.permissions.leadership-role-ids", java.util.List.of("111111111111111144"));
        return yaml;
    }
}
