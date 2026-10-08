package com.sebu.backend.auth.exception;

public class AuthSessionExpiredException extends RuntimeException {
    public AuthSessionExpiredException() {
        super("AUTH_SESSION_EXPIRED");
    }
}
