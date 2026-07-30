ALTER TABLE members
    MODIFY COLUMN discord_user_id VARCHAR(32) NULL,
    ADD COLUMN discord_username VARCHAR(100) NULL AFTER discord_user_id,
    ADD COLUMN discord_avatar_url VARCHAR(1000) NULL AFTER discord_username,
    ADD COLUMN minecraft_login_provisional BOOLEAN NOT NULL DEFAULT FALSE AFTER minecraft_name;

CREATE TABLE web_login_challenges (
    id CHAR(36) PRIMARY KEY,
    browser_token_hash CHAR(64) NOT NULL UNIQUE,
    verification_code_hash CHAR(64) NOT NULL UNIQUE,
    member_id CHAR(36) NULL,
    minecraft_uuid CHAR(36) NULL,
    minecraft_name VARCHAR(100) NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    verified_at TIMESTAMP(6) NULL,
    consumed_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_web_login_member
        FOREIGN KEY (member_id) REFERENCES members(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_web_login_expiry ON web_login_challenges(expires_at);
CREATE INDEX idx_web_login_member ON web_login_challenges(member_id, created_at);
