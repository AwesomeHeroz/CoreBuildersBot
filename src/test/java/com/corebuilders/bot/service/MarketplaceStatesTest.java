package com.corebuilders.bot.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static com.corebuilders.bot.service.MarketplaceStates.*;
import static org.junit.jupiter.api.Assertions.*;

class MarketplaceStatesTest {
    @Test
    void keepsOrderHeldWhileAnyLineStillNeedsAction() {
        List<LineState> lines = List.of(
                new LineState(LINE_SETTLED, true),
                new LineState(LINE_PENDING, false)
        );

        assertFalse(isComplete(lines));
        assertEquals(ORDER_HELD, orderStatus(lines));
    }

    @Test
    void disputedLineTakesPriorityOverCompletion() {
        List<LineState> lines = List.of(
                new LineState(LINE_SETTLED, true),
                new LineState(LINE_DISPUTED, false)
        );

        assertFalse(isComplete(lines));
        assertEquals(ORDER_DISPUTED, orderStatus(lines));
    }

    @Test
    void completesOnlyWhenEveryLineIsTerminal() {
        List<LineState> lines = List.of(
                new LineState(LINE_SETTLED, true),
                new LineState(LINE_CANCELLED, false),
                new LineState(LINE_REFUNDED, false),
                new LineState(LINE_DELIVERED, true)
        );

        assertTrue(isComplete(lines));
        assertEquals(ORDER_COMPLETED, orderStatus(lines));
    }

    @Test
    void emptyOrderIsNeverComplete() {
        assertFalse(isComplete(List.of()));
        assertEquals(ORDER_HELD, orderStatus(List.of()));
    }
}
