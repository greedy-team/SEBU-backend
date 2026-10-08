package com.sebu.backend.researchfield.candidate.domain;

import java.util.Objects;

public record ResearchFieldCandidateDraft(
    String sourceFieldKey,
    String rawFieldText,
    String candidateName,
    ResearchFieldExtractionMethod extractionMethod,
    int sourceOrder
) {
    private static final int HASH_LENGTH = 64;
    private static final int MAX_RAW_FIELD_TEXT_LENGTH = 2000;
    private static final int MAX_CANDIDATE_NAME_LENGTH = 100;

    public ResearchFieldCandidateDraft {
        sourceFieldKey = requireHash(sourceFieldKey, "SOURCE_FIELD_KEY_INVALID");
        rawFieldText = requireText(
            rawFieldText,
            MAX_RAW_FIELD_TEXT_LENGTH,
            "RAW_FIELD_TEXT_INVALID"
        );
        candidateName = normalizeNullable(
            candidateName,
            MAX_CANDIDATE_NAME_LENGTH,
            "CANDIDATE_NAME_INVALID"
        );
        extractionMethod = Objects.requireNonNull(
            extractionMethod,
            "EXTRACTION_METHOD_REQUIRED"
        );
        if (sourceOrder < 0) {
            throw new IllegalArgumentException("SOURCE_ORDER_INVALID");
        }
    }

    private static String requireHash(String value, String errorCode) {
        String normalized = requireText(value, HASH_LENGTH, errorCode);
        if (normalized.length() != HASH_LENGTH
            || !normalized.matches("[0-9a-f]{" + HASH_LENGTH + "}")) {
            throw new IllegalArgumentException(errorCode);
        }
        return normalized;
    }

    private static String requireText(String value, int maxLength, String errorCode) {
        String normalized = normalizeNullable(value, maxLength, errorCode);
        if (normalized == null) {
            throw new IllegalArgumentException(errorCode);
        }
        return normalized;
    }

    private static String normalizeNullable(String value, int maxLength, String errorCode) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(errorCode);
        }
        return normalized;
    }
}
