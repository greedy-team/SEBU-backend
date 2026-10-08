package com.sebu.backend.community.exception;

public class PostForbiddenException extends RuntimeException {
    public PostForbiddenException() {
        super("POST_FORBIDDEN");
    }
}
