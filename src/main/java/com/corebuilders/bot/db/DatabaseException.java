package com.corebuilders.bot.db;

public class DatabaseException extends RuntimeException {
    private final String sqlState;

    public DatabaseException(String message, String sqlState, Throwable cause) {
        super(message, cause);
        this.sqlState = sqlState;
    }

    public String sqlState() {
        return sqlState;
    }
}
