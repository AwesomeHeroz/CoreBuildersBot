package com.corebuilders.bot.application;

import java.util.List;

/** Calculates stable pages for Discord modal questions. */
public final class ApplicationQuestionPages<T> {
    private final List<T> questions;
    private final int pageSize;

    public ApplicationQuestionPages(List<T> questions, int pageSize) {
        if (questions == null) {
            throw new IllegalArgumentException("questions cannot be null.");
        }
        if (pageSize < 1) {
            throw new IllegalArgumentException("pageSize must be positive.");
        }
        this.questions = List.copyOf(questions);
        this.pageSize = pageSize;
    }

    public List<T> page(int pageIndex) {
        int start = pageIndex * pageSize;
        if (pageIndex < 0 || start >= questions.size()) {
            throw new IllegalArgumentException("Invalid application page: " + pageIndex);
        }
        return questions.subList(start, Math.min(questions.size(), start + pageSize));
    }

    public int pageCount() {
        return questions.isEmpty() ? 0 : (questions.size() + pageSize - 1) / pageSize;
    }
}
