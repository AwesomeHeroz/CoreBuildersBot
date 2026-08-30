ALTER TABLE applications
    ADD COLUMN first_reviewer_discord_id VARCHAR(32) NULL AFTER ticket_channel_id,
    ADD COLUMN first_reviewed_at TIMESTAMP(6) NULL AFTER first_reviewer_discord_id;

CREATE INDEX idx_applications_first_reviewer
    ON applications(first_reviewer_discord_id, first_reviewed_at);
