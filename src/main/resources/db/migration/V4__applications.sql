CREATE TABLE applications (
    id CHAR(36) PRIMARY KEY,
    discord_user_id VARCHAR(32) NOT NULL,
    username VARCHAR(100) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    answers_json LONGTEXT NOT NULL,
    pending_channel_id VARCHAR(32),
    pending_message_id VARCHAR(32),
    ticket_channel_id VARCHAR(32),
    reviewer_discord_id VARCHAR(32),
    review_reason VARCHAR(1000),
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    reviewed_at TIMESTAMP(6) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_applications_user_created ON applications(discord_user_id, created_at);
CREATE INDEX idx_applications_status_created ON applications(status, created_at);
