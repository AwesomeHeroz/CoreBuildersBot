package com.corebuilders.bot.discord.music;

import com.corebuilders.bot.config.MusicConfig;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.managers.AudioManager;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletionException;

import static com.corebuilders.bot.discord.music.MusicFormatting.duration;
import static com.corebuilders.bot.discord.music.MusicFormatting.safe;

/** Discord slash-command adapter for voice playback. */
public final class MusicDiscordListener extends ListenerAdapter implements AutoCloseable {
    private final MusicConfig config;
    private final String guildId;
    private final MusicService music;

    public MusicDiscordListener(MusicConfig config, String guildId, MusicService music) {
        this.config = Objects.requireNonNull(config, "config");
        this.guildId = Objects.requireNonNull(guildId, "guildId");
        this.music = Objects.requireNonNull(music, "music");
    }

    public Set<String> handledCommandNames() {
        return Set.of("music");
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!"music".equals(event.getName())) return;
        if (!event.isFromGuild() || event.getGuild() == null || !guildId.equals(event.getGuild().getId())) {
            event.reply("Music commands can only be used in the configured Core Builders server.")
                    .setEphemeral(true).queue();
            return;
        }
        if (!config.enabled()) {
            event.reply("Music playback is disabled in config.yml.").setEphemeral(true).queue();
            return;
        }

        try {
            switch (Objects.requireNonNullElse(event.getSubcommandName(), "")) {
                case "join" -> join(event);
                case "play" -> play(event);
                case "queue" -> showQueue(event);
                case "nowplaying" -> nowPlaying(event);
                case "skip" -> skip(event);
                case "pause" -> pause(event, true);
                case "resume" -> pause(event, false);
                case "stop" -> stop(event);
                case "leave" -> leave(event);
                case "volume" -> volume(event);
                default -> event.reply("Unknown music action.").setEphemeral(true).queue();
            }
        } catch (Exception error) {
            event.reply("❌ " + message(error)).setEphemeral(true).queue();
        }
    }

    private void join(SlashCommandInteractionEvent event) {
        requireControllerRole(event);
        Guild guild = event.getGuild();
        AudioChannel channel = requireCallerVoice(event);
        requireBotPermissions(guild, channel);
        requireAvailableChannel(guild, channel);
        music.connect(guild, channel);
        event.reply("🔊 Joined **" + safe(channel.getName(), 80) + "**.").queue();
    }

    private void play(SlashCommandInteractionEvent event) {
        requireControllerRole(event);
        Guild guild = event.getGuild();
        AudioChannel channel = requireCallerVoice(event);
        requireBotPermissions(guild, channel);
        requireAvailableChannel(guild, channel);
        String query = Objects.requireNonNull(event.getOption("query"), "query").getAsString();
        music.connect(guild, channel);

        event.deferReply().queue(hook -> music.enqueue(
                guild,
                query,
                event.getUser().getId(),
                event.getUser().getName()
        ).whenComplete((result, error) -> {
            if (error != null) {
                hook.editOriginal("❌ " + message(error)).queue();
                return;
            }
            MusicTrack first = result.accepted().getFirst();
            String action = result.startedImmediately() ? "▶️ Playing" : "➕ Queued";
            StringBuilder response = new StringBuilder(action)
                    .append(" **").append(safe(first.title(), 120)).append("**")
                    .append(" — ").append(safe(first.author(), 80))
                    .append(" [`").append(duration(first.durationMillis(), first.stream())).append("`]");
            if (result.accepted().size() > 1) {
                response.append("\nAdded ").append(result.accepted().size()).append(" tracks from the playlist.");
            }
            if (result.rejected() > 0) {
                response.append(" Skipped ").append(result.rejected()).append(" track(s) due to limits or queue capacity.");
            }
            hook.editOriginal(response.toString()).queue();
        }));
    }

    private void showQueue(SlashCommandInteractionEvent event) {
        var scheduler = music.manager(event.getGuild()).scheduler();
        StringBuilder text = new StringBuilder("**Music queue**\n");
        scheduler.currentTrack().ifPresentOrElse(track -> {
            MusicTrack meta = MusicTrack.from(track);
            text.append("▶️ ").append(safe(meta.title(), 100))
                    .append(" — ").append(safe(meta.author(), 60))
                    .append(" [`").append(duration(meta.durationMillis(), meta.stream())).append("`]\n");
        }, () -> text.append("Nothing is playing.\n"));

        List<com.sedmelluq.discord.lavaplayer.track.AudioTrack> queued = scheduler.queueSnapshot();
        for (int index = 0; index < Math.min(10, queued.size()); index++) {
            MusicTrack meta = MusicTrack.from(queued.get(index));
            text.append(index + 1).append(". ")
                    .append(safe(meta.title(), 90))
                    .append(" [`").append(duration(meta.durationMillis(), meta.stream())).append("`]\n");
        }
        if (queued.size() > 10) text.append("…and ").append(queued.size() - 10).append(" more.");
        event.reply(text.toString()).queue();
    }

    private void nowPlaying(SlashCommandInteractionEvent event) {
        var current = music.manager(event.getGuild()).scheduler().currentTrack();
        if (current.isEmpty()) {
            event.reply("Nothing is currently playing.").setEphemeral(true).queue();
            return;
        }
        var track = current.get();
        MusicTrack meta = MusicTrack.from(track);
        String progress = meta.stream()
                ? "LIVE"
                : duration(track.getPosition(), false) + " / " + duration(meta.durationMillis(), false);
        event.reply("▶️ **" + safe(meta.title(), 120) + "** — " + safe(meta.author(), 80)
                + " [`" + progress + "`]\nRequested by **" + safe(meta.requesterName(), 60) + "**.").queue();
    }

    private void skip(SlashCommandInteractionEvent event) {
        requireController(event);
        var skipped = music.manager(event.getGuild()).scheduler().skip();
        event.reply(skipped.isPresent() ? "⏭️ Skipped the current track." : "Nothing is currently playing.").queue();
    }

    private void pause(SlashCommandInteractionEvent event, boolean paused) {
        requireController(event);
        var scheduler = music.manager(event.getGuild()).scheduler();
        if (scheduler.currentTrack().isEmpty()) {
            event.reply("Nothing is currently playing.").setEphemeral(true).queue();
            return;
        }
        scheduler.setPaused(paused);
        event.reply(paused ? "⏸️ Playback paused." : "▶️ Playback resumed.").queue();
    }

    private void stop(SlashCommandInteractionEvent event) {
        requireController(event);
        music.manager(event.getGuild()).scheduler().stop();
        event.reply("⏹️ Playback stopped and the queue was cleared.").queue();
    }

    private void leave(SlashCommandInteractionEvent event) {
        requireController(event);
        music.disconnect(event.getGuild(), true);
        event.reply("👋 Left the voice channel and cleared the queue.").queue();
    }

    private void volume(SlashCommandInteractionEvent event) {
        requireController(event);
        int requested = Math.toIntExact(Objects.requireNonNull(event.getOption("percent"), "percent").getAsLong());
        int volume = Math.max(0, Math.min(150, requested));
        music.manager(event.getGuild()).scheduler().setVolume(volume);
        event.reply("🔉 Volume set to **" + volume + "%**.").queue();
    }

    private AudioChannel requireCallerVoice(SlashCommandInteractionEvent event) {
        Member member = Objects.requireNonNull(event.getMember(), "member");
        if (member.getVoiceState() == null || member.getVoiceState().getChannel() == null) {
            throw new IllegalStateException("Join a voice channel first.");
        }
        AudioChannel channel = member.getVoiceState().getChannel();
        if (channel.getType() != ChannelType.VOICE) {
            throw new IllegalStateException("Join a normal voice channel first; Stage channels are not supported.");
        }
        return channel;
    }

    private void requireController(SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();
        Member member = Objects.requireNonNull(event.getMember(), "member");
        AudioChannel userChannel = requireCallerVoice(event);
        AudioManager audio = guild.getAudioManager();
        if (audio.getConnectedChannel() == null
                || !audio.getConnectedChannel().getId().equals(userChannel.getId())) {
            throw new IllegalStateException("Join the same voice channel as the bot first.");
        }
        requireControllerRole(event);
    }

    private void requireControllerRole(SlashCommandInteractionEvent event) {
        if (config.controllerRoleIds().isEmpty()) return;
        Member member = Objects.requireNonNull(event.getMember(), "member");
        boolean allowed = member.getRoles().stream()
                .anyMatch(role -> config.controllerRoleIds().contains(role.getId()));
        if (!allowed) throw new SecurityException("You do not have a configured music-controller role.");
    }

    private static void requireBotPermissions(Guild guild, AudioChannel channel) {
        Member self = guild.getSelfMember();
        if (!self.hasPermission(channel, Permission.VOICE_CONNECT)) {
            throw new SecurityException("The bot needs Connect permission in that voice channel.");
        }
        if (!self.hasPermission(channel, Permission.VOICE_SPEAK)) {
            throw new SecurityException("The bot needs Speak permission in that voice channel.");
        }
    }

    private static void requireAvailableChannel(Guild guild, AudioChannel requested) {
        var connected = guild.getAudioManager().getConnectedChannel();
        if (connected != null && !connected.getId().equals(requested.getId())) {
            throw new IllegalStateException("The bot is already being used in **" + safe(connected.getName(), 80) + "**.");
        }
    }

    private static String message(Throwable error) {
        Throwable current = error;
        while ((current instanceof CompletionException || current.getClass() == RuntimeException.class)
                && current.getCause() != null) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank() ? "Music operation failed." : safe(message, 400);
    }

    @Override
    public void close() {
        music.close();
    }
}
