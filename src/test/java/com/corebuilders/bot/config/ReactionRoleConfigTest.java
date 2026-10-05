package com.corebuilders.bot.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ReactionRoleConfigTest {
    @Test
    void loadsUnicodeAndCustomEmojiMappings() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("""
                discord:
                  reaction-roles:
                    enabled: true
                    channel: "123456789012345678"
                    message-id: "223456789012345678"
                    remove-role-on-reaction-remove: true
                    mappings:
                      - reaction: "⚔️"
                        role-id: "323456789012345678"
                      - reaction: "core:423456789012345678"
                        role-id: "523456789012345678"
                """);

        ReactionRoleConfig config = new ReactionRoleConfig(yaml);

        assertTrue(config.enabled());
        assertEquals("323456789012345678", config.roleIdForReaction("⚔️"));
        assertEquals("523456789012345678", config.roleIdForReaction("core:423456789012345678"));
        assertEquals("523456789012345678", config.roleIdForReaction("<:core:423456789012345678>"));
        assertTrue(config.removeRoleOnReactionRemove());
    }

    @Test
    void supportsCustomEmojiIdOnly() throws Exception {
        YamlConfiguration yaml = baseConfig();
        yaml.set("discord.reaction-roles.mappings", java.util.List.of(java.util.Map.of(
                "reaction", "423456789012345678",
                "role-id", "523456789012345678"
        )));

        ReactionRoleConfig config = new ReactionRoleConfig(yaml);
        assertEquals("523456789012345678", config.roleIdForReaction("emoji_name:423456789012345678"));
    }

    @Test
    void rejectsDuplicateEquivalentCustomEmojiMappings() throws Exception {
        YamlConfiguration yaml = baseConfig();
        yaml.set("discord.reaction-roles.mappings", java.util.List.of(
                java.util.Map.of("reaction", "core:423456789012345678", "role-id", "523456789012345678"),
                java.util.Map.of("reaction", "<:renamed:423456789012345678>", "role-id", "623456789012345678")
        ));

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> new ReactionRoleConfig(yaml));
        assertTrue(error.getMessage().contains("Duplicate reaction mapping"));
    }

    @Test
    void disabledConfigurationDoesNotRequireMessageOrMappings() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("discord.reaction-roles.enabled", false);
        assertDoesNotThrow(() -> new ReactionRoleConfig(yaml));
    }

    private static YamlConfiguration baseConfig() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("discord.reaction-roles.enabled", true);
        yaml.set("discord.reaction-roles.channel", "123456789012345678");
        yaml.set("discord.reaction-roles.message-id", "223456789012345678");
        return yaml;
    }
}
