CREATE TABLE discord_web_login_challenges (
    id CHAR(36) PRIMARY KEY,
    browser_token_hash CHAR(64) NOT NULL UNIQUE,
    verification_code_hash CHAR(64) NOT NULL UNIQUE,
    member_id CHAR(36) NULL,
    discord_user_id VARCHAR(32) NULL,
    discord_username VARCHAR(100) NULL,
    discord_avatar_url VARCHAR(1000) NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    verified_at TIMESTAMP(6) NULL,
    consumed_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_discord_web_login_member
        FOREIGN KEY (member_id) REFERENCES members(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_discord_web_login_expiry ON discord_web_login_challenges(expires_at);
CREATE INDEX idx_discord_web_login_member ON discord_web_login_challenges(member_id, created_at);
