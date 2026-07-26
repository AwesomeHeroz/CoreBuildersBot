package com.corebuilders.bot.web.auth;

public final class OAuthException extends RuntimeException {
    public enum Code { INVALID_RESPONSE, ACCESS_DENIED, NOT_IN_GUILD, PROVIDER_UNAVAILABLE }

    private final Code code;

    public OAuthException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public OAuthException(Code code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public Code code() {
        return code;
    }
}
