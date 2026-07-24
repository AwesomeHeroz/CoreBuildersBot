package com.corebuilders.bot.util;

public final class ErrorMessages {
    private ErrorMessages() {}

    public static String safe(Throwable error) {
        return safe(error, 1800);
    }

    public static String safe(Throwable error, int maxLength) {
        if (error == null) return "Unknown error";
        Throwable current = error;
        while (current.getCause() != null && (current.getMessage() == null || current.getMessage().isBlank())) {
            current = current.getCause();
        }
        String message = current.getMessage();
        if (message == null || message.isBlank()) {
            message = current.getClass().getSimpleName();
        }
        return truncate(message, Math.max(1, maxLength));
    }

    private static String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, Math.max(0, maxLength - 1)) + "…";
    }
}
