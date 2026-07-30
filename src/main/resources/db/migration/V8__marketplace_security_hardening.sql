ALTER TABLE members
    ADD COLUMN security_version BIGINT NOT NULL DEFAULT 0 AFTER minecraft_login_provisional;

ALTER TABLE marketplace_items
    ADD COLUMN version BIGINT NOT NULL DEFAULT 1 AFTER active;

ALTER TABLE marketplace_order_items
    ADD COLUMN funds_released BOOLEAN NOT NULL DEFAULT FALSE AFTER status,
    ADD COLUMN buyer_confirmed_at TIMESTAMP(6) NULL AFTER delivered_at,
    ADD COLUMN disputed_at TIMESTAMP(6) NULL AFTER buyer_confirmed_at,
    ADD COLUMN dispute_reason VARCHAR(500) NULL AFTER disputed_at,
    ADD COLUMN resolved_at TIMESTAMP(6) NULL AFTER dispute_reason,
    ADD COLUMN resolution VARCHAR(32) NULL AFTER resolved_at,
    ADD COLUMN resolution_note VARCHAR(500) NULL AFTER resolution;

-- Orders created before escrow was introduced already credited their sellers.
UPDATE marketplace_order_items SET funds_released = TRUE;

CREATE INDEX idx_marketplace_order_buyer_status
    ON marketplace_orders(buyer_member_id, status, created_at);
CREATE INDEX idx_marketplace_line_buyer_state
    ON marketplace_order_items(order_id, status, funds_released);
CREATE INDEX idx_marketplace_line_dispute_queue
    ON marketplace_order_items(status, funds_released, disputed_at);
