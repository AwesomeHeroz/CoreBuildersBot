package com.corebuilders.bot;

import com.corebuilders.bot.minecraft.CoreCommand;
import com.corebuilders.bot.minecraft.PlayerConnectionListener;
import com.corebuilders.bot.runtime.CoreBuildersRuntime;
import com.corebuilders.bot.util.ErrorMessages;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

public final class CoreBuildersPlugin extends JavaPlugin {
    private CoreBuildersRuntime runtime;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        Thread thread = Thread.currentThread();
        ClassLoader previousClassLoader = thread.getContextClassLoader();
        thread.setContextClassLoader(getClass().getClassLoader());
        try {
            runtime = CoreBuildersRuntime.start(this);
            runtime.commandRegistrar().registerCommands();

            CoreCommand coreCommand = new CoreCommand(
                    this,
                    runtime.linkService(),
                    runtime.memberService(),
                    runtime.achievementService(),
                    runtime.ledgerService()
            );

            PluginCommand command = getCommand("core");
            if (command == null) {
                throw new IllegalStateException("The /core command is missing from plugin.yml.");
            }
            command.setExecutor(coreCommand);
            command.setTabCompleter(coreCommand);

            getServer().getPluginManager().registerEvents(
                    new PlayerConnectionListener(this, runtime.linkService()),
                    this
            );

            getLogger().info(
                    "Core Builders enabled without Spring. Discord, progression, economy, "
                            + "external MySQL, Discord music, and Minecraft integration are active."
            );
        } catch (Exception error) {
            getLogger().log(Level.SEVERE, "Failed to start Core Builders: " + ErrorMessages.safe(error), error);
            closeRuntime();
            getServer().getPluginManager().disablePlugin(this);
        } finally {
            thread.setContextClassLoader(previousClassLoader);
        }
    }

    @Override
    public void onDisable() {
        closeRuntime();
    }

    private void closeRuntime() {
        if (runtime == null) {
            return;
        }
        try {
            runtime.close();
        } catch (Exception error) {
            getLogger().log(Level.WARNING, "Error while shutting down Core Builders: " + ErrorMessages.safe(error), error);
        } finally {
            runtime = null;
        }
    }


}
