package com.sebu.backend.promotion.exception;

public class CandidatePromotionException extends RuntimeException {
    public CandidatePromotionException(String message) {
        super(message);
    }

    public CandidatePromotionException(String message, Throwable cause) {
        super(message, cause);
    }
}
