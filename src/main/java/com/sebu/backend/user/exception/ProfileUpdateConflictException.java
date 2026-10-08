package com.sebu.backend.user.exception;

public class ProfileUpdateConflictException extends RuntimeException {
    public ProfileUpdateConflictException() {
        super("PROFILE_UPDATE_CONFLICT");
    }
}
