package com.sebu.backend.community.exception;

public class PostNotFoundException extends RuntimeException {
    public PostNotFoundException() {
        super("POST_NOT_FOUND");
    }
}
