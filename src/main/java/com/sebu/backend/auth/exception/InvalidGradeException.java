package com.sebu.backend.auth.exception;

public class InvalidGradeException extends RuntimeException {
    public InvalidGradeException() {
        super("INVALID_GRADE");
    }
}
