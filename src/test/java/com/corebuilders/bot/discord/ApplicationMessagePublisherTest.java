package com.corebuilders.bot.discord;

import com.corebuilders.bot.model.Domain.ApplicationStatus;
import com.corebuilders.bot.model.Models.ApplicationRecord;
import net.dv8tion.jda.api.components.buttons.Button;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ApplicationMessagePublisherTest {
    private static final UUID ID = UUID.fromString("12345678-1234-1234-1234-123456789abc");

    @Test
    void firstStageShowsRecruiterControlsOnly() {
        List<Button> buttons = ApplicationMessagePublisher.reviewButtons(application(ApplicationStatus.PENDING));

        assertEquals(List.of(
                "app:recruiter-approve:" + ID,
                "app:recruiter-reject:" + ID,
                "app:ticket:" + ID
        ), buttons.stream().map(Button::getCustomId).toList());
    }

    @Test
    void secondStageShowsLeaderControlsOnly() {
        List<Button> buttons = ApplicationMessagePublisher.reviewButtons(application(ApplicationStatus.LEADER_REVIEW));

        assertEquals(List.of(
                "app:leader-approve:" + ID,
                "app:leader-reject:" + ID,
                "app:ticket:" + ID
        ), buttons.stream().map(Button::getCustomId).toList());
    }

    @Test
    void terminalApplicationsHaveNoDecisionButtons() {
        assertTrue(ApplicationMessagePublisher.reviewButtons(application(ApplicationStatus.ACCEPTED)).isEmpty());
        assertTrue(ApplicationMessagePublisher.reviewButtons(application(ApplicationStatus.REJECTED)).isEmpty());
    }

    private static ApplicationRecord application(ApplicationStatus status) {
        return new ApplicationRecord(
                ID,
                "444444444444444444",
                "Player",
                status,
                List.of(),
                "555555555555555555",
                "666666666666666666",
                null,
                status == ApplicationStatus.LEADER_REVIEW ? "111111111111111111" : null,
                status == ApplicationStatus.LEADER_REVIEW ? Instant.ofEpochSecond(200) : null,
                null,
                null,
                Instant.ofEpochSecond(100),
                null
        );
    }
}
