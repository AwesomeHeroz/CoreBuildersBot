package com.corebuilders.bot.application;

import com.corebuilders.bot.model.Domain.ApplicationStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ApplicationReviewPolicyTest {
    @Test
    void onlyRecruiterPendingAndLeaderReviewAreActive() {
        assertTrue(ApplicationReviewPolicy.isActive(ApplicationStatus.PENDING));
        assertTrue(ApplicationReviewPolicy.isActive(ApplicationStatus.LEADER_REVIEW));
        assertFalse(ApplicationReviewPolicy.isActive(ApplicationStatus.ACCEPTED));
        assertFalse(ApplicationReviewPolicy.isActive(ApplicationStatus.REJECTED));
    }

    @Test
    void recruiterActionsAreRestrictedToFirstStage() {
        assertDoesNotThrow(() -> ApplicationReviewPolicy.requireRecruiterReview(ApplicationStatus.PENDING));
        assertThrows(IllegalStateException.class,
                () -> ApplicationReviewPolicy.requireRecruiterReview(ApplicationStatus.LEADER_REVIEW));
        assertThrows(IllegalStateException.class,
                () -> ApplicationReviewPolicy.requireRecruiterReview(ApplicationStatus.ACCEPTED));
        assertThrows(IllegalStateException.class,
                () -> ApplicationReviewPolicy.requireRecruiterReview(ApplicationStatus.REJECTED));
    }

    @Test
    void leaderActionsAreRestrictedToSecondStage() {
        assertDoesNotThrow(() -> ApplicationReviewPolicy.requireLeaderReview(ApplicationStatus.LEADER_REVIEW));
        assertThrows(IllegalStateException.class,
                () -> ApplicationReviewPolicy.requireLeaderReview(ApplicationStatus.PENDING));
        assertThrows(IllegalStateException.class,
                () -> ApplicationReviewPolicy.requireLeaderReview(ApplicationStatus.ACCEPTED));
        assertThrows(IllegalStateException.class,
                () -> ApplicationReviewPolicy.requireLeaderReview(ApplicationStatus.REJECTED));
    }

    @Test
    void transitionMapRequiresBothAcceptanceLevels() {
        assertEquals(ApplicationStatus.LEADER_REVIEW,
                ApplicationReviewPolicy.recruiterAccept(ApplicationStatus.PENDING));
        assertEquals(ApplicationStatus.ACCEPTED,
                ApplicationReviewPolicy.leaderAccept(ApplicationStatus.LEADER_REVIEW));
        assertEquals(ApplicationStatus.REJECTED,
                ApplicationReviewPolicy.recruiterReject(ApplicationStatus.PENDING));
        assertEquals(ApplicationStatus.REJECTED,
                ApplicationReviewPolicy.leaderReject(ApplicationStatus.LEADER_REVIEW));

        assertThrows(IllegalStateException.class,
                () -> ApplicationReviewPolicy.leaderAccept(ApplicationStatus.PENDING));
        assertThrows(IllegalStateException.class,
                () -> ApplicationReviewPolicy.recruiterAccept(ApplicationStatus.LEADER_REVIEW));
    }

    @Test
    void statusLabelsMakeBothPendingStagesUnambiguous() {
        assertEquals("PENDING RECRUITER REVIEW", ApplicationReviewPolicy.displayStatus(ApplicationStatus.PENDING));
        assertEquals("PENDING LEADER REVIEW", ApplicationReviewPolicy.displayStatus(ApplicationStatus.LEADER_REVIEW));
    }
}
