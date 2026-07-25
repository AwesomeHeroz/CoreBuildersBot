package com.corebuilders.bot.discord.music;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class MusicRequestPolicyTest {
    @Test
    void convertsPlainTextToYoutubeSearch() {
        MusicRequestPolicy policy = new MusicRequestPolicy(false, Set.of());
        assertEquals("ytsearch:lofi: focus music", policy.resolve("lofi: focus music"));
    }

    @Test
    void acceptsKnownMusicSitesAndSubdomains() {
        MusicRequestPolicy policy = new MusicRequestPolicy(false, Set.of());
        assertEquals(
                "https://www.youtube.com/watch?v=test",
                policy.resolve("https://www.youtube.com/watch?v=test")
        );
        assertEquals(
                "https://artist.bandcamp.com/track/example",
                policy.resolve("https://artist.bandcamp.com/track/example")
        );
    }

    @Test
    void rejectsHttpCredentialsAndCustomPorts() {
        MusicRequestPolicy policy = new MusicRequestPolicy(true, Set.of("radio.example.com"));
        assertThrows(IllegalArgumentException.class,
                () -> policy.resolve("http://radio.example.com/stream"));
        assertThrows(IllegalArgumentException.class,
                () -> policy.resolve("https://user:pass@radio.example.com/stream"));
        assertThrows(IllegalArgumentException.class,
                () -> policy.resolve("https://radio.example.com:8443/stream"));
    }

    @Test
    void rejectsUnsafeConfiguredDirectHosts() {
        assertThrows(IllegalArgumentException.class,
                () -> new MusicRequestPolicy(true, Set.of("localhost")));
        assertThrows(IllegalArgumentException.class,
                () -> new MusicRequestPolicy(true, Set.of("127.0.0.1")));
        assertThrows(IllegalArgumentException.class,
                () -> new MusicRequestPolicy(true, Set.of("radio.internal")));
    }

    @Test
    void genericDirectUrlsRequireAnExplicitAllowedHost() {
        MusicRequestPolicy disabled = new MusicRequestPolicy(false, Set.of("radio.example.com"));
        assertThrows(IllegalArgumentException.class,
                () -> disabled.resolve("https://radio.example.com/live.mp3"));

        MusicRequestPolicy enabled = new MusicRequestPolicy(true, Set.of("radio.example.com"));
        assertEquals(
                "https://stream.radio.example.com/live.mp3",
                enabled.resolve("https://stream.radio.example.com/live.mp3")
        );
        assertThrows(IllegalArgumentException.class,
                () -> enabled.resolve("https://notradio.example.com/live.mp3"));
    }
}
