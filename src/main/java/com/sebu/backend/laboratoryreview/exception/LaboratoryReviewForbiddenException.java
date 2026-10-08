package com.sebu.backend.laboratoryreview.exception;

public class LaboratoryReviewForbiddenException extends RuntimeException {

    public LaboratoryReviewForbiddenException() {
        super("LABORATORY_REVIEW_FORBIDDEN");
    }
}
