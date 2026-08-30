package com.corebuilders.bot.model;

import com.corebuilders.bot.model.Domain.ApplicationStatus;

import java.util.Locale;

/** Pure state policy for the two-level application review workflow. */
public final class ApplicationReviewPolicy {
    private ApplicationReviewPolicy() {}

    public static boolean isActive(ApplicationStatus status) {
        return status == ApplicationStatus.PENDING || status == ApplicationStatus.LEADER_REVIEW;
    }

    public static void requireRecruiterReview(ApplicationStatus status) {
        require(status, ApplicationStatus.PENDING, "recruiter review");
    }

    public static void requireLeaderReview(ApplicationStatus status) {
        require(status, ApplicationStatus.LEADER_REVIEW, "leader review");
    }

    public static ApplicationStatus recruiterAccept(ApplicationStatus status) {
        requireRecruiterReview(status);
        return ApplicationStatus.LEADER_REVIEW;
    }

    public static ApplicationStatus recruiterReject(ApplicationStatus status) {
        requireRecruiterReview(status);
        return ApplicationStatus.REJECTED;
    }

    public static ApplicationStatus leaderAccept(ApplicationStatus status) {
        requireLeaderReview(status);
        return ApplicationStatus.ACCEPTED;
    }

    public static ApplicationStatus leaderReject(ApplicationStatus status) {
        requireLeaderReview(status);
        return ApplicationStatus.REJECTED;
    }

    public static String displayStatus(ApplicationStatus status) {
        return switch (status) {
            case PENDING -> "PENDING RECRUITER REVIEW";
            case LEADER_REVIEW -> "PENDING LEADER REVIEW";
            case ACCEPTED -> "ACCEPTED";
            case REJECTED -> "REJECTED";
        };
    }

    private static void require(ApplicationStatus actual, ApplicationStatus expected, String stage) {
        if (actual == expected) return;
        if (actual == ApplicationStatus.ACCEPTED || actual == ApplicationStatus.REJECTED) {
            throw new IllegalStateException("This application has already been "
                    + actual.name().toLowerCase(Locale.ROOT) + ".");
        }
        throw new IllegalStateException("This application is currently in " + displayStatus(actual).toLowerCase(Locale.ROOT)
                + " and cannot be handled as " + stage + ".");
    }
}
