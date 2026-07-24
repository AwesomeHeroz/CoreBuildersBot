package com.corebuilders.bot.discord;

import com.corebuilders.bot.model.Domain.ApplicationStatus;
import com.corebuilders.bot.model.Models.ApplicationFile;
import com.corebuilders.bot.model.Models.ApplicationRecord;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ApplicationTextFormatterTest {

    @Test
    void createsSafeTicketNamesFromConfiguredPattern() {
        UUID id = UUID.fromString("12345678-1234-1234-1234-123456789abc");
        ApplicationTextFormatter formatter = new ApplicationTextFormatter("Application {username} {id}");

        assertEquals("application-some-player-12345678", formatter.ticketName(application(id, "Some Player", null)));
    }

    @Test
    void replacesApplicationMessagePlaceholders() {
        UUID id = UUID.fromString("12345678-1234-1234-1234-123456789abc");
        ApplicationRecord application = application(id, "Player", "999");
        ApplicationTextFormatter formatter = new ApplicationTextFormatter(null);

        String result = formatter.message(
                "{user}|{username}|{application_id}|{reason}|{ticket}|{reviewer}|{role}",
                application,
                "<@reviewer>",
                "Member",
                "Looks good"
        );

        assertEquals(
                "<@42>|Player|12345678-1234-1234-1234-123456789abc|Looks good|<#999>|<@reviewer>|Member",
                result
        );
    }

    @Test
    void formatsUploadsAndSanitizesFileNames() {
        ApplicationTextFormatter formatter = new ApplicationTextFormatter(null);
        ApplicationFile file = new ApplicationFile(
                "evidence", "Evidence", "build[1].png", "image/png", 1536,
                "https://example.test/file", "message"
        );

        assertTrue(formatter.formatFiles(List.of(file)).contains("build\\[1\\].png"));
        assertTrue(formatter.formatFiles(List.of(file)).contains("1.5 KiB"));
        assertEquals("bad_name.png", formatter.safeFileName("bad\nname.png"));
        assertEquals("application-upload", formatter.safeFileName(""));
        assertNull(formatter.blankToNull("   "));
        assertEquals("value", formatter.blankToNull("value"));
    }

    private static ApplicationRecord application(UUID id, String username, String ticketChannelId) {
        return new ApplicationRecord(
                id,
                "42",
                username,
                ApplicationStatus.PENDING,
                List.of(),
                null,
                null,
                ticketChannelId,
                null,
                "Stored reason",
                Instant.ofEpochSecond(100),
                null
        );
    }
}
