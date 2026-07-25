package com.corebuilders.bot.discord.music;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MusicFormattingTest {
    @Test
    void formatsTrackDurations() {
        assertEquals("0:00", MusicFormatting.duration(0, false));
        assertEquals("3:05", MusicFormatting.duration(185_000, false));
        assertEquals("1:02:03", MusicFormatting.duration(3_723_000, false));
        assertEquals("LIVE", MusicFormatting.duration(0, true));
    }

    @Test
    void neutralisesMentionsAndLongText() {
        assertEquals("@\u200Beveryone hello", MusicFormatting.safe("@everyone hello", 100));
        assertEquals("1234…", MusicFormatting.safe("123456789", 5));
    }
}
