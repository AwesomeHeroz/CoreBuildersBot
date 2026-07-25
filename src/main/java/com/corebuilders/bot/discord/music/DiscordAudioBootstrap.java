package com.corebuilders.bot.discord.music;

import moe.kyokobot.libdave.DaveFactory;
import moe.kyokobot.libdave.NativeDaveFactory;
import moe.kyokobot.libdave.jda.LDJDADaveSessionFactory;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.audio.AudioModuleConfig;

/** Configures the DAVE implementation required by current Discord voice connections. */
public final class DiscordAudioBootstrap {
    private DiscordAudioBootstrap() {}

    public static void configure(JDABuilder builder) {
        DaveFactory daveFactory = new NativeDaveFactory();
        builder.setAudioModuleConfig(
                new AudioModuleConfig()
                        .withDaveSessionFactory(new LDJDADaveSessionFactory(daveFactory))
        );
    }
}
