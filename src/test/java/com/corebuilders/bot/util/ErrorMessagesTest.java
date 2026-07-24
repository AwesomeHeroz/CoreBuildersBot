package com.corebuilders.bot.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ErrorMessagesTest {
    @Test
    void findsUsefulNestedMessage() {
        RuntimeException error = new RuntimeException(null, new IllegalStateException("database unavailable"));
        assertEquals("database unavailable", ErrorMessages.safe(error));
    }

    @Test
    void truncatesLongMessages() {
        RuntimeException error = new RuntimeException("abcdefghij");
        assertEquals("abcd…", ErrorMessages.safe(error, 5));
    }
}
