package com.corebuilders.bot.discord.music;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.track.playback.AudioFrame;
import net.dv8tion.jda.api.audio.AudioSendHandler;

import java.nio.ByteBuffer;
import java.util.Objects;

/** Supplies Lavaplayer's already encoded Opus frames to JDA. */
public final class LavaplayerSendHandler implements AudioSendHandler {
    private final AudioPlayer player;
    private AudioFrame lastFrame;

    public LavaplayerSendHandler(AudioPlayer player) {
        this.player = Objects.requireNonNull(player, "player");
    }

    @Override
    public boolean canProvide() {
        lastFrame = player.provide();
        return lastFrame != null;
    }

    @Override
    public ByteBuffer provide20MsAudio() {
        AudioFrame frame = lastFrame;
        lastFrame = null;
        return frame == null ? null : ByteBuffer.wrap(frame.getData());
    }

    @Override
    public boolean isOpus() {
        return true;
    }
}
