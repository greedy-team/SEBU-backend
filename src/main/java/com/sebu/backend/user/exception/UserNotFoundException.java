package com.sebu.backend.user.exception;

public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException() {
        super("USER_NOT_FOUND");
    }
}
