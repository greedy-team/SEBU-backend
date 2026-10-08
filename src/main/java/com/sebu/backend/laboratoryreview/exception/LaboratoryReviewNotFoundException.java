package com.sebu.backend.laboratoryreview.exception;

public class LaboratoryReviewNotFoundException extends RuntimeException {

    public LaboratoryReviewNotFoundException() {
        super("LABORATORY_REVIEW_NOT_FOUND");
    }
}
