package com.corebuilders.bot.discord.music;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

/** Safe display metadata attached to every queued track. */
public record MusicTrack(
        String title,
        String author,
        String uri,
        long durationMillis,
        boolean stream,
        String requesterId,
        String requesterName
) {
    public static MusicTrack from(AudioTrack track, String requesterId, String requesterName) {
        var info = track.getInfo();
        return new MusicTrack(
                safe(info.title, "Unknown title"),
                safe(info.author, "Unknown artist"),
                safe(info.uri, ""),
                Math.max(0L, info.length),
                info.isStream,
                requesterId,
                safe(requesterName, "Unknown user")
        );
    }

    public static MusicTrack from(AudioTrack track) {
        Object data = track.getUserData();
        if (data instanceof MusicTrack metadata) return metadata;
        return from(track, "", "Unknown user");
    }

    private static String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
