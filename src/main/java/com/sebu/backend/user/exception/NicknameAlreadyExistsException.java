package com.sebu.backend.user.exception;

public class NicknameAlreadyExistsException extends RuntimeException {
    public NicknameAlreadyExistsException() {
        super("NICKNAME_ALREADY_EXISTS");
    }
}
