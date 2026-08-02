ALTER TABLE marketplace_order_items
    ADD COLUMN seller_confirmed_at TIMESTAMP(6) NULL AFTER buyer_confirmed_at,
    ADD COLUMN cancelled_at TIMESTAMP(6) NULL AFTER seller_confirmed_at,
    ADD COLUMN cancelled_by VARCHAR(16) NULL AFTER cancelled_at,
    ADD COLUMN discord_ticket_state VARCHAR(32) NOT NULL DEFAULT 'PENDING' AFTER resolution_note,
    ADD COLUMN discord_channel_id VARCHAR(32) NULL AFTER discord_ticket_state,
    ADD COLUMN discord_message_id VARCHAR(32) NULL AFTER discord_channel_id,
    ADD COLUMN discord_ticket_updated_at TIMESTAMP(6) NULL AFTER discord_message_id;

-- Preserve the completion timestamp from orders settled before the buyer/seller workflow changed.
UPDATE marketplace_order_items
SET seller_confirmed_at = buyer_confirmed_at
WHERE funds_released = TRUE AND buyer_confirmed_at IS NOT NULL;

CREATE INDEX idx_marketplace_ticket_queue
    ON marketplace_order_items(discord_ticket_state, created_at);
CREATE UNIQUE INDEX uq_marketplace_ticket_channel
    ON marketplace_order_items(discord_channel_id);
