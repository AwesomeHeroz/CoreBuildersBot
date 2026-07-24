package com.corebuilders.bot.discord.command;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TextCommandParserTest {
    private final TextCommandParser parser = new TextCommandParser("!core");

    @Test
    void parsesCommandAndArguments() {
        var result = parser.parse("!core leaderboard weekly");

        assertNotNull(result);
        assertEquals("leaderboard", result.command());
        assertEquals("weekly", result.argument(0, "overall"));
    }

    @Test
    void prefixMatchingIsCaseInsensitiveAndRequiresBoundary() {
        assertNotNull(parser.parse("!CORE profile"));
        assertNull(parser.parse("!coreprofile"));
        assertNull(parser.parse("hello !core profile"));
    }

    @Test
    void barePrefixMapsToHelp() {
        var result = parser.parse("!core");
        assertNotNull(result);
        assertEquals("help", result.command());
        assertTrue(result.arguments().isEmpty());
    }
}
