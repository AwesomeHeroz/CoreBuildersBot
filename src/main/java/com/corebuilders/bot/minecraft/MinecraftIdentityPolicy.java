package com.corebuilders.bot.minecraft;

import com.corebuilders.bot.config.WebsiteConfig;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;

/** Fails closed when Minecraft UUIDs are not backed by a trusted identity configuration. */
public final class MinecraftIdentityPolicy {
    private MinecraftIdentityPolicy() {}

    public static void validate(JavaPlugin plugin, WebsiteConfig website) {
        if (!website.enabled()) return;
        String mode = plugin.getConfig().getString("minecraft.web-login.identity-mode", "ONLINE_MODE")
                .trim().toUpperCase(Locale.ROOT);
        switch (mode) {
            case "ONLINE_MODE" -> {
                if (!plugin.getServer().getOnlineMode()) {
                    throw new IllegalStateException(
                            "Minecraft website login requires server online-mode=true. "
                                    + "For a securely configured Velocity/Bungee backend, explicitly set "
                                    + "minecraft.web-login.identity-mode=TRUSTED_PROXY."
                    );
                }
            }
            case "TRUSTED_PROXY" -> {
                if (!plugin.getConfig().getBoolean("minecraft.web-login.trusted-proxy-confirmed", false)) {
                    throw new IllegalStateException(
                            "TRUSTED_PROXY web login requires minecraft.web-login.trusted-proxy-confirmed=true "
                                    + "after modern forwarding/shared-secret validation and backend firewalling are verified."
                    );
                }
                plugin.getLogger().warning(
                        "Minecraft web login trusts proxy-forwarded UUIDs. The operator explicitly confirmed "
                                + "modern forwarding/secret validation and that the Paper backend is not directly reachable."
                );
            }
            case "DEVELOPMENT" -> {
                String host = website.publicBaseUrl().getHost().toLowerCase(Locale.ROOT);
                if (!(host.equals("localhost") || host.equals("::1") || host.startsWith("127."))) {
                    throw new IllegalStateException("DEVELOPMENT identity mode is allowed only with a loopback website URL.");
                }
                plugin.getLogger().warning("Minecraft website login is using DEVELOPMENT identity mode.");
            }
            default -> throw new IllegalStateException(
                    "minecraft.web-login.identity-mode must be ONLINE_MODE, TRUSTED_PROXY, or DEVELOPMENT."
            );
        }
    }
}
