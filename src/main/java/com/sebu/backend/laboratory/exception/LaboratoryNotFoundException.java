package com.sebu.backend.laboratory.exception;

public class LaboratoryNotFoundException extends RuntimeException {

    public LaboratoryNotFoundException() {
        super("LABORATORY_NOT_FOUND");
    }
}
