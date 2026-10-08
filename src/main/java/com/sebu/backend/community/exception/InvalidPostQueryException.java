package com.sebu.backend.community.exception;

public class InvalidPostQueryException extends RuntimeException {
    public InvalidPostQueryException() {
        super("INVALID_QUERY_PARAMETER");
    }
}
