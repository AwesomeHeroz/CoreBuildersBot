CREATE TABLE members (
    id CHAR(36) PRIMARY KEY,
    discord_user_id VARCHAR(32) NOT NULL UNIQUE,
    username VARCHAR(100) NOT NULL,
    reputation VARCHAR(32) NOT NULL DEFAULT 'UNVERIFIED',
    primary_role VARCHAR(100),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE xp_transactions (
    id CHAR(36) PRIMARY KEY,
    member_id CHAR(36) NOT NULL,
    amount BIGINT NOT NULL,
    category VARCHAR(40) NOT NULL,
    source_type VARCHAR(40) NOT NULL,
    reference_id CHAR(36),
    reason VARCHAR(500) NOT NULL,
    actor_discord_id VARCHAR(32) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_xp_member FOREIGN KEY (member_id) REFERENCES members(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX idx_xp_member_created ON xp_transactions(member_id, created_at);
CREATE INDEX idx_xp_created ON xp_transactions(created_at);
CREATE INDEX idx_xp_category ON xp_transactions(category);

CREATE TABLE credit_transactions (
    id CHAR(36) PRIMARY KEY,
    member_id CHAR(36) NOT NULL,
    amount BIGINT NOT NULL,
    source_type VARCHAR(40) NOT NULL,
    reference_id CHAR(36),
    reason VARCHAR(500) NOT NULL,
    actor_discord_id VARCHAR(32) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_credit_member FOREIGN KEY (member_id) REFERENCES members(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX idx_credit_member_created ON credit_transactions(member_id, created_at);

CREATE TABLE contributions (
    id CHAR(36) PRIMARY KEY,
    member_id CHAR(36) NOT NULL,
    category VARCHAR(40) NOT NULL,
    description VARCHAR(1000) NOT NULL,
    project_name VARCHAR(200),
    evidence_url VARCHAR(1000),
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    suggested_cxp BIGINT NOT NULL DEFAULT 0,
    suggested_credits BIGINT NOT NULL DEFAULT 0,
    awarded_cxp BIGINT,
    awarded_credits BIGINT,
    reviewer_discord_id VARCHAR(32),
    review_reason VARCHAR(500),
    reviewed_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_contribution_member FOREIGN KEY (member_id) REFERENCES members(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX idx_contribution_status_created ON contributions(status, created_at);

CREATE TABLE achievements (
    code VARCHAR(64) PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(500) NOT NULL,
    metric VARCHAR(40) NOT NULL,
    category VARCHAR(40),
    threshold BIGINT NOT NULL,
    reward_cxp BIGINT NOT NULL DEFAULT 0,
    reward_credits BIGINT NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE member_achievements (
    member_id CHAR(36) NOT NULL,
    achievement_code VARCHAR(64) NOT NULL,
    unlocked_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (member_id, achievement_code),
    CONSTRAINT fk_member_achievement_member FOREIGN KEY (member_id) REFERENCES members(id),
    CONSTRAINT fk_member_achievement_achievement FOREIGN KEY (achievement_code) REFERENCES achievements(code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE projects (
    id CHAR(36) PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    description VARCHAR(1500) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'OPEN',
    lead_discord_id VARCHAR(32) NOT NULL,
    created_by_discord_id VARCHAR(32) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    completed_at TIMESTAMP(6) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE project_members (
    project_id CHAR(36) NOT NULL,
    member_id CHAR(36) NOT NULL,
    joined_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (project_id, member_id),
    CONSTRAINT fk_project_member_project FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE,
    CONSTRAINT fk_project_member_member FOREIGN KEY (member_id) REFERENCES members(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE project_tasks (
    id CHAR(36) PRIMARY KEY,
    project_id CHAR(36) NOT NULL,
    title VARCHAR(300) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'OPEN',
    assigned_member_id CHAR(36),
    reward_cxp BIGINT NOT NULL DEFAULT 0,
    reward_credits BIGINT NOT NULL DEFAULT 0,
    completed_by_member_id CHAR(36),
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    completed_at TIMESTAMP(6) NULL,
    CONSTRAINT fk_project_task_project FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE,
    CONSTRAINT fk_project_task_assignee FOREIGN KEY (assigned_member_id) REFERENCES members(id),
    CONSTRAINT fk_project_task_completer FOREIGN KEY (completed_by_member_id) REFERENCES members(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX idx_project_task_project ON project_tasks(project_id, status);

CREATE TABLE missions (
    id CHAR(36) PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    description VARCHAR(1500) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'OPEN',
    reward_cxp BIGINT NOT NULL DEFAULT 0,
    reward_credits BIGINT NOT NULL DEFAULT 0,
    max_slots INT NOT NULL DEFAULT 0,
    deadline TIMESTAMP(6) NULL,
    created_by_discord_id VARCHAR(32) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    completed_at TIMESTAMP(6) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE mission_members (
    mission_id CHAR(36) NOT NULL,
    member_id CHAR(36) NOT NULL,
    joined_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (mission_id, member_id),
    CONSTRAINT fk_mission_member_mission FOREIGN KEY (mission_id) REFERENCES missions(id) ON DELETE CASCADE,
    CONSTRAINT fk_mission_member_member FOREIGN KEY (member_id) REFERENCES members(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE shop_items (
    code VARCHAR(64) PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    description VARCHAR(500) NOT NULL,
    price BIGINT NOT NULL,
    stock INT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE shop_orders (
    id CHAR(36) PRIMARY KEY,
    member_id CHAR(36) NOT NULL,
    item_code VARCHAR(64) NOT NULL,
    price BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    fulfillment_note VARCHAR(1000),
    completed_by_discord_id VARCHAR(32),
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    completed_at TIMESTAMP(6) NULL,
    CONSTRAINT fk_shop_order_member FOREIGN KEY (member_id) REFERENCES members(id),
    CONSTRAINT fk_shop_order_item FOREIGN KEY (item_code) REFERENCES shop_items(code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX idx_order_status_created ON shop_orders(status, created_at);

CREATE TABLE audit_logs (
    id CHAR(36) PRIMARY KEY,
    actor_discord_id VARCHAR(32) NOT NULL,
    action VARCHAR(100) NOT NULL,
    target_discord_id VARCHAR(32),
    entity_type VARCHAR(80),
    entity_id VARCHAR(100),
    details VARCHAR(2000) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX idx_audit_created ON audit_logs(created_at);
CREATE INDEX idx_audit_target ON audit_logs(target_discord_id, created_at);
