package com.corebuilders.bot.db;

public final class DuplicateKeyException extends DatabaseException {
    public DuplicateKeyException(String message, String sqlState, Throwable cause) {
        super(message, sqlState, cause);
    }
}
