package com.sebu.backend.community.exception;

public class CommentForbiddenException extends RuntimeException {
    public CommentForbiddenException() {
        super("COMMENT_FORBIDDEN");
    }
}
