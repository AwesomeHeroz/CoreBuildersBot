package com.corebuilders.bot.discord;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DiscordFormattingTest {

    @Test
    void truncatesAndFormatsValuesConsistently() {
        assertEquals("abc…", DiscordFormatting.truncate("abcdef", 4));
        assertEquals("", DiscordFormatting.truncate(null, 4));
        assertEquals("—", DiscordFormatting.value("  "));
        assertEquals("null", DiscordFormatting.valueOrNull(null));
        assertEquals("1,234,567", DiscordFormatting.formatNumber(1_234_567));
        assertEquals(0, DiscordFormatting.number(null));
        assertEquals("🥇", DiscordFormatting.medal(0));
        assertEquals("▫️", DiscordFormatting.medal(4));
    }
}
