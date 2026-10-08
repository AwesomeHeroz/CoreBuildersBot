CREATE TABLE build_challenge_submissions (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    discord_user_id VARCHAR(32) NOT NULL,
    discord_username VARCHAR(100) NOT NULL,
    submission_guard VARCHAR(32) NULL,
    ign VARCHAR(100) NOT NULL,
    coordinates VARCHAR(255) NOT NULL,
    screenshots_json MEDIUMTEXT NOT NULL,
    submission_channel_id VARCHAR(32) NULL,
    submission_message_id VARCHAR(32) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uq_build_challenge_submission_guard (submission_guard),
    INDEX idx_build_challenge_user (discord_user_id),
    INDEX idx_build_challenge_created (created_at)
);

CREATE TABLE build_challenge_scores (
    submission_id VARCHAR(36) NOT NULL,
    judge_discord_id VARCHAR(32) NOT NULL,
    score INT NOT NULL,
    notes VARCHAR(1000) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (submission_id, judge_discord_id),
    CONSTRAINT fk_build_challenge_score_submission FOREIGN KEY (submission_id)
        REFERENCES build_challenge_submissions(id) ON DELETE CASCADE,
    INDEX idx_build_challenge_scores_submission (submission_id)
);

CREATE TABLE build_challenge_winners (
    place_no INT NOT NULL PRIMARY KEY,
    submission_id VARCHAR(36) NOT NULL,
    winner_discord_id VARCHAR(32) NOT NULL,
    claim_code VARCHAR(100) NOT NULL,
    claim_channel_id VARCHAR(32) NOT NULL,
    assigned_by_discord_id VARCHAR(32) NOT NULL,
    assigned_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uq_build_challenge_winner_submission (submission_id),
    UNIQUE KEY uq_build_challenge_claim_code (claim_code),
    CONSTRAINT fk_build_challenge_winner_submission FOREIGN KEY (submission_id)
        REFERENCES build_challenge_submissions(id) ON DELETE RESTRICT
);
