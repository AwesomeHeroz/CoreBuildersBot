package com.corebuilders.bot.discord.music;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/** Per-guild queue and playback state. */
public final class TrackScheduler extends AudioEventAdapter {
    public enum QueueResult { STARTED, QUEUED, FULL }

    private final AudioPlayer player;
    private final ArrayDeque<AudioTrack> queue = new ArrayDeque<>();
    private final int maxQueueSize;
    private final ScheduledExecutorService timer;
    private final Duration idleTimeout;
    private final Runnable idleAction;
    private ScheduledFuture<?> idleTask;

    public TrackScheduler(
            AudioPlayer player,
            int maxQueueSize,
            ScheduledExecutorService timer,
            Duration idleTimeout,
            Runnable idleAction
    ) {
        this.player = Objects.requireNonNull(player, "player");
        this.maxQueueSize = Math.max(1, maxQueueSize);
        this.timer = Objects.requireNonNull(timer, "timer");
        this.idleTimeout = Objects.requireNonNull(idleTimeout, "idleTimeout");
        this.idleAction = Objects.requireNonNull(idleAction, "idleAction");
    }

    public synchronized QueueResult queue(AudioTrack track) {
        Objects.requireNonNull(track, "track");
        cancelIdleTask();
        if (player.getPlayingTrack() == null && player.startTrack(track, true)) {
            return QueueResult.STARTED;
        }
        if (queue.size() >= maxQueueSize) return QueueResult.FULL;
        queue.addLast(track);
        return QueueResult.QUEUED;
    }

    public synchronized Optional<AudioTrack> skip() {
        AudioTrack previous = player.getPlayingTrack();
        AudioTrack next = queue.pollFirst();
        if (next == null) {
            player.stopTrack();
            scheduleIdleTask();
        } else {
            player.startTrack(next, false);
        }
        return Optional.ofNullable(previous);
    }

    public synchronized void stop() {
        queue.clear();
        player.stopTrack();
        player.setPaused(false);
        scheduleIdleTask();
    }

    public synchronized void armIdleDisconnectIfIdle() {
        if (player.getPlayingTrack() == null && queue.isEmpty()) {
            scheduleIdleTask();
        }
    }

    public synchronized void clearForDisconnect() {
        cancelIdleTask();
        queue.clear();
        player.stopTrack();
        player.setPaused(false);
    }

    public synchronized List<AudioTrack> queueSnapshot() {
        return new ArrayList<>(queue);
    }

    public synchronized int queueSize() {
        return queue.size();
    }

    public Optional<AudioTrack> currentTrack() {
        return Optional.ofNullable(player.getPlayingTrack());
    }

    public boolean isPaused() {
        return player.isPaused();
    }

    public void setPaused(boolean paused) {
        player.setPaused(paused);
    }

    public int volume() {
        return player.getVolume();
    }

    public void setVolume(int volume) {
        player.setVolume(Math.max(0, Math.min(150, volume)));
    }

    public synchronized void destroy() {
        cancelIdleTask();
        queue.clear();
        player.stopTrack();
        player.destroy();
    }

    @Override
    public synchronized void onTrackStart(AudioPlayer player, AudioTrack track) {
        cancelIdleTask();
    }

    @Override
    public synchronized void onTrackEnd(
            AudioPlayer player,
            AudioTrack track,
            AudioTrackEndReason endReason
    ) {
        if (endReason.mayStartNext) startNext();
    }

    @Override
    public synchronized void onTrackStuck(AudioPlayer player, AudioTrack track, long thresholdMs) {
        startNext();
    }

    @Override
    public void onTrackException(AudioPlayer player, AudioTrack track, FriendlyException exception) {
        // Lavaplayer also emits onTrackEnd; queue progression is handled there.
    }

    private void startNext() {
        AudioTrack next = queue.pollFirst();
        if (next == null) {
            scheduleIdleTask();
        } else {
            player.startTrack(next, false);
        }
    }

    private void scheduleIdleTask() {
        cancelIdleTask();
        idleTask = timer.schedule(idleAction, idleTimeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    private void cancelIdleTask() {
        if (idleTask != null) {
            idleTask.cancel(false);
            idleTask = null;
        }
    }
}
