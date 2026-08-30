-- Spawn-help requester history and helper leaderboard query support.
-- The spawn_help_tickets table already retains the Discord user ID, Discord username,
-- Minecraft IGN, helper identity, status, and timestamps for every ticket.
ALTER TABLE spawn_help_tickets
    ADD INDEX idx_spawn_help_ign_created (ingame_name, created_at),
    ADD INDEX idx_spawn_help_helper_completed (status, helper_discord_id, closed_at);
