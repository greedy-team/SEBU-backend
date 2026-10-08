package com.sebu.backend.auth.exception;

public class RecoveryTokenInvalidException extends RuntimeException {
    public RecoveryTokenInvalidException() {
        super("RECOVERY_TOKEN_INVALID");
    }
}
