package com.sebu.backend.laboratoryreview.exception;

public class LaboratoryReviewAlreadyExistsException extends RuntimeException {

    public LaboratoryReviewAlreadyExistsException() {
        super("LABORATORY_REVIEW_ALREADY_EXISTS");
    }
}
