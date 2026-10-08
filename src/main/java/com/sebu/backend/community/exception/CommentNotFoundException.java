package com.sebu.backend.community.exception;

public class CommentNotFoundException extends RuntimeException {
    public CommentNotFoundException() {
        super("COMMENT_NOT_FOUND");
    }
}
