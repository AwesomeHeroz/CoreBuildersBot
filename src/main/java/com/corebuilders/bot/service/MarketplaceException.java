package com.corebuilders.bot.service;

/** Stable service errors that HTTP and Discord adapters can map without inspecting messages. */
public final class MarketplaceException extends RuntimeException {
    public enum Code {
        VALIDATION,
        NOT_FOUND,
        CONFLICT,
        FORBIDDEN,
        INSUFFICIENT_FUNDS,
        OUT_OF_STOCK,
        PRICE_CHANGED
    }

    private final Code code;

    public MarketplaceException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public Code code() {
        return code;
    }

    public static MarketplaceException validation(String message) {
        return new MarketplaceException(Code.VALIDATION, message);
    }

    public static MarketplaceException notFound(String message) {
        return new MarketplaceException(Code.NOT_FOUND, message);
    }

    public static MarketplaceException conflict(String message) {
        return new MarketplaceException(Code.CONFLICT, message);
    }

    public static MarketplaceException forbidden(String message) {
        return new MarketplaceException(Code.FORBIDDEN, message);
    }
}
