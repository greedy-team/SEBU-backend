package com.sebu.backend.crawling.exception;

public class ProfessorCrawlException extends RuntimeException {
    public ProfessorCrawlException(String message) {
        super(message);
    }

    public ProfessorCrawlException(String message, Throwable cause) {
        super(message, cause);
    }
}
