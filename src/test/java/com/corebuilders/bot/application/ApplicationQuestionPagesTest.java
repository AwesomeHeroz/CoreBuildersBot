package com.corebuilders.bot.application;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ApplicationQuestionPagesTest {
    @Test
    void splitsQuestionsIntoStablePages() {
        ApplicationQuestionPages<Integer> pages = new ApplicationQuestionPages<>(
                List.of(1, 2, 3, 4, 5, 6, 7),
                5
        );

        assertEquals(2, pages.pageCount());
        assertEquals(List.of(1, 2, 3, 4, 5), pages.page(0));
        assertEquals(List.of(6, 7), pages.page(1));
    }

    @Test
    void supportsDisabledWorkflowWithNoQuestions() {
        ApplicationQuestionPages<Integer> pages = new ApplicationQuestionPages<>(List.of(), 5);
        assertEquals(0, pages.pageCount());
        assertThrows(IllegalArgumentException.class, () -> pages.page(0));
    }

    @Test
    void rejectsInvalidPage() {
        ApplicationQuestionPages<Integer> pages = new ApplicationQuestionPages<>(List.of(1), 5);
        assertThrows(IllegalArgumentException.class, () -> pages.page(-1));
        assertThrows(IllegalArgumentException.class, () -> pages.page(1));
    }
}
