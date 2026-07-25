package com.corebuilders.bot.discord.music;

import com.corebuilders.bot.config.MusicConfig;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import dev.lavalink.youtube.YoutubeAudioSourceManager;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.managers.AudioManager;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/** Coordinates source loading, queueing, and guild audio connections. */
public final class MusicService implements AutoCloseable {
    public record EnqueueResult(List<MusicTrack> accepted, int rejected, boolean startedImmediately) {
        public EnqueueResult {
            accepted = List.copyOf(accepted);
        }
    }

    private final MusicConfig config;
    private final MusicRequestPolicy requestPolicy;
    private final AudioPlayerManager playerManager;
    private final ScheduledExecutorService timer;
    private final Map<Long, GuildMusicManager> guildManagers = new ConcurrentHashMap<>();

    public MusicService(MusicConfig config) {
        this.config = Objects.requireNonNull(config, "config");
        this.requestPolicy = new MusicRequestPolicy(config);
        this.playerManager = new DefaultAudioPlayerManager();
        this.timer = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "corebot-music-idle");
            thread.setDaemon(true);
            return thread;
        });

        playerManager.registerSourceManager(new YoutubeAudioSourceManager());
        AudioSourceManagers.registerRemoteSources(
                playerManager,
                com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeAudioSourceManager.class
        );
    }

    public GuildMusicManager manager(Guild guild) {
        return guildManagers.computeIfAbsent(guild.getIdLong(), ignored -> createManager(guild));
    }

    public void connect(Guild guild, AudioChannel channel) {
        GuildMusicManager manager = manager(guild);
        AudioManager audio = guild.getAudioManager();
        audio.setSendingHandler(manager.sendHandler());
        audio.setSelfMuted(false);
        audio.setSelfDeafened(true);
        if (audio.getConnectedChannel() == null
                || !audio.getConnectedChannel().getId().equals(channel.getId())) {
            audio.openAudioConnection(channel);
        }
        manager.scheduler().armIdleDisconnectIfIdle();
    }

    public void disconnect(Guild guild, boolean clearQueue) {
        GuildMusicManager manager = guildManagers.get(guild.getIdLong());
        if (manager != null && clearQueue) manager.scheduler().clearForDisconnect();
        guild.getAudioManager().closeAudioConnection();
        guild.getAudioManager().setSendingHandler(null);
    }

    public CompletableFuture<EnqueueResult> enqueue(
            Guild guild,
            String input,
            String requesterId,
            String requesterName
    ) {
        GuildMusicManager manager = manager(guild);
        String identifier;
        try {
            identifier = requestPolicy.resolve(input);
        } catch (RuntimeException error) {
            return CompletableFuture.failedFuture(error);
        }

        CompletableFuture<EnqueueResult> result = new CompletableFuture<>();
        AtomicBoolean completed = new AtomicBoolean(false);
        ScheduledFuture<?> timeout = timer.schedule(
                () -> finishFailure(
                        result,
                        completed,
                        new IllegalStateException("The audio source took too long to respond.")
                ),
                config.loadTimeoutSeconds(),
                TimeUnit.SECONDS
        );
        playerManager.loadItemOrdered(manager, identifier, new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack track) {
                completeEnqueue(result, completed, timeout, manager, List.of(track), requesterId, requesterName);
            }

            @Override
            public void playlistLoaded(AudioPlaylist playlist) {
                List<AudioTrack> tracks;
                if (playlist.isSearchResult()) {
                    AudioTrack selected = playlist.getSelectedTrack();
                    tracks = selected == null
                            ? playlist.getTracks().stream().limit(1).toList()
                            : List.of(selected);
                } else if (playlist.getSelectedTrack() != null) {
                    tracks = List.of(playlist.getSelectedTrack());
                } else {
                    tracks = playlist.getTracks().stream()
                            .limit(config.maxPlaylistTracks())
                            .toList();
                }
                completeEnqueue(result, completed, timeout, manager, tracks, requesterId, requesterName);
            }

            @Override
            public void noMatches() {
                finishFailure(result, completed, new IllegalArgumentException("No matching track was found."));
                timeout.cancel(false);
            }

            @Override
            public void loadFailed(FriendlyException exception) {
                finishFailure(result, completed, new IllegalStateException(
                        "The audio source could not be loaded. Check the URL or try another search."
                ));
                timeout.cancel(false);
            }
        });
        return result;
    }

    private void completeEnqueue(
            CompletableFuture<EnqueueResult> result,
            AtomicBoolean completed,
            ScheduledFuture<?> timeout,
            GuildMusicManager manager,
            List<AudioTrack> tracks,
            String requesterId,
            String requesterName
    ) {
        if (!completed.compareAndSet(false, true)) return;
        timeout.cancel(false);
        try {
            result.complete(enqueueTracks(manager, tracks, requesterId, requesterName));
        } catch (Exception error) {
            result.completeExceptionally(error);
        }
    }

    private static void finishFailure(
            CompletableFuture<EnqueueResult> result,
            AtomicBoolean completed,
            RuntimeException error
    ) {
        if (completed.compareAndSet(false, true)) {
            result.completeExceptionally(error);
        }
    }

    private EnqueueResult enqueueTracks(
            GuildMusicManager manager,
            List<AudioTrack> tracks,
            String requesterId,
            String requesterName
    ) {
        List<MusicTrack> accepted = new ArrayList<>();
        int rejected = 0;
        boolean started = false;

        for (AudioTrack track : tracks) {
            if (!isAllowed(track)) {
                rejected++;
                continue;
            }
            MusicTrack metadata = MusicTrack.from(track, requesterId, requesterName);
            track.setUserData(metadata);
            TrackScheduler.QueueResult queueResult = manager.scheduler().queue(track);
            if (queueResult == TrackScheduler.QueueResult.FULL) {
                rejected++;
                continue;
            }
            if (queueResult == TrackScheduler.QueueResult.STARTED) started = true;
            accepted.add(metadata);
        }

        if (accepted.isEmpty()) {
            String reason = manager.scheduler().queueSize() >= config.maxQueueSize()
                    ? "The music queue is full."
                    : "No tracks passed the configured stream or duration limits.";
            throw new IllegalArgumentException(reason);
        }
        return new EnqueueResult(accepted, rejected, started);
    }

    private boolean isAllowed(AudioTrack track) {
        var info = track.getInfo();
        if (info.isStream) return config.allowStreams();
        return info.length <= config.maxTrackDurationMillis();
    }

    private GuildMusicManager createManager(Guild guild) {
        var player = playerManager.createPlayer();
        player.setVolume(config.defaultVolume());
        TrackScheduler scheduler = new TrackScheduler(
                player,
                config.maxQueueSize(),
                timer,
                Duration.ofSeconds(config.idleDisconnectSeconds()),
                () -> disconnect(guild, false)
        );
        player.addListener(scheduler);
        return new GuildMusicManager(guild, player, scheduler);
    }

    @Override
    public void close() {
        guildManagers.values().forEach(GuildMusicManager::destroy);
        guildManagers.clear();
        playerManager.shutdown();
        timer.shutdownNow();
    }
}
