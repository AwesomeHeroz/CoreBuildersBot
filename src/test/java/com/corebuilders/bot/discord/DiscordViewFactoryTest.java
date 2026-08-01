package com.corebuilders.bot.discord;

import com.corebuilders.bot.model.Domain.*;
import com.corebuilders.bot.model.Models.Contribution;
import com.corebuilders.bot.model.Models.Project;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DiscordViewFactoryTest {
    private final DiscordViewFactory views = new DiscordViewFactory();

    @Test
    void projectListShowsEmptyState() {
        var embed = views.projectList(List.of());
        assertEquals("Active Core Builders Projects", embed.getTitle());
        assertEquals("No active projects.", embed.getDescription());
    }

    @Test
    void contributionReviewTreatsMissingAwardsAsZero() {
        Contribution contribution = new Contribution(
                UUID.randomUUID(), UUID.randomUUID(), "42", "Player",
                ContributionCategory.BUILDING, "Built a farm", null, null,
                ContributionStatus.REJECTED, 100, 20,
                null, null, "99", "Not complete", Instant.EPOCH
        );

        var embed = views.contributionReviewed(contribution);
        assertTrue(embed.getFields().stream().anyMatch(field -> "0 points • 0 coins".equals(field.getValue())));
    }

}
