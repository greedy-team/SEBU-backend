package com.sebu.backend.auth.exception;

public class RefreshTokenInvalidException extends RuntimeException {
    public enum Reason { INVALID, MALFORMED, NOT_FOUND, USER_UNAVAILABLE, REVOKED, EXPIRED, SESSION_EXPIRED }
    private final Reason reason;

    public RefreshTokenInvalidException() {
        this(Reason.INVALID);
    }

    public RefreshTokenInvalidException(Reason reason) {
        super("REFRESH_TOKEN_INVALID");
        this.reason = reason;
    }

    public Reason getReason() { return reason; }
}
