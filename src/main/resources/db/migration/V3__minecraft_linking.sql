ALTER TABLE members
    ADD COLUMN minecraft_uuid CHAR(36) NULL UNIQUE,
    ADD COLUMN minecraft_name VARCHAR(100) NULL;

CREATE TABLE minecraft_link_codes (
    code VARCHAR(16) PRIMARY KEY,
    discord_user_id VARCHAR(32) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_link_code_member FOREIGN KEY (discord_user_id)
        REFERENCES members(discord_user_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_link_codes_expires ON minecraft_link_codes(expires_at);
