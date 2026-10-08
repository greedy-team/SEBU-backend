package com.sebu.backend.auth.exception;

public class AuthSessionConflictException extends RuntimeException {
    public AuthSessionConflictException() {
        super("AUTH_SESSION_CONFLICT");
    }
}
