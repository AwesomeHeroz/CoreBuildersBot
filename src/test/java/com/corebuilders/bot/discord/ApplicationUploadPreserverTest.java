package com.corebuilders.bot.discord;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ApplicationUploadPreserverTest {
    @Test
    void acceptsOnlyHttpsDiscordCdnUrls() {
        assertDoesNotThrow(() -> ApplicationUploadPreserver.validateDiscordAttachmentUri(
                URI.create("https://cdn.discordapp.com/attachments/1/2/file.png")
        ));
        assertThrows(IllegalArgumentException.class, () -> ApplicationUploadPreserver.validateDiscordAttachmentUri(
                URI.create("http://cdn.discordapp.com/attachments/1/2/file.png")
        ));
        assertThrows(IllegalArgumentException.class, () -> ApplicationUploadPreserver.validateDiscordAttachmentUri(
                URI.create("https://evil.example/file.png")
        ));
        assertThrows(IllegalArgumentException.class, () -> ApplicationUploadPreserver.validateDiscordAttachmentUri(
                URI.create("https://evil.discordapp.com/file.png")
        ));
    }
}
