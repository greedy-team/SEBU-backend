package com.sebu.backend.researchfield.extraction.exception;

public class ResearchFieldExtractionException extends RuntimeException {
    public ResearchFieldExtractionException(String message) {
        super(message);
    }

    public ResearchFieldExtractionException(String message, Throwable cause) {
        super(message, cause);
    }
}
