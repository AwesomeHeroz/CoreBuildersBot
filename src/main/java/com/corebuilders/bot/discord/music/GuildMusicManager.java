package com.corebuilders.bot.discord.music;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import net.dv8tion.jda.api.entities.Guild;

import java.util.Objects;

/** Owns one independent Lavaplayer instance and queue per Discord guild. */
public final class GuildMusicManager {
    private final Guild guild;
    private final AudioPlayer player;
    private final TrackScheduler scheduler;
    private final LavaplayerSendHandler sendHandler;

    GuildMusicManager(Guild guild, AudioPlayer player, TrackScheduler scheduler) {
        this.guild = Objects.requireNonNull(guild, "guild");
        this.player = Objects.requireNonNull(player, "player");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.sendHandler = new LavaplayerSendHandler(player);
    }

    public Guild guild() { return guild; }
    public AudioPlayer player() { return player; }
    public TrackScheduler scheduler() { return scheduler; }
    public LavaplayerSendHandler sendHandler() { return sendHandler; }

    public void destroy() {
        guild.getAudioManager().closeAudioConnection();
        guild.getAudioManager().setSendingHandler(null);
        scheduler.destroy();
    }
}
