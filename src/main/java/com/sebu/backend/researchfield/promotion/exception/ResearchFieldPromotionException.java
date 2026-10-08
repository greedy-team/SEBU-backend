package com.sebu.backend.researchfield.promotion.exception;

public class ResearchFieldPromotionException extends RuntimeException {
    public ResearchFieldPromotionException(String message) {
        super(message);
    }

    public ResearchFieldPromotionException(String message, Throwable cause) {
        super(message, cause);
    }
}
