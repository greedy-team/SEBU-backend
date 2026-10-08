package com.sebu.backend.auth.exception;

public class InvalidLoginRequestException extends RuntimeException {
    public InvalidLoginRequestException() {
        super("INVALID_LOGIN_REQUEST");
    }
}
