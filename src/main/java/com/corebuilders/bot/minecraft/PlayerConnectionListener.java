package com.corebuilders.bot.minecraft;

import com.corebuilders.bot.service.LinkService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class PlayerConnectionListener implements Listener {
    private final JavaPlugin plugin;
    private final LinkService links;

    public PlayerConnectionListener(JavaPlugin plugin, LinkService links) {
        this.plugin = plugin;
        this.links = links;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        var player = event.getPlayer();
        var playerId = player.getUniqueId();
        var playerName = player.getName();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                links.updateMinecraftName(playerId, playerName);
            } catch (Exception error) {
                plugin.getLogger().warning("Could not update linked Minecraft name for " + playerName + ": " + error.getMessage());
            }
        });
    }
}
